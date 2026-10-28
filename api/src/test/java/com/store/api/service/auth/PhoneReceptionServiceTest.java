package com.store.api.service.auth;

import com.store.api.config.security.UserContext;
import com.store.api.model.entity.User;
import com.store.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhoneReceptionServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PhoneReceptionService service = new PhoneReceptionService(users);

    @AfterEach void clear() { UserContext.clear(); }

    @Test void rejectsAnonymousWithoutReadingAnyAccount() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, service::getStatus);
        verifyNoInteractions(users);
    }

    @Test void readsOnlyCurrentAccountAndDoesNotGuessHistoricalActivity() {
        UserContext.setCurrentUser(User.builder().id(1L).build());
        Instant received = Instant.parse("2026-10-27T12:00:00Z");
        when(users.findLastPhoneReceivedAt(1L)).thenReturn(Optional.of(received));
        assertEquals(received, service.getStatus().getLastReceivedAt());
        UserContext.setCurrentUser(User.builder().id(2L).build());
        when(users.findLastPhoneReceivedAt(2L)).thenReturn(Optional.empty());
        assertNull(service.getStatus().getLastReceivedAt());
        verify(users).findLastPhoneReceivedAt(1L);
        verify(users).findLastPhoneReceivedAt(2L);
        verifyNoMoreInteractions(users);
    }
}
