package com.store.api.model.dto.transaction;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ManualReconciliationRequest {
    @NotNull @Positive private Long capturedId;
    @NotNull @Positive private Long expiresAt;
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}") private String previewToken;
}
