package com.store.api.model.dto.transaction;

import com.store.api.model.enums.FlowType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionCorrectionRequest {
    @NotBlank @Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    private String requestId;
    @NotNull @PositiveOrZero
    private Long expectedRevision;
    @NotBlank @Size(max = 300)
    private String reason;
    @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;
    @NotNull
    private LocalDateTime transactionDate;
    @NotNull
    private FlowType flowType;
}
