package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionOriginResponse;
import com.store.api.model.enums.CaptureOrigin;
import com.store.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransactionOriginService {
    private final TransactionRepository transactions;

    @Transactional(readOnly = true)
    public TransactionOriginResponse getOrigin(Long id) {
        var transaction = transactions.findByIdAndUser(id, UserContext.requireCurrentUser()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado."));
        var origin = transaction.getCaptureOrigin() == null ? CaptureOrigin.UNKNOWN : transaction.getCaptureOrigin();
        return new TransactionOriginResponse(origin, transaction.getCreatedAt(),
                origin == CaptureOrigin.EMAIL ? transaction.getOperationReference() : null);
    }
}
