package com.store.api.service.auth;

import com.store.api.model.dto.auth.DevicePairingInfoResponse;
import com.store.api.model.dto.auth.DevicePairingVerifyResponse;
import com.store.api.model.entity.User;
import com.store.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevicePairingService {

    private final UserRepository userRepository;

    @Value("${app.server.public-url:http://192.168.1.5:8080}")
    private String publicServerUrl;

    @Transactional
    public DevicePairingInfoResponse getPairingInfo(User user) {
        if (user.getDevicePairingToken() == null || user.getDevicePairingToken().isBlank()) {
            user.setDevicePairingToken(User.generateNewDevicePairingToken());
            user = userRepository.save(user);
            log.info("Generated initial device pairing token for user [{}]", user.getEmail());
        }

        String qrPayload = buildQrPayload(user.getDevicePairingToken(), user.getEmail());

        return DevicePairingInfoResponse.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .pairingToken(user.getDevicePairingToken())
                .serverUrl(publicServerUrl)
                .qrPayload(qrPayload)
                .build();
    }

    @Transactional
    public DevicePairingInfoResponse regeneratePairingToken(User user) {
        String newToken = User.generateNewDevicePairingToken();
        user.setDevicePairingToken(newToken);
        User savedUser = userRepository.save(user);
        log.info("Regenerated device pairing token for user [{}]", savedUser.getEmail());

        String qrPayload = buildQrPayload(savedUser.getDevicePairingToken(), savedUser.getEmail());

        return DevicePairingInfoResponse.builder()
                .userId(savedUser.getId())
                .userEmail(savedUser.getEmail())
                .pairingToken(savedUser.getDevicePairingToken())
                .serverUrl(publicServerUrl)
                .qrPayload(qrPayload)
                .build();
    }

    @Transactional(readOnly = true)
    public DevicePairingVerifyResponse verifyDeviceToken(String token) {
        if (token == null || token.isBlank()) {
            return DevicePairingVerifyResponse.builder()
                    .valid(false)
                    .message("Token cannot be empty")
                    .build();
        }

        Optional<User> userOptional = userRepository.findByDevicePairingToken(token.trim());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            return DevicePairingVerifyResponse.builder()
                    .valid(true)
                    .userId(user.getId())
                    .userEmail(user.getEmail())
                    .fullName(user.getFullName())
                    .message("Device token verified successfully")
                    .build();
        }

        return DevicePairingVerifyResponse.builder()
                .valid(false)
                .message("Invalid or revoked device token")
                .build();
    }

    private String buildQrPayload(String token, String userEmail) {
        String safeToken = token != null ? token.replace("\"", "\\\"") : "";
        String safeUrl = publicServerUrl != null ? publicServerUrl.replace("\"", "\\\"") : "";
        String safeEmail = userEmail != null ? userEmail.replace("\"", "\\\"") : "";
        return String.format("{\"token\":\"%s\",\"serverUrl\":\"%s\",\"userEmail\":\"%s\"}", safeToken, safeUrl, safeEmail);
    }
}
