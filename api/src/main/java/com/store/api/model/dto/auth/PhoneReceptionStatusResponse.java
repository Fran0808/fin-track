package com.store.api.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class PhoneReceptionStatusResponse {
    private Instant lastReceivedAt;
}
