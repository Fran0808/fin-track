package com.store.api.model.dto.email;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEmailTransaction {
    private BigDecimal amount;
    private String currency;
    private FlowType flowType;
    @Builder.Default
    private OperationType operationType = OperationType.UNKNOWN;
    private String merchantName;
    private String channel;
    private String cardLast4;
    private String operationNumber;
    // Explicit receipt evidence; operationNumber retains its legacy identity/hash semantics.
    private String receiptReference;
    private LocalDateTime transactionDate;
    private String transactionHash;
    private String rawBody;
}
