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
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired ManualTransactionService manual;
    @Autowired com.store.api.service.analytics.AnalyticsService analytics;
    @Autowired com.store.api.repository.CategoryRepository categories;
    @Autowired com.store.api.repository.ClassificationRuleRepository rules;
    @Autowired com.store.api.service.category.RuleHistoryService ruleHistory;

    private com.store.api.model.dto.transaction.ManualTransactionRequest manualRequest() {
        var req = ManualTransactionServiceTest.request(); req.setRequestId(UUID.randomUUID().toString());
        req.setAmount(new BigDecimal("2.30")); req.setTransactionDate(LocalDateTime.of(2026,10,1,12,0)); return req;
    }
    private com.store.api.model.dto.transaction.ManualReconciliationRequest reviewed(
            com.store.api.model.dto.transaction.ManualReconciliationPreview preview, int index) {
        var result = new com.store.api.model.dto.transaction.ManualReconciliationRequest();
        result.setCapturedId(preview.getCandidates().get(index).getTransaction().getId());
        result.setExpiresAt(preview.getExpiresAt()); result.setPreviewToken(preview.getCandidates().get(index).getPreviewToken()); return result;
    }

    @Test void manualCreationHasPrivateDecimalIdentityAndDoesNotMarkPhoneReception() {
        User owner=user(), other=user(); UserContext.setCurrentUser(owner);
        try {
            var req=manualRequest(); req.setAmount(new BigDecimal("0.10"));
            var first=manual.create(req); var again=manual.create(req);
            assertEquals(first.getId(),again.getId()); assertEquals(1,transactions.countByUserId(owner.getId()));
            assertTrue(users.findLastPhoneReceivedAt(owner.getId()).isEmpty());
            assertEquals(new BigDecimal("0.10"),transactions.sumAmountByFlowType(FlowType.EXPENSE,owner.getId()));
            req.setAmount(new BigDecimal("0.11"));
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.create(req)).getStatusCode().value());
            UserContext.setCurrentUser(other); req.setAmount(new BigDecimal("0.10"));
            assertNotEquals(first.getId(),manual.create(req).getId());
            assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.status(first.getId())).getStatusCode().value());
        } finally { UserContext.clear(); }
    }

    @Test void reconciliationPreservesAuditAndExcludesManualFromAllFinancialReadsUntilUndo() {
        User owner=user(); UserContext.setCurrentUser(owner);
        try {
            var req=manualRequest(); req.setContactName("Synthetic manual only");
            var entry=manual.create(req); var capture=service.processEmailAndSave(request("manual-capture"),"OP-123");
            assertEquals(new BigDecimal("4.60"),analytics.getSummary().getTotalExpense());
            var preview=manual.preview(entry.getId()); assertEquals(1,preview.getCandidates().size());
            var command=reviewed(preview,0); assertEquals(capture.getId(),manual.reconcile(entry.getId(),command).getId());
            assertEquals(capture.getId(),manual.reconcile(entry.getId(),command).getId());
            assertEquals(new BigDecimal("2.30"),analytics.getSummary().getTotalExpense());
            assertEquals(1,transactions.countByUserId(owner.getId()));
            var period=analytics.getPeriodAnalytics(2026,10);
            assertEquals(new BigDecimal("2.30"),period.getMonthlyExpense());
            assertEquals(1,period.getTotalMovements());
            assertEquals(1,period.getCategoryBreakdown().getFirst().getCount());
            assertEquals(1,period.getTopMerchants().size());
            assertEquals("Example store",period.getLargestExpense().getMerchantName());
            var page=service.getTransactions(null,null,null,null,org.springframework.data.domain.PageRequest.of(0,10));
            assertEquals(1,page.getTotalElements()); assertEquals(capture.getId(),page.getContent().getFirst().getId());
            assertEquals(1,service.getPendingTransactions(com.store.api.model.enums.PendingReason.ALL,org.springframework.data.domain.PageRequest.of(0,10)).getTotalElements());
            String csv=new String(service.exportTransactionsCsv(null,null,null,null,null,null,null,null),java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(csv.contains("Synthetic manual only")); assertTrue(csv.contains("Example store"));
            var state=manual.status(capture.getId()); assertEquals(entry.getId(),state.getManual().getId());
            assertNotNull(state.getReconciledAt()); assertEquals(entry.getAmount(),state.getManual().getAmount());
            assertEquals("OP-123",transactions.findById(capture.getId()).orElseThrow().getOperationReference());
            var targetCategory=categories.saveAndFlush(com.store.api.model.entity.Category.builder().user(owner).name("Synthetic category").build());
            var rule=rules.saveAndFlush(com.store.api.model.entity.ClassificationRule.builder().user(owner).category(targetCategory)
                    .matchText("Synthetic manual only").enabled(true).priority(1).build());
            assertTrue(ruleHistory.preview(rule.getId()).getTransactions().isEmpty());
            var categoryEdit=new com.store.api.model.dto.transaction.TransactionClassificationRequest(); categoryEdit.setCategory("Synthetic category");
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateClassification(entry.getId(),categoryEdit)).getStatusCode().value());
            manual.undo(entry.getId(),capture.getId(),1); manual.undo(entry.getId(),capture.getId(),1);
            assertEquals(new BigDecimal("4.60"),analytics.getSummary().getTotalExpense());
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.reconcile(entry.getId(),command)).getStatusCode().value());
            manual.reconcile(entry.getId(),reviewed(manual.preview(entry.getId()),0));
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.undo(entry.getId(),capture.getId(),1)).getStatusCode().value());
            assertEquals(new BigDecimal("2.30"),analytics.getSummary().getTotalExpense());
        } finally { UserContext.clear(); }
    }

    @Test void reconciliationReviewRejectsTamperingExpiryChangedFieldsAndForeignIds() {
        User owner=user(), other=user(); UserContext.setCurrentUser(owner);
        try {
            var entry=manual.create(manualRequest()); var capture=service.processAndSave(request("review-capture"));
            var preview=manual.preview(entry.getId()); var command=reviewed(preview,0); command.setPreviewToken("0".repeat(64));
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.reconcile(entry.getId(),command)).getStatusCode().value());
            command.setPreviewToken(preview.getCandidates().getFirst().getPreviewToken()); command.setExpiresAt(1L);
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.reconcile(entry.getId(),command)).getStatusCode().value());
            command.setExpiresAt(preview.getExpiresAt());
            var category=categories.saveAndFlush(com.store.api.model.entity.Category.builder().user(owner).name("Changed review").build());
            var change=new com.store.api.model.dto.transaction.TransactionClassificationRequest();change.setCategory(category.getName());
            service.updateClassification(capture.getId(),change);
            assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.reconcile(entry.getId(),command)).getStatusCode().value());
            UserContext.setCurrentUser(other);var foreign=service.processAndSave(request("review-capture"));UserContext.setCurrentUser(owner);
            command.setCapturedId(foreign.getId());
            assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.reconcile(entry.getId(),command)).getStatusCode().value());
            assertNull(manual.status(entry.getId()).getCaptured());
        } finally { UserContext.clear(); }
    }

    @Test void reconciliationUsesCaptureDateWithoutRewritingTheOriginalManualMonth() {
        User owner=user(); UserContext.setCurrentUser(owner);
        try {
            var req=manualRequest(); req.setFlowType(FlowType.INCOME);req.setTransactionDate(LocalDateTime.of(2026,8,31,22,0));
            var entry=manual.create(req);var sync=request("cross-month");sync.setFlowType(FlowType.INCOME);sync.setTransactionDate(LocalDateTime.of(2026,9,1,8,0));
            var capture=service.processEmailAndSave(sync,null);
            manual.reconcile(entry.getId(),reviewed(manual.preview(entry.getId()),0));
            assertEquals(0,analytics.getPeriodAnalytics(2026,8).getMonthlyIncome().signum());
            assertEquals(new BigDecimal("2.30"),analytics.getPeriodAnalytics(2026,9).getMonthlyIncome());
            assertEquals(req.getTransactionDate(),manual.status(capture.getId()).getManual().getTransactionDate());
            assertEquals(0,analytics.getSummary().getTotalExpense().signum());
        } finally { UserContext.clear(); }
    }

    @Test void concurrentManualRequestsAndReconciliationsCannotCountTheSameIntentTwice() throws Exception {
        User owner=user();var req=manualRequest();
        try (ExecutorService executor=Executors.newFixedThreadPool(2)) {
            var tasks=List.of(0,1).stream().map(i->executor.submit(()->{
                UserContext.setCurrentUser(owner);try{return manual.create(req).getId();}finally{UserContext.clear();}
            })).toList();
            assertEquals(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS));
        }
        UserContext.setCurrentUser(owner);
        Long first,second,captured;
        com.store.api.model.dto.transaction.ManualReconciliationRequest a,b;
        try {
            first=manual.create(req).getId();second=manual.create(manualRequest()).getId();captured=service.processAndSave(request("shared-capture")).getId();
            a=reviewed(manual.preview(first),0);b=reviewed(manual.preview(second),0);
        } finally {UserContext.clear();}
        try (ExecutorService executor=Executors.newFixedThreadPool(2)) {
            var f1=executor.submit(()->reconcileOutcome(owner,first,a));var f2=executor.submit(()->reconcileOutcome(owner,second,b));
            assertEquals(1,f1.get(20,TimeUnit.SECONDS)+f2.get(20,TimeUnit.SECONDS));
        }
        assertEquals(2,transactions.countByUserId(owner.getId()));
        assertEquals(new BigDecimal("4.60"),transactions.sumAmountByFlowType(FlowType.EXPENSE,owner.getId()));
    }

    private int reconcileOutcome(User owner,Long id,com.store.api.model.dto.transaction.ManualReconciliationRequest request) {
        UserContext.setCurrentUser(owner);
        try {manual.reconcile(id,request);return 1;}
        catch(org.springframework.web.server.ResponseStatusException failure){assertEquals(409,failure.getStatusCode().value());return 0;}
        finally{UserContext.clear();}
    }


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
            google.recordSyncResult(false, com.store.api.model.enums.EmailSyncFailure.PROCESSING);
            var token = googleTokens.findByUserId(owner.getId()).orElseThrow();
            assertEquals(200L, token.getLastSyncedInternalDate());
            assertTrue(token.getLastSyncFailed());
            assertNotNull(token.getLastSuccessfulSyncAt());
            assertNotNull(token.getLastSyncAttemptAt());
            assertEquals(com.store.api.model.enums.EmailSyncFailure.PROCESSING, token.getLastSyncFailureCode());
            var successfulAt = token.getLastSuccessfulSyncAt();
            google.recordSyncResult(true);
            var recovered = googleTokens.findByUserId(owner.getId()).orElseThrow();
            assertFalse(recovered.getLastSyncFailed());
            assertNull(recovered.getLastSyncFailureCode());
            assertFalse(recovered.getLastSuccessfulSyncAt().isBefore(successfulAt));
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            var future = java.time.Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            transaction.executeWithoutResult(status -> {
                googleTokens.recordSyncResult(owner.getId(), true, future, com.store.api.model.enums.EmailSyncFailure.RATE_LIMITED);
                googleTokens.recordSyncResult(owner.getId(), false, future.minusSeconds(1), null);
            });
            var ordered = googleTokens.findByUserId(owner.getId()).orElseThrow();
            assertEquals(com.store.api.model.enums.EmailSyncFailure.RATE_LIMITED, ordered.getLastSyncFailureCode());
            assertTrue(ordered.getLastSyncFailed());
            assertEquals(future.truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                    ordered.getLastSyncAttemptAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        } finally { UserContext.clear(); }
    }

    @Test void phoneReceptionTracksAcceptedRetriesButNotEmailOrInvalidBatches() {
        User owner = user();
        User other = user();
        UserContext.setCurrentUser(owner);
        try {
            var req = request("reception");
            service.processEmailAndSave(req, null);
            assertTrue(users.findLastPhoneReceivedAt(owner.getId()).isEmpty());
            service.processAndSave(req);
            var first = users.findLastPhoneReceivedAt(owner.getId()).orElseThrow();
            service.processBatch(List.of(req));
            var second = users.findLastPhoneReceivedAt(owner.getId()).orElseThrow();
            assertFalse(second.isBefore(first));
            assertTrue(users.findLastPhoneReceivedAt(other.getId()).isEmpty());
            var invalid = request("invalid-reception"); invalid.setAmount(new BigDecimal("-1"));
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.processBatch(List.of(req, invalid)));
            assertEquals(second, users.findLastPhoneReceivedAt(owner.getId()).orElseThrow());
            owner.setFullName("Synthetic updated profile");
            users.saveAndFlush(owner);
            assertEquals(second, users.findLastPhoneReceivedAt(owner.getId()).orElseThrow());
            var contradictory = request("contradictory-reception");
            contradictory.setOperationType(com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT);
            var valid = request("rolled-back-reception");
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service.processBatch(List.of(valid, contradictory)));
            assertEquals(second, users.findLastPhoneReceivedAt(owner.getId()).orElseThrow());
            assertFalse(transactions.existsByTransactionHashAndUser(valid.getTransactionHash(), owner));
            assertEquals(com.store.api.model.enums.CaptureOrigin.EMAIL,
                    transactions.findByTransactionHashAndUser(req.getTransactionHash(), owner).orElseThrow().getCaptureOrigin());
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
            var evidence = rawLogs.findAll().stream()
                    .filter(raw -> raw.getTransaction() != null
                            && raw.getTransaction().getId().equals(id)).toList();
            assertEquals(1, evidence.size());
            assertEquals(owner.getId(), evidence.getFirst().getUser().getId());
            assertEquals(before + 2, rawLogs.count());
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
