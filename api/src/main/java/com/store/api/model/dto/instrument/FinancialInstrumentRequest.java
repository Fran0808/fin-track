package com.store.api.model.dto.instrument;

import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class FinancialInstrumentRequest {
    @NotNull private InstrumentType type;
    @NotBlank @Size(max = 100) private String alias;
    @NotNull private Bank bank;
    @Size(max = 100) private String institutionName;
    @Pattern(regexp = "[0-9]{4}") private String lastFour;
    @NotNull private Boolean active = true;
    @Positive private Long linkedAccountId;
}
