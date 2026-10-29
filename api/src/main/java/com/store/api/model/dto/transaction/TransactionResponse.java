package com.store.api.model.dto.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private com.store.api.model.enums.CaptureOrigin captureOrigin;
    private Long reconciledToId;
    private BigDecimal amount;
    private FlowType flowType;
    private OperationType operationType;
    private String contactName;
    private String channel;
    private String cardLast4;
    private String category;
    private LocalDateTime transactionDate;
    private String transactionHash;
    private LocalDateTime createdAt;
}
