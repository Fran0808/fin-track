package com.store.api.service.auth;

import com.store.api.config.security.UserContext;
import com.store.api.model.entity.GoogleOAuthToken;
import com.store.api.model.entity.User;
import com.store.api.repository.GoogleOAuthTokenRepository;
import com.store.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleOAuthServiceTest {
    private final GoogleOAuthTokenRepository tokens = mock(GoogleOAuthTokenRepository.class);
    private final GoogleOAuthService service = new GoogleOAuthService(tokens, mock(UserRepository.class), new ObjectMapper());

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void failedOrUnverifiedProfileCannotCreateAUserOrStoreCredentials() {
        var users = mock(UserRepository.class);
        var securedService = new GoogleOAuthService(tokens, users, new ObjectMapper());
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        org.springframework.test.util.ReflectionTestUtils.setField(securedService, "restClient", builder.build());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"synthetic-token\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        assertThrows(IllegalStateException.class, () -> securedService.exchangeCodeForTokens("synthetic-code"));
        verifyNoInteractions(users, tokens);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":\"subject\",\"email\":\"test@example.test\",\"verified_email\":false}",
            "{\"email\":\"test@example.test\",\"verified_email\":true}"})
    void incompleteIdentityCannotCreateAnAccount(String profile) {
        var users = mock(UserRepository.class);
        var securedService = new GoogleOAuthService(tokens, users, new ObjectMapper());
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        org.springframework.test.util.ReflectionTestUtils.setField(securedService, "restClient", builder.build());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"synthetic-token\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(profile, org.springframework.http.MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> securedService.exchangeCodeForTokens("synthetic-code"));
        verifyNoInteractions(users, tokens);
        server.verify();
    }

    @Test
    void anonymousAccessCannotSelectOrUpdateAnyGoogleAccount() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class, service::getValidAccessToken).getStatusCode().value());
        assertThrows(ResponseStatusException.class, service::getLastSyncedInternalDate);
        assertThrows(ResponseStatusException.class, () -> service.recordSyncResult(false));
        assertThrows(ResponseStatusException.class, () -> service.updateLastSyncedInternalDate(100L));
        verifyNoInteractions(tokens);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"401,AUTH_REQUIRED", "429,RATE_LIMITED", "503,TEMPORARY", "400,UNKNOWN", "400,AUTH_REQUIRED"})
    void failedCredentialRefreshRetainsSafeCauseInsteadOfClaimingEveryFailureNeedsReauthorization(
            int status, com.store.api.model.enums.EmailSyncFailure expected) {
        UserContext.setCurrentUser(User.builder().id(10L).build());
        when(tokens.findByUserId(10L)).thenReturn(Optional.of(GoogleOAuthToken.builder()
                .accessToken("synthetic-expired-access").refreshToken("synthetic-refresh")
                .expiresAt(LocalDateTime.now().minusHours(1)).build()));
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "restClient", builder.build());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.valueOf(status)).body(
                                status == 400 && expected == com.store.api.model.enums.EmailSyncFailure.AUTH_REQUIRED
                                        ? "{\"error\":\"invalid_grant\"}" : "private provider text"));
        var error = assertThrows(com.store.api.service.email.EmailSyncException.class, service::getValidAccessToken);
        assertEquals(expected, error.getFailure());
        assertFalse(error.toString().contains("private provider text"));
        verify(tokens, never()).save(any());
        server.verify();
    }

    @Test void recordsSafeFailureAndClearsItAfterRecovery() {
        UserContext.setCurrentUser(User.builder().id(10L).build());
        service.recordSyncResult(false, com.store.api.model.enums.EmailSyncFailure.PROCESSING);
        service.recordSyncResult(true, com.store.api.model.enums.EmailSyncFailure.TEMPORARY);
        verify(tokens).recordSyncResult(eq(10L), eq(true), any(), eq(com.store.api.model.enums.EmailSyncFailure.PROCESSING));
        verify(tokens).recordSyncResult(eq(10L), eq(false), any(), isNull());
    }

    @Test void statusIncludesOnlyOwnedMetadataWithoutRefreshingCredentials() {
        UserContext.setCurrentUser(User.builder().id(10L).build());
        when(tokens.findByUserId(10L)).thenReturn(Optional.of(GoogleOAuthToken.builder()
                .email("owner@example.test").lastSyncFailed(true)
                .lastSyncAttemptAt(java.time.Instant.parse("2026-10-27T12:00:00Z"))
                .lastSyncFailureCode(com.store.api.model.enums.EmailSyncFailure.TEMPORARY).build()));
        var result = service.getStatus();
        assertTrue(result.isLastSyncFailed());
        assertEquals(com.store.api.model.enums.EmailSyncFailure.TEMPORARY, result.getLastSyncFailureCode());
        assertNotNull(result.getLastSyncAttemptAt());
        assertNull(result.getLastSuccessfulSyncAt());
        verify(tokens).findByUserId(10L);
        verifyNoMoreInteractions(tokens);
    }

    @Test
    void googleCredentialsAreResolvedOnlyForTheCurrentUser() {
        UserContext.setCurrentUser(User.builder().id(10L).build());
        when(tokens.findByUserId(10L)).thenReturn(Optional.of(GoogleOAuthToken.builder()
                .accessToken("first-user-access-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build()));
        when(tokens.findByUserId(20L)).thenReturn(Optional.empty());
        assertEquals(Optional.of("first-user-access-token"), service.getValidAccessToken());

        UserContext.setCurrentUser(User.builder().id(20L).build());
        assertEquals(Optional.empty(), service.getValidAccessToken());
        verify(tokens).findByUserId(10L);
        verify(tokens).findByUserId(20L);
        verifyNoMoreInteractions(tokens);
    }
}
