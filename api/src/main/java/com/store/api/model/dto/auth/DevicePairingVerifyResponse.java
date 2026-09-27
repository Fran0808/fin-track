package com.store.api.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePairingVerifyResponse {
    private boolean valid;
    private Long userId;
    private String userEmail;
    private String fullName;
    private String message;
}
