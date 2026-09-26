package com.store.api.service.email.scheduler;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.entity.User;
import com.store.api.service.auth.GoogleOAuthService;
import com.store.api.service.email.EmailIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailSyncSchedulerTest {
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void syncsEachExplicitUserAndClearsContextAfterFailures() {
        EmailIngestionService ingestion = mock(EmailIngestionService.class);
        GoogleOAuthService google = mock(GoogleOAuthService.class);
        User first = User.builder().id(10L).email("first@example.test").build();
        User second = User.builder().id(20L).email("second@example.test").build();
        when(google.getConnectedUsers()).thenReturn(List.of(first, second));
        when(ingestion.hasActiveConnection()).thenReturn(true);
        List<Long> syncedUsers = new ArrayList<>();
        when(ingestion.syncEmails()).thenAnswer(invocation -> {
            Long id = UserContext.requireCurrentUser().getId();
            syncedUsers.add(id);
            if (id == 10L) {
                throw new IllegalStateException("Test account unavailable");
            }
            return EmailSyncResponse.builder().scannedCount(0).savedCount(0).build();
        });

        new EmailSyncScheduler(ingestion, google).scheduleEmailSync();

        assertEquals(List.of(10L, 20L), syncedUsers);
        assertNull(UserContext.getCurrentUser());
    }
}
