package com.store.api.service.email.scheduler;

import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.service.email.EmailIngestionService;
import com.store.api.service.auth.GoogleOAuthService;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mail.sync.enabled", havingValue = "true")
public class EmailSyncScheduler {

    private final EmailIngestionService emailIngestionService;
    private final GoogleOAuthService googleOAuthService;

    @Scheduled(fixedDelayString = "${mail.sync.fixed-delay-ms:60000}", initialDelay = 15000)
    public void scheduleEmailSync() {
        java.util.List<User> connectedUsers = googleOAuthService.getConnectedUsers();
        if (connectedUsers.isEmpty()) {
            log.debug("Background email synchronization skipped: Google account not yet connected.");
            return;
        }

        for (User user : connectedUsers) {
            UserContext.setCurrentUser(user);
            try {
                if (!emailIngestionService.hasActiveConnection()) {
                    googleOAuthService.recordSyncResult(false);
                    log.warn("Background email synchronization skipped: connection unavailable");
                    continue;
                }
                EmailSyncResponse response = emailIngestionService.syncEmails();
                log.info("Scheduled email sync completed: scanned={}, saved={}", response.getScannedCount(), response.getSavedCount());
            } catch (Exception e) {
                log.warn("Scheduled email synchronization failed");
            } finally {
                UserContext.clear();
            }
        }
    }
}
