package com.store.api.model.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ManualReconciliationStatus {
    private TransactionResponse manual;
    private TransactionResponse captured;
    private LocalDateTime reconciledAt;
    private int revision;
}
