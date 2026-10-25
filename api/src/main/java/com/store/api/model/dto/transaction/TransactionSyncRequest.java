package com.store.api.model.dto.transaction;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionSyncRequest {
    public static final int MAX_RAW_TEXT_LENGTH = 65536;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax("99999999.99")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Flow type (INCOME or EXPENSE) is required")
    private FlowType flowType;

    // Optional for older device clients; absence/null means no operation evidence.
    private OperationType operationType;

    @NotBlank(message = "Contact name is required")
    @Size(max = 150)
    private String contactName;

    @Size(max = 50)
    private String channel;

    @Size(max = 10)
    private String cardLast4;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;

    @NotBlank(message = "Transaction hash is required")
    @Size(max = 64)
    private String transactionHash;

    @Size(max = MAX_RAW_TEXT_LENGTH)
    private String rawNotificationText;
}
