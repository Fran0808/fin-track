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
