package com.store.api.model.dto.category;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class RuleHistoryApplyResponse {
    private int appliedCount;
    private int skippedCount;
}
