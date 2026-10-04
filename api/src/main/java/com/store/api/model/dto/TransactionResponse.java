package com.store.api.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.store.api.model.enums.FlowType;

import lombok.Builder;
import lombok.Data;

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
    private String category;
    private java.util.List<String> tags;
    private String notes;
    private LocalDateTime transactionDate;
    private String transactionHash;
    private LocalDateTime createdAt;
}
