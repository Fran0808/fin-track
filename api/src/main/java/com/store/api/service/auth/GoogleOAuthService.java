package com.store.api.service.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.store.api.model.dto.auth.GoogleAuthStatusResponse;
import com.store.api.model.entity.GoogleOAuthToken;
import com.store.api.repository.GoogleOAuthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.store.api.model.entity.User;
import com.store.api.config.security.UserContext;
import com.store.api.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String GMAIL_OAUTH_SCOPES = "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/gmail.readonly";

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri:http://localhost:8080/api/v1/auth/google/callback}")
    private String redirectUri;

    private final GoogleOAuthTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = com.store.api.config.ExternalHttp.client();

    private Optional<GoogleOAuthToken> findTokenForCurrentContext() {
        User currentUser = UserContext.requireCurrentUser();
        return tokenRepository.findByUserId(currentUser.getId());
    }

    public String buildAuthorizationUrl(String state) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID is not configured in .env");
        }

        return GOOGLE_AUTH_ENDPOINT + "?"
                + "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(GMAIL_OAUTH_SCOPES, StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&prompt=consent";
    }

    @Transactional
    public GoogleOAuthToken exchangeCodeForTokens(String authorizationCode) {
        log.info("Exchanging authorization code with Google OAuth2 server...");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", authorizationCode);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        String rawResponse = restClient.post()
                .uri(GOOGLE_TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(rawResponse);
            String accessToken = json.path("access_token").asText();
            String refreshToken = json.path("refresh_token").asText(null);
            long expiresIn = json.path("expires_in").asLong(3600);
            String scope = json.path("scope").asText("");
            if (accessToken.isBlank()) throw new IllegalStateException("Google access token is missing");

            GoogleUserProfile profile = fetchUserProfile(accessToken);
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);

            User user = userRepository.findByGoogleSubId(profile.sub()).or(() -> userRepository.findByEmail(profile.email()))
                    .orElseGet(() -> {
                        log.info("Registering verified Google user");
                        return User.builder()
                                .email(profile.email())
                                .build();
                    });

            if (user.getGoogleSubId() != null && !user.getGoogleSubId().equals(profile.sub())) {
                throw new IllegalStateException("Google identity does not match the existing user");
            }

            user.setFullName(profile.name());
            user.setEmail(profile.email());
            user.setPictureUrl(profile.picture());
            user.setGoogleSubId(profile.sub());
            user.setLastLoginAt(LocalDateTime.now());
            User savedUser = userRepository.save(user);

            Optional<GoogleOAuthToken> existingOpt = tokenRepository.findByUserId(savedUser.getId())
                    .or(() -> tokenRepository.findByEmail(profile.email()));
            GoogleOAuthToken token;

            if (existingOpt.isPresent()) {
                token = existingOpt.get();
                if (token.getUser() != null && !savedUser.getId().equals(token.getUser().getId())) {
                    throw new IllegalStateException("Google credential owner does not match");
                }
                token.setEmail(profile.email());
                token.setAccessToken(accessToken);
                if (refreshToken != null && !refreshToken.isBlank()) {
                    token.setRefreshToken(refreshToken);
                }
                token.setExpiresAt(expiresAt);
                token.setScope(scope);
                token.setUser(savedUser);
            } else {
                token = GoogleOAuthToken.builder()
                        .user(savedUser)
                        .email(profile.email())
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresAt(expiresAt)
                        .scope(scope)
                        .build();
            }

            GoogleOAuthToken saved = tokenRepository.save(token);
            log.info("Google account linked successfully");
            return saved;
        } catch (Exception ex) {
            log.warn("Google credentials could not be processed");
            throw new IllegalStateException("Google credentials could not be processed");
        }
    }

    public record GoogleUserProfile(String email, String name, String picture, String sub) {}

    private GoogleUserProfile fetchUserProfile(String accessToken) {
        try {
            String userInfoRaw = restClient.get()
                    .uri(GOOGLE_USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode userInfo = objectMapper.readTree(userInfoRaw);
            String email = userInfo.path("email").asText("");
            String name = userInfo.path("name").asText(null);
            String picture = userInfo.path("picture").asText(null);
            String sub = userInfo.has("id") ? userInfo.path("id").asText(null) : userInfo.path("sub").asText(null);
            boolean verified = userInfo.path("verified_email").asBoolean(false) || userInfo.path("email_verified").asBoolean(false);
            if (email.isBlank() || email.length() > 150 || sub == null || sub.isBlank() || sub.length() > 100 || !verified
                    || (name != null && name.length() > 200)) {
                throw new IllegalStateException("Google did not supply a verified identity");
            }

            return new GoogleUserProfile(email, name, picture, sub);
        } catch (Exception ex) {
            throw new IllegalStateException("Google identity could not be verified");
        }
    }

    @Transactional
    public Optional<String> getValidAccessToken() {
        Optional<GoogleOAuthToken> tokenOpt = findTokenForCurrentContext();
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        GoogleOAuthToken token = tokenOpt.get();
        if (!token.isExpired()) {
            return Optional.of(token.getAccessToken());
        }

        // Token expired, refresh it using refresh_token
        if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            log.warn("Google access token expired and no refresh token available. User must re-authenticate.");
            return Optional.empty();
        }

        log.info("Refreshing Google access token");
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", token.getRefreshToken());
            body.add("grant_type", "refresh_token");

            String rawResponse = restClient.post()
                    .uri(GOOGLE_TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(rawResponse);
            String newAccessToken = json.path("access_token").asText();
            if (newAccessToken.isBlank()) throw new IllegalStateException("Google refresh returned no access token");
            long expiresIn = json.path("expires_in").asLong(3600);

            token.setAccessToken(newAccessToken);
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            tokenRepository.save(token);

            log.info("Google access token refreshed successfully");
            return Optional.of(newAccessToken);
        } catch (Exception ex) {
            log.warn("Google access token could not be refreshed");
            var failure = com.store.api.service.email.EmailSyncException.classify(ex);
            if (ex instanceof org.springframework.web.client.RestClientResponseException response
                    && response.getStatusCode().value() == 400) {
                try {
                    if ("invalid_grant".equals(objectMapper.readTree(response.getResponseBodyAsString()).path("error").asText())) {
                        failure = com.store.api.model.enums.EmailSyncFailure.AUTH_REQUIRED;
                    }
                } catch (Exception ignored) {
                    // An unrecognized provider response remains an unknown failure.
                }
            }
            throw new com.store.api.service.email.EmailSyncException(failure);
        }
    }

    @Transactional(readOnly = true)
    public GoogleAuthStatusResponse getStatus() {
        User currentUser = UserContext.getCurrentUser();
        Optional<GoogleOAuthToken> tokenOpt = currentUser == null
                ? Optional.empty()
                : tokenRepository.findByUserId(currentUser.getId());
        if (tokenOpt.isPresent()) {
            return GoogleAuthStatusResponse.builder()
                    .connected(true)
                    .email(tokenOpt.get().getEmail())
                    .lastSuccessfulSyncAt(tokenOpt.get().getLastSuccessfulSyncAt())
                    .lastSyncFailed(Boolean.TRUE.equals(tokenOpt.get().getLastSyncFailed()))
                    .lastSyncAttemptAt(tokenOpt.get().getLastSyncAttemptAt())
                    .lastSyncFailureCode(tokenOpt.get().getLastSyncFailureCode())
                    .build();
        }
        return GoogleAuthStatusResponse.builder()
                .connected(false)
                .email(null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<User> getConnectedUsers() {
        return tokenRepository.findConnectedUsers();
    }

    @Transactional(readOnly = true)
    public Optional<Long> getLastSyncedInternalDate() {
        return findTokenForCurrentContext()
                .map(GoogleOAuthToken::getLastSyncedInternalDate);
    }

    @Transactional
    public void updateLastSyncedInternalDate(Long internalDateMs) {
        if (internalDateMs == null || internalDateMs <= 0) {
            return;
        }
        tokenRepository.advanceCursor(UserContext.requireCurrentUser().getId(), internalDateMs);
    }

    @Transactional
    public void recordSyncResult(boolean success) {
        recordSyncResult(success, success ? null : com.store.api.model.enums.EmailSyncFailure.UNKNOWN);
    }

    @Transactional
    public void recordSyncResult(boolean success, com.store.api.model.enums.EmailSyncFailure failure) {
        tokenRepository.recordSyncResult(UserContext.requireCurrentUser().getId(), !success, Instant.now(),
                success ? null : (failure == null ? com.store.api.model.enums.EmailSyncFailure.UNKNOWN : failure));
    }

    @Transactional
    public void disconnect() {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        tokenRepository.findByUserId(currentUser.getId()).ifPresent(tokenRepository::delete);
        log.info("Google account disconnected");
    }
}
