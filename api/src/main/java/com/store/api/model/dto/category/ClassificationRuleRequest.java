package com.store.api.model.dto.category;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassificationRuleRequest {
    @NotBlank @Size(max = 150)
    private String matchText;
    @NotNull @Positive
    private Long categoryId;
    @NotNull @Min(0) @Max(9999)
    private Integer priority;
    @NotNull
    private Boolean enabled;
}
