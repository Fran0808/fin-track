package com.store.api.model.dto.email;

import com.store.api.model.dto.transaction.TransactionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSyncResponse {
    private String status;
    private int scannedCount;
    private int processedInBatch;
    private int savedCount;
    private List<TransactionResponse> transactions;
    private String message;
}
