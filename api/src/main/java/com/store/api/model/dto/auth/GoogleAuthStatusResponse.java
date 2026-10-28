package com.store.api.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthStatusResponse {
    private boolean connected;
    private String email;
    private Instant lastSuccessfulSyncAt;
    private boolean lastSyncFailed;
    private Instant lastSyncAttemptAt;
    private com.store.api.model.enums.EmailSyncFailure lastSyncFailureCode;
}
