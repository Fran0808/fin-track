package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.*;
import com.store.api.model.entity.*;
import com.store.api.model.enums.OperationType;
import com.store.api.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @Validated @RequiredArgsConstructor
public class TransactionCorrectionService {
    private final TransactionRepository transactions;
    private final TransactionCorrectionRepository corrections;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionCorrectionHistory history(Long id, int page) {
        User user = UserContext.requireCurrentUser();
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página no válida.");
        Transaction transaction = transactions.findByIdAndUser(id, user).orElseThrow(TransactionCorrectionService::missing);
        var entries = corrections.findByTransactionIdAndUserOrderByIdDesc(id, user, PageRequest.of(page, 20));
        return new TransactionCorrectionHistory(TransactionService.mapToResponse(transaction), revision(id, user),
                !linked(transaction, user), entries.map(TransactionCorrectionHistory.Entry::from).getContent(), entries.getTotalPages());
    }

    @Transactional
    public TransactionResponse correct(Long id, @NotNull @Valid TransactionCorrectionRequest request) {
        User user = UserContext.requireCurrentUser();
        Transaction transaction = transactions.findLockedByIdAndUser(id, user).orElseThrow(TransactionCorrectionService::missing);
        String requestId = UUID.fromString(request.getRequestId()).toString();
        var previous = corrections.findByTransactionIdAndUserAndRequestId(id, user, requestId);
        if (previous.isPresent()) {
            var saved = previous.get();
            if (saved.getExpectedRevision() != request.getExpectedRevision() || !saved.getReason().equals(request.getReason().trim())
                    || saved.getAfterAmount().compareTo(request.getAmount()) != 0 || !saved.getAfterDate().equals(request.getTransactionDate())
                    || saved.getAfterFlow() != request.getFlowType()) throw conflict("Este envío ya se utilizó con otros datos. Vuelve a consultar el movimiento.");
            return TransactionService.mapToResponse(transaction);
        }
        if (linked(transaction, user)) throw conflict("Deshaz la conciliación antes de corregir este movimiento.");
        if (revision(id, user) != request.getExpectedRevision()) throw conflict("El movimiento cambió. Vuelve a abrir la corrección y revisa sus datos.");
        if (transaction.getAmount().compareTo(request.getAmount()) == 0 && transaction.getTransactionDate().equals(request.getTransactionDate())
                && transaction.getFlowType() == request.getFlowType()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cambia al menos un dato antes de confirmar.");
        OperationType operation = transaction.getFlowType() == request.getFlowType() ? transaction.getOperationType() : OperationType.UNKNOWN;
        corrections.save(TransactionCorrection.builder().transaction(transaction).user(user).requestId(requestId)
                .expectedRevision(request.getExpectedRevision()).reason(request.getReason().trim())
                .beforeAmount(transaction.getAmount()).afterAmount(request.getAmount())
                .beforeDate(transaction.getTransactionDate()).afterDate(request.getTransactionDate())
                .beforeFlow(transaction.getFlowType()).afterFlow(request.getFlowType())
                .beforeOperation(transaction.getOperationType()).afterOperation(operation).createdAt(LocalDateTime.now()).build());
        transaction.setAmount(request.getAmount());
        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setFlowType(request.getFlowType());
        transaction.setOperationType(operation);
        transaction.setReconciliationRevision(transaction.getReconciliationRevision() + 1);
        // Identity, source evidence and classification remain independent of the correction.
        return TransactionService.mapToResponse(transactions.save(transaction));
    }

    private boolean linked(Transaction transaction, User user) {
        return transaction.getReconciledToId() != null || transactions.findByReconciledToIdAndUser(transaction.getId(), user).isPresent();
    }
    private long revision(Long id, User user) {
        return corrections.findFirstByTransactionIdAndUserOrderByIdDesc(id, user).map(TransactionCorrection::getId).orElse(0L);
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
