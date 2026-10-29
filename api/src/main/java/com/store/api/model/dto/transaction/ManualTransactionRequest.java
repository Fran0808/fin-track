package com.store.api.model.dto.transaction;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.ManualPaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ManualTransactionRequest {
    @NotBlank @Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    private String requestId;
    @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;
    @NotNull private FlowType flowType;
    @NotBlank @Size(max = 150) private String contactName;
    @NotNull private LocalDateTime transactionDate;
    @NotNull private ManualPaymentMethod paymentMethod;
    @Positive private Long categoryId;
}
