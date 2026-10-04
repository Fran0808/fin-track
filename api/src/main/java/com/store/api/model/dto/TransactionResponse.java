package com.store.api.model.dto;

import com.store.api.model.enums.FlowType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private FinancialInstrumentResponse financialInstrument;
    private BigDecimal amount;
    private FlowType flowType;
    private String contactName;
    private String channel;
    private String cardLast4;
    private LocalDateTime transactionDate;
    private String transactionHash;
    private LocalDateTime createdAt;
}
