package com.store.api.service.auth;

import com.store.api.config.security.UserContext;
import com.store.api.model.entity.GoogleOAuthToken;
import com.store.api.model.entity.User;
import com.store.api.repository.GoogleOAuthTokenRepository;
import com.store.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
