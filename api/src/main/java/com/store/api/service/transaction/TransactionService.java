package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionClassificationRequest;
import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.dto.instrument.FinancialInstrumentResponse;
import com.store.api.model.entity.RawNotificationLog;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.PendingReason;
import com.store.api.service.category.ClassificationRuleService;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
import com.store.api.repository.CategoryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RawNotificationRepository rawNotificationRepository;
    private final CategoryRepository categoryRepository;
    private final ClassificationRuleService classificationRules;

    @Transactional
    public TransactionResponse processAndSave(@NotNull @Valid TransactionSyncRequest request) {
        User currentUser = UserContext.requireCurrentUser();
        String channel = (request.getChannel() != null && !request.getChannel().isBlank())
                ? request.getChannel().trim()
                : "UNKNOWN";

        transactionRepository.lockTransactionHash(currentUser.getId(), request.getTransactionHash());
        if (transactionRepository.existsByTransactionHashAndUser(request.getTransactionHash(), currentUser)) {
            return mapToResponse(transactionRepository.findByTransactionHashAndUser(request.getTransactionHash(), currentUser).orElseThrow());
        }
        Transaction transaction = Transaction.builder()
                .user(currentUser)
                .amount(request.getAmount())
                .flowType(request.getFlowType())
                .contactName(request.getContactName().trim())
                .channel(channel)
                .cardLast4(request.getCardLast4())
                .transactionDate(request.getTransactionDate())
                .transactionHash(request.getTransactionHash())
                .build();

        classificationRules.classifyNewTransaction(transaction);
        Transaction saved = transactionRepository.save(transaction);
        if (request.getRawNotificationText() != null && !request.getRawNotificationText().isBlank()) {
            rawNotificationRepository.save(RawNotificationLog.builder()
                    .user(currentUser)
                    .transaction(saved)
                    .rawText(request.getRawNotificationText())
                    .sourcePackage(channel)
                    .isProcessed(true)
                    .build());
        }
        log.info("Transaction stored successfully");
        return mapToResponse(saved);
    }

    @Transactional
    public List<TransactionResponse> processBatch(@NotEmpty @Size(max = 100) List<@NotNull @Valid TransactionSyncRequest> requests) {
        User user = UserContext.requireCurrentUser();
        requests.stream().map(TransactionSyncRequest::getTransactionHash).distinct().sorted()
                .forEach(hash -> transactionRepository.lockTransactionHash(user.getId(), hash));
        List<TransactionResponse> responses = new ArrayList<>();
        for (TransactionSyncRequest req : requests) {
            responses.add(processAndSave(req));
        }
        return responses;
    }

    @Transactional
    public TransactionResponse updateClassification(Long id, TransactionClassificationRequest request) {
        User currentUser = UserContext.requireCurrentUser();
        Transaction tx = transactionRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (request.getCategory() != null) {
            String trimmed = request.getCategory().trim();
            if (trimmed.isEmpty()) {
                tx.setCategory(null);
            } else if (!trimmed.equalsIgnoreCase(tx.getCategory())) {
                if (trimmed.length() > 100
                        || !categoryRepository.existsByUserAndActiveTrueAndNameIgnoreCase(currentUser, trimmed)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Selecciona una categoría activa de tu catálogo.");
                }
                tx.setCategory(trimmed);
            }
        }

        Transaction saved = transactionRepository.save(tx);
        log.info("Transaction classification updated");
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getPendingTransactions(PendingReason reason, Pageable pageable) {
        User user = UserContext.requireCurrentUser();
        return transactionRepository.findAll(pendingSpecification(user, reason), pageable).map(TransactionService::mapToResponse);
    }

    private Specification<Transaction> pendingSpecification(User user, PendingReason reason) {
        return (root, query, cb) -> {
            Predicate missingCategory = cb.and(
                    cb.notEqual(root.get("flowType"), FlowType.INTERNAL_TRANSFER),
                    cb.equal(cb.trim(cb.coalesce(root.get("category"), "")), ""));
            Predicate missingInstrument = cb.isNull(root.get("financialInstrument"));
            Predicate needsReview = switch (reason) {
                case CATEGORY -> missingCategory;
                case INSTRUMENT -> missingInstrument;
                case ALL -> cb.or(missingCategory, missingInstrument);
            };
            return cb.and(cb.equal(root.get("user"), user), needsReview);
        };
    }

    public Specification<Transaction> buildSpecification(
            User currentUser,
            LocalDateTime startDate,
            LocalDateTime endDate,
            FlowType flowType,
            String search,
            Long financialInstrumentId,
            String category,
            String channel,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), currentUser));

            if (financialInstrumentId != null) {
                predicates.add(cb.equal(root.get("financialInstrument").get("id"), financialInstrumentId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            if (flowType != null) {
                predicates.add(cb.equal(root.get("flowType"), flowType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), category.trim().toUpperCase()));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("channel")), channel.trim().toUpperCase()));
            }
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                Predicate contactMatch = cb.like(cb.lower(root.get("contactName")), pattern);
                Predicate categoryMatch = cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern);
                predicates.add(cb.or(contactMatch, categoryMatch));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Long financialInstrumentId, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, financialInstrumentId, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            LocalDateTime startDate,
            LocalDateTime endDate,
            FlowType flowType,
            String search,
            Long financialInstrumentId,
            String category,
            String channel,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable
    ) {
        return getTransactions(startDate, endDate, flowType, search, financialInstrumentId,
                category, channel, minAmount, maxAmount, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel,
            BigDecimal minAmount, BigDecimal maxAmount, PendingReason pendingReason, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, financialInstrumentId, category,
                channel, minAmount, maxAmount, pendingReason, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel,
            BigDecimal minAmount, BigDecimal maxAmount, PendingReason pendingReason, String merchantName, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, financialInstrumentId, category, channel,
                minAmount, maxAmount, pendingReason, merchantName, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel, BigDecimal minAmount, BigDecimal maxAmount,
            PendingReason pendingReason, String merchantName, String categoryGroup, Pageable pageable) {
        User currentUser = UserContext.requireCurrentUser();
        Specification<Transaction> spec = buildSpecification(currentUser, startDate, endDate, flowType, search, financialInstrumentId, category, channel, minAmount, maxAmount);
        if (pendingReason != null) spec = spec.and(pendingSpecification(currentUser, pendingReason));
        if (merchantName != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("contactName"), merchantName));
        if (categoryGroup != null) spec = spec.and(categoryGroupSpecification(categoryGroup));
        return transactionRepository.findAll(spec, pageable).map(TransactionService::mapToResponse);
    }

    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(
            LocalDateTime startDate,
            LocalDateTime endDate,
            FlowType flowType,
            String search,
            Long financialInstrumentId,
            String category,
            String channel,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        return exportTransactionsCsv(startDate, endDate, flowType, search, financialInstrumentId,
                category, channel, minAmount, maxAmount, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel,
            BigDecimal minAmount, BigDecimal maxAmount, PendingReason pendingReason) {
        return exportTransactionsCsv(startDate, endDate, flowType, search, financialInstrumentId, category,
                channel, minAmount, maxAmount, pendingReason, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel,
            BigDecimal minAmount, BigDecimal maxAmount, PendingReason pendingReason, String merchantName) {
        return exportTransactionsCsv(startDate, endDate, flowType, search, financialInstrumentId, category,
                channel, minAmount, maxAmount, pendingReason, merchantName, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(
            LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search,
            Long financialInstrumentId, String category, String channel, BigDecimal minAmount, BigDecimal maxAmount,
            PendingReason pendingReason, String merchantName, String categoryGroup) {
        User currentUser = UserContext.requireCurrentUser();
        Specification<Transaction> spec = buildSpecification(currentUser, startDate, endDate, flowType, search, financialInstrumentId, category, channel, minAmount, maxAmount);
        if (pendingReason != null) spec = spec.and(pendingSpecification(currentUser, pendingReason));
        if (merchantName != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("contactName"), merchantName));
        if (categoryGroup != null) spec = spec.and(categoryGroupSpecification(categoryGroup));
        Sort sort = Sort.by(Sort.Direction.DESC, "transactionDate", "id");
        Page<Transaction> exportPage = transactionRepository.findAll(spec, org.springframework.data.domain.PageRequest.of(0, 10001, sort));
        if (exportPage.getTotalElements() > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Reduce los filtros para exportar hasta 10000 movimientos.");
        List<Transaction> list = exportPage.getContent();

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM so Excel opens with proper encoding
        sb.append("\uFEFF");
        sb.append("ID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Medio,Tarjeta,Hash\n");

        for (Transaction t : list) {
            sb.append(escapeCsv(String.valueOf(t.getId()))).append(",");
            sb.append(escapeCsv(t.getTransactionDate() != null ? t.getTransactionDate().toString() : "")).append(",");
            sb.append(escapeCsv(t.getContactName())).append(",");
            sb.append(escapeCsv(t.getAmount() != null ? t.getAmount().toPlainString() : "0.00")).append(",");
            sb.append(escapeCsv(t.getFlowType() != null ? t.getFlowType().name() : "")).append(",");
            sb.append(escapeCsv(t.getCategory() != null ? t.getCategory() : "")).append(",");
            sb.append(escapeCsv(t.getChannel() != null ? t.getChannel() : "")).append(",");
            sb.append(escapeCsv(t.getCardLast4() != null ? t.getCardLast4() : "")).append(",");
            sb.append(escapeCsv(t.getTransactionHash())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<Transaction> categoryGroupSpecification(String categoryGroup) {
        return (root, query, cb) -> cb.equal(cb.trim(cb.coalesce(root.get("category"), "")), categoryGroup);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String trimmed = value.stripLeading();
        if ((!trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0)) >= 0)
                || value.startsWith("\t") || value.startsWith("\r")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static TransactionResponse mapToResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .financialInstrument(FinancialInstrumentResponse.from(t.getFinancialInstrument()))
                .amount(t.getAmount())
                .flowType(t.getFlowType())
                .contactName(t.getContactName())
                .channel(t.getChannel())
                .cardLast4(t.getCardLast4())
                .category(t.getCategory())
                .transactionDate(t.getTransactionDate())
                .transactionHash(t.getTransactionHash())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
