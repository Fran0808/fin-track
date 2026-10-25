package com.store.api.model.dto.transaction;

import com.store.api.model.enums.CaptureOrigin;
import lombok.*;

import java.time.LocalDateTime;

@Data @AllArgsConstructor
public class TransactionOriginResponse {
    private CaptureOrigin origin;
    private LocalDateTime registeredAt;
    private String operationReference;
}
