package com.store.api.model.dto.transaction;

import com.store.api.model.enums.FlowType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionSyncRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Flow type (INCOME or EXPENSE) is required")
    private FlowType flowType;

    @NotBlank(message = "Contact name is required")
    private String contactName;

    private String channel;

    private String cardLast4;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;

    @NotBlank(message = "Transaction hash is required")
    private String transactionHash;

    private String rawNotificationText;
}