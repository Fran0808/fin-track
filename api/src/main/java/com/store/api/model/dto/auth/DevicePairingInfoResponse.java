package com.store.api.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePairingInfoResponse {
    private Long userId;
    private String userEmail;
    private String pairingToken;
    private String serverUrl;
    private String qrPayload;
}
