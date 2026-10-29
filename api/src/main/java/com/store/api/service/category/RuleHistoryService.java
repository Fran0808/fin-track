package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.*;
import com.store.api.model.entity.ClassificationRule;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.ClassificationRuleRepository;
import com.store.api.repository.TransactionRepository;
import com.store.api.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class RuleHistoryService {
    private static final int LIMIT = 200;
    private static final long PREVIEW_SECONDS = 600;
    private final TransactionRepository transactions;
    private final ClassificationRuleRepository rules;
    private final byte[] signingKey;

    public RuleHistoryService(TransactionRepository transactions, ClassificationRuleRepository rules,
                              @Value("${jwt.secret}") String secret) {
        this.transactions = transactions;
        this.rules = rules;
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RuleHistoryPreview preview(Long ruleId) {
        User user = UserContext.requireCurrentUser();
        ClassificationRule selected = requireActive(ruleId, user);
        List<ClassificationRule> ordered = rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(user);
        Specification<Transaction> scope = (root, query, cb) -> cb.and(
                cb.equal(root.get("user"), user),
                cb.isNull(root.get("reconciledToId")),
                root.get("flowType").in(FlowType.EXPENSE, FlowType.INCOME),
                cb.equal(cb.trim(cb.coalesce(root.get("category"), "")), ""));
        List<Transaction> matches = new ArrayList<>();
        int pageNumber = 0;
        boolean exhausted;
        do {
            var page = transactions.findAll(scope, PageRequest.of(pageNumber++, LIMIT, Sort.by("id")));
            for (Transaction transaction : page) {
                if (wins(selected, ordered, transaction, user)) matches.add(transaction);
                if (matches.size() > LIMIT) break;
            }
            exhausted = page.isLast();
        } while (!exhausted && matches.size() <= LIMIT);
        boolean hasMore = matches.size() > LIMIT;
        List<Transaction> batch = matches.stream().limit(LIMIT).toList();
        long expiresAt = Instant.now().getEpochSecond() + PREVIEW_SECONDS;
        return RuleHistoryPreview.builder().ruleId(ruleId).matchText(selected.getMatchText())
                .categoryName(selected.getCategory().getName()).limit(LIMIT).hasMore(hasMore).expiresAt(expiresAt)
                .transactions(batch.stream().map(TransactionService::mapToResponse).toList())
                .previewToken(sign(user, selected, ordered, batch, expiresAt)).build();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RuleHistoryApplyResponse apply(Long ruleId, RuleHistoryApplyRequest request) {
        User user = UserContext.requireCurrentUser();
        ClassificationRule selected = requireActive(ruleId, user);
        if (request.getTransactionIds() == null || request.getTransactionIds().isEmpty()
                || request.getTransactionIds().size() > LIMIT
                || new HashSet<>(request.getTransactionIds()).size() != request.getTransactionIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa la selección de movimientos.");
        }
        long now = Instant.now().getEpochSecond();
        if (request.getExpiresAt() == null || request.getExpiresAt() <= now
                || request.getExpiresAt() > now + PREVIEW_SECONDS) throw outdated();
        List<ClassificationRule> ordered = rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(user);
        List<Transaction> batch = transactions.lockHistorySelection(user, request.getTransactionIds());
        if (batch.size() != request.getTransactionIds().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selección no encontrada. Vuelve a revisar la vista previa.");
        }
        String expected = sign(user, selected, ordered, batch, request.getExpiresAt());
        if (request.getPreviewToken() == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                request.getPreviewToken().getBytes(StandardCharsets.UTF_8))) throw outdated();
        int applied = 0;
        for (Transaction transaction : batch) {
            if ((transaction.getCategory() == null || transaction.getCategory().isBlank())
                    && transaction.getFlowType() != FlowType.INTERNAL_TRANSFER
                    && wins(selected, ordered, transaction, user)) {
                transaction.setCategory(selected.getCategory().getName());
                applied++;
            }
        }
        return new RuleHistoryApplyResponse(applied, batch.size() - applied);
    }

    private ClassificationRule requireActive(Long id, User user) {
        ClassificationRule rule = rules.findByIdAndUser(id, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla no encontrada."));
        if (!rule.isEnabled() || !rule.getCategory().isActive()
                || !user.getId().equals(rule.getCategory().getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La regla y su categoría deben estar activas.");
        }
        return rule;
    }

    private boolean wins(ClassificationRule selected, List<ClassificationRule> ordered, Transaction transaction, User user) {
        if (transaction.getReconciledToId() != null || !user.getId().equals(transaction.getUser().getId())
                || (transaction.getFlowType() != FlowType.INCOME && transaction.getFlowType() != FlowType.EXPENSE)) return false;
        return ordered.stream().filter(rule -> ClassificationRuleService.matches(rule, transaction, user))
                .findFirst().map(rule -> rule.getId().equals(selected.getId())).orElse(false);
    }

    private ResponseStatusException outdated() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "La vista previa venció o cambió la regla o un movimiento. Vuelve a revisar antes de confirmar.");
    }

    private String sign(User user, ClassificationRule selected, List<ClassificationRule> ordered,
                        List<Transaction> batch, long expiresAt) {
        try {
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeUTF("fintrack-rule-history-v1");
            data.writeLong(user.getId()); data.writeLong(selected.getId()); data.writeLong(expiresAt);
            data.writeInt(ordered.size());
            for (ClassificationRule rule : ordered) {
                data.writeLong(rule.getId()); data.writeUTF(rule.getMatchText()); data.writeInt(rule.getPriority());
                data.writeLong(rule.getCategory().getId()); data.writeUTF(rule.getCategory().getName());
                data.writeBoolean(rule.getCategory().isActive()); data.writeLong(rule.getCategory().getUser().getId());
            }
            data.writeInt(batch.size());
            for (Transaction transaction : batch.stream().sorted(Comparator.comparing(Transaction::getId)).toList()) {
                data.writeLong(transaction.getId()); data.writeUTF(transaction.getContactName());
                data.writeUTF(transaction.getFlowType().name());
                data.writeUTF(transaction.getAmount().stripTrailingZeros().toPlainString());
                data.writeUTF(transaction.getTransactionDate().toString()); data.writeUTF(transaction.getTransactionHash());
                // Category changes are intentionally excluded: reviewed rows already classified are skipped on retry.
            }
            data.flush();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign rule history preview", ex);
        }
    }
}
