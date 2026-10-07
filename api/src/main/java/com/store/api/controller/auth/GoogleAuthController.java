package com.store.api.controller.auth;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.auth.GoogleAuthStatusResponse;
import com.store.api.model.dto.auth.GoogleAuthUrlResponse;
import com.store.api.model.dto.auth.UserResponse;
import com.store.api.model.entity.GoogleOAuthToken;
import com.store.api.model.entity.User;
import com.store.api.service.auth.GoogleOAuthService;
import com.store.api.service.auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;

    @Value("${google.oauth.frontend-success-url:http://localhost:5173?auth=google_connected}")
    private String frontendSuccessUrl;

    @Value("${google.oauth.frontend-error-url:http://localhost:5173?auth=google_error}")
    private String frontendErrorUrl;

    @GetMapping("/url")
    public ResponseEntity<GoogleAuthUrlResponse> getAuthorizationUrl() {
        String url = googleOAuthService.buildAuthorizationUrl();
        return ResponseEntity.ok(new GoogleAuthUrlResponse(url));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error
    ) {
        if (error != null || code == null || code.isBlank()) {
            log.warn("Google OAuth callback returned error or empty code: error={}", error);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendErrorUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        try {
            GoogleOAuthToken token = googleOAuthService.exchangeCodeForTokens(code);
            String jwt = jwtService.generateToken(token.getUser());
            String targetUrl = frontendSuccessUrl.contains("?")
                    ? frontendSuccessUrl + "&token=" + jwt
                    : frontendSuccessUrl + "?token=" + jwt;
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(targetUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception ex) {
            log.error("Failed to process Google OAuth callback code", ex);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendErrorUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        User user = UserContext.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .pictureUrl(user.getPictureUrl())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build());
    }

    @GetMapping("/status")
    public ResponseEntity<GoogleAuthStatusResponse> getStatus() {
        return ResponseEntity.ok(googleOAuthService.getStatus());
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        googleOAuthService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
