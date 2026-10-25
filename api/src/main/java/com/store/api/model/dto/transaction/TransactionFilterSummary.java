package com.store.api.model.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TransactionFilterSummary {
    private final BigDecimal totalExpense;
    private final BigDecimal totalIncome;
    private final BigDecimal internalTransfersAmount;
}
