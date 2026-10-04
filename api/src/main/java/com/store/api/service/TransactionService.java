package com.store.api.service;

import com.store.api.config.security.UserContext;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Pageable pageable) {
        return getTransactions(startDate, endDate, flowType, search, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(LocalDateTime startDate, LocalDateTime endDate, FlowType flowType, String search, Long financialInstrumentId, Pageable pageable) {
        User currentUser = UserContext.requireCurrentUser();
        Specification<Transaction> spec = (root, query, cb) -> {
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
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("contactName")), "%" + search.toLowerCase().trim() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return transactionRepository.findAll(spec, pageable).map(TransactionService::mapToResponse);
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
                .transactionDate(t.getTransactionDate())
                .transactionHash(t.getTransactionHash())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
