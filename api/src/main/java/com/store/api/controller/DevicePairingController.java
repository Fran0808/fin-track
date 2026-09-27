package com.store.api.controller;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.auth.DevicePairingInfoResponse;
import com.store.api.model.dto.auth.DevicePairingVerifyResponse;
import com.store.api.model.entity.User;
import com.store.api.service.auth.DevicePairingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/pairing-info")
@RequiredArgsConstructor
@Slf4j
public class DevicePairingController {

    private final DevicePairingService devicePairingService;

    @GetMapping
    public ResponseEntity<DevicePairingInfoResponse> getPairingInfo() {
        User currentUser = UserContext.requireCurrentUser();
        return ResponseEntity.ok(devicePairingService.getPairingInfo(currentUser));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<DevicePairingInfoResponse> regeneratePairingToken() {
        User currentUser = UserContext.requireCurrentUser();
        return ResponseEntity.ok(devicePairingService.regeneratePairingToken(currentUser));
    }

    @PostMapping("/verify")
    public ResponseEntity<DevicePairingVerifyResponse> verifyPairing(
            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String token = headerToken;
        if ((token == null || token.isBlank()) && body != null) {
            token = body.get("token");
        }

        DevicePairingVerifyResponse response = devicePairingService.verifyDeviceToken(token);
        if (response.isValid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }
}
