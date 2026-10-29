package com.store.api.model.dto.transaction;

import com.store.api.model.entity.TransactionCorrection;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @AllArgsConstructor
public class TransactionCorrectionHistory {
    private TransactionResponse transaction;
    private long revision;
    private boolean editable;
    private List<Entry> entries;
    private int totalPages;

    @Data @AllArgsConstructor
    public static class Entry {
        private Long id;
        private String reason;
        private BigDecimal beforeAmount;
        private BigDecimal afterAmount;
        private LocalDateTime beforeDate;
        private LocalDateTime afterDate;
        private FlowType beforeFlow;
        private FlowType afterFlow;
        private OperationType beforeOperation;
        private OperationType afterOperation;
        private LocalDateTime createdAt;

        public static Entry from(TransactionCorrection row) {
            return new Entry(row.getId(), row.getReason(), row.getBeforeAmount(), row.getAfterAmount(), row.getBeforeDate(),
                    row.getAfterDate(), row.getBeforeFlow(), row.getAfterFlow(), row.getBeforeOperation(), row.getAfterOperation(), row.getCreatedAt());
        }
    }
}
