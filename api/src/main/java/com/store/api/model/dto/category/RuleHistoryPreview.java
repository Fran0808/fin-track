package com.store.api.model.dto.category;

import com.store.api.model.dto.transaction.TransactionResponse;
import lombok.*;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RuleHistoryPreview {
    private Long ruleId;
    private String matchText;
    private String categoryName;
    private List<TransactionResponse> transactions;
    private int limit;
    private boolean hasMore;
    private long expiresAt;
    private String previewToken;
}
