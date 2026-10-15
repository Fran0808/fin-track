package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
import com.store.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// Initialization deliberately fails if application tables already exist.
@EnabledIfEnvironmentVariable(named = "DEPLOYMENT_TEST_DATABASE_URL",
        matches = "^jdbc:postgresql://localhost:[0-9]+/fintrack_deployment_eval_[a-z0-9_]+$")
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "spring.datasource.url=${DEPLOYMENT_TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DB_USER:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:synthetic-test-password}",
        "google.oauth.client-id=synthetic-client", "google.oauth.client-secret=synthetic-secret",
        "google.oauth.redirect-uri=https://fintrack.example.test/api/v1/auth/google/callback",
        "google.oauth.frontend-success-url=https://fintrack.example.test?auth=google_connected",
        "google.oauth.frontend-error-url=https://fintrack.example.test?auth=google_error",
        "app.server.public-url=https://fintrack.example.test", "app.cors.allowed-origins=https://fintrack.example.test",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../scripts/migrations/20261014_empty_database.sql",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "mail.sync.enabled=false", "jwt.secret=deployment-tests-only-signing-key-at-least-32-bytes"
})
class DeploymentPersistenceTest {
    @Autowired TransactionService service;
    @Autowired UserRepository users;
    @Autowired TransactionRepository transactions;
    @Autowired RawNotificationRepository rawLogs;
    @Autowired com.store.api.repository.GoogleOAuthTokenRepository googleTokens;
    @Autowired com.store.api.service.auth.GoogleOAuthService google;

    @Test void syncMetadataCannotMoveCursorBackwardOrOverwriteItWithStatus() {
        User owner = user();
        googleTokens.saveAndFlush(com.store.api.model.entity.GoogleOAuthToken.builder().user(owner)
                .email(owner.getEmail()).accessToken("synthetic-access-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build());
        UserContext.setCurrentUser(owner);
        try {
            google.updateLastSyncedInternalDate(200L);
            google.updateLastSyncedInternalDate(100L);
            google.recordSyncResult(true);
            google.recordSyncResult(false);
            var token = googleTokens.findByUserId(owner.getId()).orElseThrow();
            assertEquals(200L, token.getLastSyncedInternalDate());
            assertTrue(token.getLastSyncFailed());
            assertNotNull(token.getLastSuccessfulSyncAt());
        } finally { UserContext.clear(); }
    }

    @Test void concurrentRetriesKeepOneMovementAndOneRawLogPerUser() throws Exception {
        User owner = user();
        User other = user();
        long before = rawLogs.count();
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(i -> executor.submit(() -> {
                UserContext.setCurrentUser(owner);
                try {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return service.processAndSave(request("concurrent-retry")).getId();
                } finally { UserContext.clear(); }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Long id = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (Future<Long> result : futures) assertEquals(id, result.get(20, TimeUnit.SECONDS));
            assertEquals(1, transactions.countByUserId(owner.getId()));
            assertEquals(before + 1, rawLogs.count());
            UserContext.setCurrentUser(other);
            try { assertNotEquals(id, service.processAndSave(request("concurrent-retry")).getId()); }
            finally { UserContext.clear(); }
            assertEquals(1, transactions.countByUserId(other.getId()));
        }
    }

    @Test void overlappingBatchesUseConsistentLockOrderAndInvalidAmountsNeverPersist() throws Exception {
        User owner = user();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> batch(owner, List.of(request("a"), request("b"))));
            var second = executor.submit(() -> batch(owner, List.of(request("b"), request("a"))));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
        assertEquals(2, transactions.countByUserId(owner.getId()));
        UserContext.setCurrentUser(owner);
        try {
            var invalid = request("invalid"); invalid.setAmount(new BigDecimal("1.001"));
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.processAndSave(invalid));
            assertEquals(2, transactions.countByUserId(owner.getId()));
        } finally { UserContext.clear(); }
    }

    private void batch(User owner, List<TransactionSyncRequest> requests) {
        UserContext.setCurrentUser(owner);
        try { service.processBatch(requests); } finally { UserContext.clear(); }
    }
    private User user() { return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test").build()); }
    private TransactionSyncRequest request(String hash) {
        var request = new TransactionSyncRequest();
        request.setAmount(new BigDecimal("2.30")); request.setFlowType(FlowType.EXPENSE);
        request.setContactName("Example store"); request.setChannel("TEST");
        request.setTransactionDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        request.setTransactionHash(hash); request.setRawNotificationText("Synthetic financial confirmation");
        return request;
    }
}
