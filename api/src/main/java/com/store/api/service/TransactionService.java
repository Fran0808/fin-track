package com.store.api.service;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.TransactionClassificationRequest;
import com.store.api.model.dto.TransactionResponse;
import com.store.api.model.dto.TransactionSyncRequest;
import com.store.api.model.dto.FinancialInstrumentResponse;
import com.store.api.model.entity.RawNotificationLog;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RawNotificationRepository rawNotificationRepository;

    @Transactional
    public TransactionResponse processAndSave(TransactionSyncRequest request) {
        User currentUser = UserContext.requireCurrentUser();
        String channel = (request.getChannel() != null && !request.getChannel().isBlank())
                ? request.getChannel().trim()
                : "UNKNOWN";

        if (request.getRawNotificationText() != null && !request.getRawNotificationText().isBlank()) {
            RawNotificationLog rawLog = RawNotificationLog.builder()
                    .rawText(request.getRawNotificationText())
                    .sourcePackage(channel)
                    .isProcessed(true)
                    .build();
            rawNotificationRepository.save(rawLog);
        }

        boolean exists = transactionRepository.existsByTransactionHashAndUser(request.getTransactionHash(), currentUser);

        if (exists) {
            log.warn("Transaction with hash [{}] already exists for user [{}]. Skipping duplicate.",
                    request.getTransactionHash(), currentUser.getEmail());
            Transaction existing = transactionRepository.findByTransactionHashAndUser(request.getTransactionHash(), currentUser).orElseThrow();
            return mapToResponse(existing);
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

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved successfully: ID={}, Amount={}, FlowType={}, Channel={}, User={}",
                saved.getId(), saved.getAmount(), saved.getFlowType(), saved.getChannel(),
                currentUser.getEmail());
        return mapToResponse(saved);
    }

    @Transactional
    public List<TransactionResponse> processBatch(List<TransactionSyncRequest> requests) {
        UserContext.requireCurrentUser();
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
            tx.setCategory(trimmed.isEmpty() ? null : trimmed.toUpperCase());
        }
        if (request.getTags() != null) {
            tx.setTagsList(request.getTags());
        }
        if (request.getNotes() != null) {
            String trimmed = request.getNotes().trim();
            tx.setNotes(trimmed.isEmpty() ? null : trimmed);
        }

        Transaction saved = transactionRepository.save(tx);
        log.info("Transaction classification updated: ID={}, Category={}, Tags={}, User={}",
                saved.getId(), saved.getCategory(), saved.getTags(), currentUser.getEmail());
        return mapToResponse(saved);
    }

    public Specification<Transaction> buildSpecification(
            User currentUser,
            LocalDateTime startDate,
            LocalDateTime endDate,
            FlowType flowType,
            String search,
            Long financialInstrumentId,
            String category,
            String tag,
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
            if (tag != null && !tag.isBlank()) {
                String cleanTag = tag.trim().replaceAll("^#+", "").toLowerCase();
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), "%" + cleanTag + "%"));
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
                Predicate tagsMatch = cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), pattern);
                Predicate notesMatch = cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern);
                predicates.add(cb.or(contactMatch, categoryMatch, tagsMatch, notesMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Long financialInstrumentId, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, financialInstrumentId, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            LocalDateTime startDate,
            LocalDateTime endDate,
            FlowType flowType,
            String search,
            Long financialInstrumentId,
            String category,
            String tag,
            String channel,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable
    ) {
        User currentUser = UserContext.requireCurrentUser();
        Specification<Transaction> spec = buildSpecification(currentUser, startDate, endDate, flowType, search, financialInstrumentId, category, tag, channel, minAmount, maxAmount);
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
            String tag,
            String channel,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        User currentUser = UserContext.requireCurrentUser();
        Specification<Transaction> spec = buildSpecification(currentUser, startDate, endDate, flowType, search, financialInstrumentId, category, tag, channel, minAmount, maxAmount);
        Sort sort = Sort.by(Sort.Direction.DESC, "transactionDate");
        List<Transaction> list = transactionRepository.findAll(spec, sort);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM so Excel opens with proper encoding
        sb.append("\uFEFF");
        sb.append("ID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Etiquetas,Medio,Tarjeta,Notas,Hash\n");

        for (Transaction t : list) {
            sb.append(escapeCsv(String.valueOf(t.getId()))).append(",");
            sb.append(escapeCsv(t.getTransactionDate() != null ? t.getTransactionDate().toString() : "")).append(",");
            sb.append(escapeCsv(t.getContactName())).append(",");
            sb.append(escapeCsv(t.getAmount() != null ? t.getAmount().toPlainString() : "0.00")).append(",");
            sb.append(escapeCsv(t.getFlowType() != null ? t.getFlowType().name() : "")).append(",");
            sb.append(escapeCsv(t.getCategory() != null ? t.getCategory() : "")).append(",");
            sb.append(escapeCsv(t.getTags() != null ? t.getTags() : "")).append(",");
            sb.append(escapeCsv(t.getChannel() != null ? t.getChannel() : "")).append(",");
            sb.append(escapeCsv(t.getCardLast4() != null ? t.getCardLast4() : "")).append(",");
            sb.append(escapeCsv(t.getNotes() != null ? t.getNotes() : "")).append(",");
            sb.append(escapeCsv(t.getTransactionHash())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
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
                .tags(t.getTagsList())
                .notes(t.getNotes())
                .transactionDate(t.getTransactionDate())
                .transactionHash(t.getTransactionHash())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
