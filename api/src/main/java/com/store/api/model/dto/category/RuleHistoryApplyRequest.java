package com.store.api.model.dto.category;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RuleHistoryApplyRequest {
    @NotEmpty @Size(max = 200)
    private List<@NotNull @Positive Long> transactionIds;
    @NotNull @Positive
    private Long expiresAt;
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}")
    private String previewToken;
}
