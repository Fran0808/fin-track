package com.store.api.model.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ManualReconciliationPreview {
    private TransactionResponse manual;
    private List<Candidate> candidates;
    private int limit;
    private boolean hasMore;
    private long expiresAt;

    @Data
    @AllArgsConstructor
    public static class Candidate {
        private TransactionResponse transaction;
        private String previewToken;
    }
}
