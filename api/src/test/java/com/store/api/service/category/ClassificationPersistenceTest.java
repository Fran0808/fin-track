package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.ClassificationRuleRequest;
import com.store.api.model.dto.transaction.TransactionClassificationRequest;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.*;
import com.store.api.model.enums.*;
import com.store.api.repository.*;
import com.store.api.service.transaction.TransactionService;
import com.store.api.service.analytics.AnalyticsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Only run against an explicitly configured disposable PostgreSQL database.
@EnabledIfEnvironmentVariable(named = "CLASSIFICATION_TEST_DATABASE_URL",
        matches = "^jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/fintrack_classification_eval_[a-z0-9_]+$")
@SpringBootTest(properties = {
        "spring.datasource.url=${CLASSIFICATION_TEST_DATABASE_URL}",
        "spring.datasource.username=${CLASSIFICATION_TEST_DATABASE_USER:fintrack_test}",
        "spring.datasource.password=${CLASSIFICATION_TEST_DATABASE_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.show-sql=false", "mail.sync.enabled=false",
        "jwt.secret=classification-tests-only-signing-key-at-least-32-bytes"
})
@Transactional
class ClassificationPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private TransactionRepository transactions;
    @Autowired private CategoryRepository categories;
    @Autowired private ClassificationRuleService rules;
    @Autowired private RuleHistoryService history;
    @Autowired private CategoryService categoryService;
    @Autowired private TransactionService service;
    @Autowired private AnalyticsService analytics;
    @Autowired private com.store.api.service.transaction.TransactionOriginService origins;
    private User owner;
    private User other;
    private Category target;

    @BeforeEach void setup() {
        owner = users.saveAndFlush(User.builder().email("owner@example.test").build());
        other = users.saveAndFlush(User.builder().email("other@example.test").build());
        target = categories.saveAndFlush(Category.builder().user(owner).name("Ocio").active(true).build());
        UserContext.setCurrentUser(owner);
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void captureOriginSurvivesRetriesAndCorrectionsAndRemainsUserScoped() {
        var request = new TransactionSyncRequest();
        request.setAmount(new BigDecimal("12.34")); request.setFlowType(FlowType.EXPENSE);
        request.setContactName("Synthetic merchant");request.setChannel("EXAMPLE_WALLET");
        request.setTransactionDate(LocalDateTime.of(2026,1,1,12,0));request.setTransactionHash("capture-shared");
        var email = service.processEmailAndSave(request,"OP-123");transactions.flush();
        var origin = origins.getOrigin(email.getId());
        assertEquals(CaptureOrigin.EMAIL,origin.getOrigin()); assertEquals("OP-123",origin.getOperationReference());
        assertEquals(email.getCreatedAt(),origin.getRegisteredAt());
        assertEquals(email.getId(),service.processAndSave(request).getId());
        assertEquals(email.getId(),service.processBatch(List.of(request)).getFirst().getId());
        service.markOwnCardPayment(email.getId());
        assertEquals("OP-123",origins.getOrigin(email.getId()).getOperationReference());
        assertEquals(origin.getRegisteredAt(),origins.getOrigin(email.getId()).getRegisteredAt());
        assertEquals(1,transactions.count());
        UserContext.setCurrentUser(other);
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->origins.getOrigin(email.getId())).getStatusCode().value());
        var phone = service.processAndSave(request);transactions.flush();
        assertEquals(CaptureOrigin.PHONE,origins.getOrigin(phone.getId()).getOrigin());
        assertNull(origins.getOrigin(phone.getId()).getOperationReference());
        UserContext.setCurrentUser(owner);
        var legacy = store(owner,"capture-legacy",null,FlowType.EXPENSE);
        assertEquals(CaptureOrigin.UNKNOWN,origins.getOrigin(legacy.getId()).getOrigin());
        request.setTransactionHash("capture-legacy");service.processEmailAndSave(request,"LATE-123");
        assertEquals(CaptureOrigin.UNKNOWN,origins.getOrigin(legacy.getId()).getOrigin());
        assertNull(origins.getOrigin(legacy.getId()).getOperationReference());
    }

    private com.store.api.model.dto.category.RuleHistoryApplyRequest historyRequest(
            com.store.api.model.dto.category.RuleHistoryPreview preview) {
        return new com.store.api.model.dto.category.RuleHistoryApplyRequest(
                preview.getTransactions().stream().map(t -> t.getId()).toList(), preview.getExpiresAt(), preview.getPreviewToken());
    }

    @Test void historyPreviewAndApplyPreserveMoneyIdentityOtherUsersAndInterveningClassifications() {
        var rule = rules.create(ClassificationRuleRequest.builder().matchText("TEST merchant").categoryId(target.getId())
                .priority(100).enabled(true).build());
        var expense = store(owner,"history-expense",null,FlowType.EXPENSE);
        var income = store(owner,"history-income","   ",FlowType.INCOME);
        var assigned = store(owner,"history-assigned","Existing",FlowType.EXPENSE);
        var transfer = store(owner,"history-transfer",null,FlowType.INTERNAL_TRANSFER);
        var foreign = store(other,"history-foreign",null,FlowType.EXPENSE);
        var preview = history.preview(rule.getId());
        assertEquals(List.of(expense.getId(),income.getId()),preview.getTransactions().stream().map(t->t.getId()).toList());
        assertNull(expense.getCategory());
        var later = store(owner,"history-arrived-later",null,FlowType.EXPENSE);
        income.setCategory("Manual"); transactions.flush();
        var result = history.apply(rule.getId(),historyRequest(preview));
        transactions.flush();
        assertEquals(1,result.getAppliedCount()); assertEquals(1,result.getSkippedCount());
        assertEquals(target.getName(),expense.getCategory()); assertEquals("Manual",income.getCategory());
        assertEquals("Existing",assigned.getCategory()); assertNull(transfer.getCategory());
        assertNull(foreign.getCategory()); assertNull(later.getCategory());
        assertEquals(new BigDecimal("10.25"),expense.getAmount());
        assertEquals(FlowType.EXPENSE,expense.getFlowType()); assertEquals("history-expense",expense.getTransactionHash());
        assertEquals(LocalDateTime.of(2026,1,1,12,0),expense.getTransactionDate()); assertEquals(6,transactions.count());
        var replay = history.apply(rule.getId(),historyRequest(preview));
        assertEquals(0,replay.getAppliedCount()); assertEquals(2,replay.getSkippedCount());
        assertEquals(List.of(later.getId()),history.preview(rule.getId()).getTransactions().stream().map(t->t.getId()).toList());
    }

    @Test void historyHonorsPriorityLiteralMatchingAndCategoryLifecycle() {
        var selected = rules.create(ClassificationRuleRequest.builder().matchText("%_ merchant").categoryId(target.getId())
                .priority(100).enabled(true).build());
        var literal = store(owner,"history-literal",null,FlowType.EXPENSE);
        literal.setContactName("%_ MERCHANT");
        var wildcard = store(owner,"history-no-wildcards",null,FlowType.EXPENSE);
        wildcard.setContactName("abc merchant"); transactions.flush();
        assertEquals(List.of(literal.getId()),history.preview(selected.getId()).getTransactions().stream().map(t->t.getId()).toList());
        var prior = rules.create(ClassificationRuleRequest.builder().matchText("merchant").categoryId(target.getId())
                .priority(0).enabled(true).build());
        assertTrue(history.preview(selected.getId()).getTransactions().isEmpty());
        rules.delete(prior.getId());
        var preview = history.preview(selected.getId());
        target.setName("Renamed"); categories.flush();
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->history.apply(selected.getId(),historyRequest(preview))).getStatusCode().value());
        assertNull(literal.getCategory());
        target.setActive(false); categories.flush();
        assertEquals(400,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->history.preview(selected.getId())).getStatusCode().value());
    }

    @Test void historyRejectsCrossTenantRequestsAndForgedSelections() {
        var selected = rules.create(ClassificationRuleRequest.builder().matchText("merchant").categoryId(target.getId())
                .priority(100).enabled(true).build());
        var owned = store(owner,"history-owned",null,FlowType.EXPENSE);
        var foreign = store(other,"history-not-owned",null,FlowType.EXPENSE);
        var preview = history.preview(selected.getId());
        UserContext.setCurrentUser(other);
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->history.preview(selected.getId())).getStatusCode().value());
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->history.apply(selected.getId(),historyRequest(preview))).getStatusCode().value());
        UserContext.setCurrentUser(owner);
        var forged = historyRequest(preview); forged.setTransactionIds(List.of(owned.getId(),foreign.getId()));
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->history.apply(selected.getId(),forged)).getStatusCode().value());
        assertNull(owned.getCategory()); assertNull(foreign.getCategory());
    }

    private Transaction store(User user, String hash, String category, FlowType flow) {
        return transactions.saveAndFlush(Transaction.builder().user(user).transactionHash(hash).contactName("Test merchant")
                .amount(new BigDecimal("10.25")).flowType(flow).category(category)
                .transactionDate(LocalDateTime.of(2026, 1, 1, 12, 0)).build());
    }

    private List<Long> pending(PendingReason reason) {
        return service.getPendingTransactions(reason, PageRequest.of(0, 10, Sort.by("id"))).getContent()
                .stream().map(item -> item.getId()).toList();
    }

    @Test void filterTotalsSpanPagesPreserveDecimalsAndSeparateTransfersAndTenants() {
        for (int i = 0; i < 12; i++) {
            var row = store(owner, "summary-expense-" + i, null, FlowType.EXPENSE);
            row.setAmount(new BigDecimal("0.10"));
        }
        store(owner, "summary-income", null, FlowType.INCOME);
        var transfer = store(owner, "summary-transfer", null, FlowType.INTERNAL_TRANSFER);
        transfer.setOperationType(OperationType.OWN_CARD_PAYMENT);
        store(other, "summary-foreign", null, FlowType.EXPENSE);
        transactions.flush();
        for (int page : new int[]{0, 1, 6, 20}) {
            var result = (com.store.api.model.dto.transaction.TransactionPageResponse) service.getTransactions(
                    null, null, null, null, null, null, null, null, null, null, null, PageRequest.of(page, 2));
            assertEquals(14, result.getTotalElements());
            assertEquals(0, new BigDecimal("1.20").compareTo(result.getSummary().getTotalExpense()));
            assertEquals(0, new BigDecimal("10.25").compareTo(result.getSummary().getTotalIncome()));
            assertEquals(0, new BigDecimal("10.25").compareTo(result.getSummary().getInternalTransfersAmount()));
        }
        var empty = (com.store.api.model.dto.transaction.TransactionPageResponse) service.getTransactions(
                null, null, null, "absent", PageRequest.of(0, 2));
        assertEquals(0, empty.getTotalElements());
        assertEquals(0, empty.getSummary().getTotalExpense().signum());
        assertEquals(0, empty.getSummary().getTotalIncome().signum());
        assertEquals(0, empty.getSummary().getInternalTransfersAmount().signum());
    }

    @Test void filterTotalsUseEveryListingPredicateIncludingHistoricalDrillDownAndReview() {
        var match = store(owner, "summary-match", "Ocio", FlowType.EXPENSE);
        match.setChannel("TEST_WALLET"); match.setContactName("Exact %_ Shop");
        var income = store(owner, "summary-other-flow", "Ocio", FlowType.INCOME);
        income.setChannel("TEST_WALLET"); income.setContactName("Exact %_ Shop");
        var outside = store(owner, "summary-other-date", "Ocio", FlowType.EXPENSE);
        outside.setTransactionDate(LocalDateTime.of(2025, 1, 1, 12, 0));
        var foreign = store(other, "summary-other-user", " Ocio ", FlowType.EXPENSE);
        foreign.setContactName("Exact %_ Shop"); foreign.setChannel("TEST_WALLET");
        store(owner, "summary-unclassified", null, FlowType.EXPENSE);
        transactions.flush();
        var result = (com.store.api.model.dto.transaction.TransactionPageResponse) service.getTransactions(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 23, 59),
                FlowType.EXPENSE, "Shop", " Ocio ", "TEST_WALLET",
                new BigDecimal("10.25"), new BigDecimal("10.25"), null, "Exact %_ Shop", "Ocio", PageRequest.of(0, 1));
        assertEquals(List.of(match.getId()), result.getContent().stream().map(t -> t.getId()).toList());
        assertEquals(0, new BigDecimal("10.25").compareTo(result.getSummary().getTotalExpense()));
        assertEquals(0, result.getSummary().getTotalIncome().signum());
        assertEquals(0, result.getSummary().getInternalTransfersAmount().signum());
        for (var reason : PendingReason.values()) {
            var review = (com.store.api.model.dto.transaction.TransactionPageResponse) service.getTransactions(
                    null, null, null, null, null, null, null, null, reason, null, "", PageRequest.of(0, 1));
            assertEquals(1, review.getTotalElements());
            assertEquals(0, new BigDecimal("10.25").compareTo(review.getSummary().getTotalExpense()));
            assertEquals(0, review.getSummary().getTotalIncome().signum());
        }
    }

    @Test void ownCardPaymentRepairPreservesIdentityAndExcludesRepaymentsFromEveryExpenseMetric() {
        var parser = new com.store.api.service.email.parser.BcpEmailParser();
        var parsed = parser.parse("Constancia de pago de tarjeta de crédito propia",
                "Realizaste un pago a tu tarjeta de S/ 81.25. Monto pagado: S/ 81.25",
                LocalDateTime.of(2026, 1, 2, 12, 0));
        assertNotNull(parsed);
        assertEquals(FlowType.INTERNAL_TRANSFER, parsed.getFlowType());
        TransactionSyncRequest replay = new TransactionSyncRequest();
        replay.setAmount(parsed.getAmount()); replay.setFlowType(parsed.getFlowType());
        replay.setOperationType(parsed.getOperationType());
        replay.setContactName(parsed.getMerchantName()); replay.setChannel(parsed.getChannel());
        replay.setTransactionDate(parsed.getTransactionDate()); replay.setTransactionHash(parsed.getTransactionHash());

        Transaction legacy = store(owner, parsed.getTransactionHash(), target.getName(), FlowType.EXPENSE);
        legacy.setContactName(parsed.getMerchantName()); legacy.setChannel(parsed.getChannel());
        legacy.setAmount(parsed.getAmount()); legacy.setTransactionDate(parsed.getTransactionDate());
        legacy.setCardLast4("1111");
        Transaction foreign = store(other, parsed.getTransactionHash(), null, FlowType.EXPENSE);
        foreign.setContactName(parsed.getMerchantName()); foreign.setChannel(parsed.getChannel());
        Transaction purchase = store(owner, "card-purchase", target.getName(), FlowType.EXPENSE);
        purchase.setContactName("School example"); purchase.setAmount(new BigDecimal("45.50"));
        purchase.setChannel("TARJETA_CREDITO_BCP");
        transactions.saveAllAndFlush(List.of(legacy, foreign, purchase));
        assertEquals(legacy.getId(), service.processAndSave(replay).getId());
        assertEquals(new BigDecimal("126.75"), analytics.getPeriodAnalytics(2026, 1).getMonthlyExpense());
        assertEquals(404, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.markOwnCardPayment(foreign.getId())).getStatusCode().value());
        var repaired = service.markOwnCardPayment(legacy.getId());
        assertEquals(FlowType.INTERNAL_TRANSFER, repaired.getFlowType());
        assertEquals(OperationType.OWN_CARD_PAYMENT, repaired.getOperationType());
        assertEquals(parsed.getTransactionHash(), repaired.getTransactionHash());
        assertEquals(parsed.getAmount(), repaired.getAmount());
        assertEquals(parsed.getTransactionDate(), repaired.getTransactionDate());
        assertEquals("1111", repaired.getCardLast4());
        assertEquals(target.getName(), repaired.getCategory());
        assertEquals(repaired.getId(), service.markOwnCardPayment(legacy.getId()).getId());
        assertEquals(FlowType.INTERNAL_TRANSFER, service.processAndSave(replay).getFlowType());
        assertEquals(FlowType.EXPENSE, transactions.findById(foreign.getId()).orElseThrow().getFlowType());
        assertEquals(3, transactions.count());
        var result = analytics.getPeriodAnalytics(2026, 1);
        assertEquals(new BigDecimal("45.50"), result.getMonthlyExpense());
        assertEquals(0, result.getMonthlyIncome().signum());
        assertEquals(new BigDecimal("81.25"), result.getInternalTransfersAmount());
        assertEquals(1, result.getTopMerchants().size());
        assertEquals("School example", result.getTopMerchants().getFirst().getMerchantName());
        assertEquals(new BigDecimal("45.50"), result.getCategoryBreakdown().getFirst().getAmount());
        assertEquals(new BigDecimal("45.50"), result.getDailyExpenses().getFirst().getCumulativeAmount());
        assertEquals(new BigDecimal("45.50"), result.getLargestExpense().getAmount());
        assertEquals(new BigDecimal("45.50"), result.getPeakExpenseDay().getAmount());
        var summary = analytics.getSummary();
        assertEquals(new BigDecimal("45.50"), summary.getTotalExpense());
        assertEquals(new BigDecimal("-45.50"), summary.getNetBalance());
        var csv = new String(service.exportTransactionsCsv(null, null, FlowType.EXPENSE, null,
                null, null, null, null), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("School example"));
        assertFalse(csv.contains("Pago Tarjeta"));
    }

    @Test void newOwnCardPaymentIsStoredOnceAsATransferAndNeverBecomesAnExpense() {
        var parsed = new com.store.api.service.email.parser.BcpEmailParser().parse(
                "Constancia de pago de tarjeta propia", "Monto: S/ 81.25", LocalDateTime.of(2026, 1, 1, 12, 0));
        TransactionSyncRequest request = new TransactionSyncRequest();
        request.setAmount(parsed.getAmount()); request.setFlowType(parsed.getFlowType());
        request.setOperationType(parsed.getOperationType());
        request.setContactName(parsed.getMerchantName()); request.setChannel(parsed.getChannel());
        request.setTransactionDate(parsed.getTransactionDate()); request.setTransactionHash(parsed.getTransactionHash());
        var saved = service.processAndSave(request);
        assertEquals(FlowType.INTERNAL_TRANSFER, saved.getFlowType());
        assertEquals(OperationType.OWN_CARD_PAYMENT, saved.getOperationType());
        assertEquals(saved.getId(), service.processAndSave(request).getId());
        assertEquals(1, transactions.count());
        assertEquals(0, analytics.getSummary().getTotalExpense().signum());
        assertEquals(0, analytics.getSummary().getTotalIncome().signum());
        assertEquals(0, analytics.getSummary().getNetBalance().signum());
        assertTrue(analytics.getPeriodAnalytics(2026, 1).getTopMerchants().isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"EXAMPLE_BANK", "EXAMPLE_WALLET", "UNKNOWN"})
    void explicitOwnCardConfirmationDoesNotDependOnProviderOrContactText(String channel) {
        var tx = store(owner, "provider-independent", target.getName(), FlowType.EXPENSE);
        tx.setChannel(channel); tx.setContactName("Reference only");
        transactions.saveAndFlush(tx);
        assertEquals(OperationType.UNKNOWN, tx.getOperationType());
        var confirmed = service.markOwnCardPayment(tx.getId());
        assertEquals(OperationType.OWN_CARD_PAYMENT, confirmed.getOperationType());
        assertEquals(FlowType.INTERNAL_TRANSFER, confirmed.getFlowType());
        assertEquals(channel, confirmed.getChannel());
        assertEquals("Reference only", confirmed.getContactName());
        assertEquals(new BigDecimal("10.25"), confirmed.getAmount());
        assertEquals(tx.getTransactionHash(), confirmed.getTransactionHash());
        assertEquals(confirmed.getId(), service.markOwnCardPayment(tx.getId()).getId());
        assertEquals(0, analytics.getSummary().getNetBalance().signum());
        assertEquals(0, analytics.getSummary().getTotalExpense().signum());
    }

    @Test void expenseHighlightsUseOwnedPeriodExpensesAndDistinguishSingleExpenseFromDailyTotal() {
        Transaction first = store(owner, "highlight-first", null, FlowType.EXPENSE);
        first.setContactName("Largest owned");first.setAmount(new BigDecimal("80.00"));
        first.setTransactionDate(LocalDateTime.of(2026, 1, 2, 12, 0));
        Transaction tied = store(owner, "highlight-tied", null, FlowType.EXPENSE);
        tied.setAmount(new BigDecimal("80.00"));tied.setTransactionDate(LocalDateTime.of(2026, 1, 3, 12, 0));
        Transaction dayA = store(owner, "highlight-day-a", null, FlowType.EXPENSE);dayA.setAmount(new BigDecimal("50.25"));
        Transaction dayB = store(owner, "highlight-day-b", null, FlowType.EXPENSE);dayB.setAmount(new BigDecimal("49.75"));
        Transaction foreign = store(other, "highlight-foreign", null, FlowType.EXPENSE);foreign.setAmount(new BigDecimal("9999.00"));
        Transaction income = store(owner, "highlight-income", null, FlowType.INCOME);income.setAmount(new BigDecimal("9000.00"));
        Transaction transfer = store(owner, "highlight-transfer", null, FlowType.INTERNAL_TRANSFER);transfer.setAmount(new BigDecimal("8000.00"));
        Transaction old = store(owner, "highlight-old", null, FlowType.EXPENSE);old.setAmount(new BigDecimal("7000.00"));
        old.setTransactionDate(LocalDateTime.of(2025, 12, 31, 12, 0));
        transactions.saveAllAndFlush(List.of(first, tied, dayA, dayB, foreign, income, transfer, old));
        var result = analytics.getPeriodAnalytics(2026, 1);
        assertEquals(new BigDecimal("260.00"), result.getMonthlyExpense());
        assertEquals("Largest owned", result.getLargestExpense().getMerchantName());
        assertEquals(new BigDecimal("80.00"), result.getLargestExpense().getAmount());
        assertEquals(LocalDateTime.of(2026, 1, 2, 12, 0), result.getLargestExpense().getTransactionDate());
        assertEquals(java.time.LocalDate.of(2026, 1, 1), result.getPeakExpenseDay().getDate());
        assertEquals(new BigDecimal("100.00"), result.getPeakExpenseDay().getAmount());
        var empty = analytics.getPeriodAnalytics(2024, 1);
        assertNull(empty.getLargestExpense());assertNull(empty.getPeakExpenseDay());
    }

    @Test void categoryDrillDownMatchesTrimmedGroupAndUncategorizedWithoutMixingScopes() {
        store(owner, "drill-trimmed", " Ocio ", FlowType.EXPENSE);
        store(owner, "drill-exact", "Ocio", FlowType.EXPENSE);
        store(owner, "drill-case", "ocio", FlowType.EXPENSE);
        store(owner, "drill-blank", "  ", FlowType.EXPENSE);
        store(owner, "drill-null", null, FlowType.EXPENSE);
        store(owner, "drill-income", "Ocio", FlowType.INCOME);
        store(owner, "drill-transfer", null, FlowType.INTERNAL_TRANSFER);
        store(other, "drill-foreign", "Ocio", FlowType.EXPENSE);
        Transaction old = store(owner, "drill-old", "Ocio", FlowType.EXPENSE);
        old.setTransactionDate(LocalDateTime.of(2025, 12, 31, 23, 59));transactions.saveAndFlush(old);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0), end = LocalDateTime.of(2026, 1, 1, 23, 59, 59);
        for (String group : List.of("Ocio", "")) {
            var page = service.getTransactions(start, end, FlowType.EXPENSE, null, null, null,
                    null, null, null, null, group, PageRequest.of(1, 1));
            assertEquals(2, page.getTotalElements());assertEquals(1, page.getContent().size());
            String csv = new String(service.exportTransactionsCsv(start, end, FlowType.EXPENSE, null,
                    null, null, null, null, null, null, group), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(3, csv.lines().count());assertFalse(csv.contains("drill-income"));
            assertFalse(csv.contains("drill-transfer"));assertFalse(csv.contains("drill-foreign"));
            assertFalse(csv.contains("drill-case"));assertFalse(csv.contains("drill-old"));
        }
    }

    @Test void exactMerchantFilterKeepsAnalysisScopeAcrossPagingAndCsv() {
        String name = "Literal %_ Shop";
        for (int index = 0; index < 3; index++) {
            Transaction mine = store(owner, "merchant-exact-" + index, null, FlowType.EXPENSE);
            mine.setContactName(name);
            transactions.saveAndFlush(mine);
        }
        Transaction similar = store(owner, "merchant-similar", null, FlowType.EXPENSE);
        similar.setContactName(name + " Extra");
        Transaction foreign = store(other, "merchant-foreign", null, FlowType.EXPENSE);
        foreign.setContactName(name);
        Transaction income = store(owner, "merchant-income", null, FlowType.INCOME);
        income.setContactName(name);
        Transaction future = store(owner, "merchant-future", null, FlowType.EXPENSE);
        future.setContactName(name);
        future.setTransactionDate(LocalDateTime.of(2026, 1, 2, 0, 0));
        transactions.saveAllAndFlush(List.of(similar, foreign, income, future));
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 23, 59, 59);
        var page = service.getTransactions(start, end, FlowType.EXPENSE, null, null, null,
                null, null, null, name, PageRequest.of(1, 2));
        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(name, page.getContent().getFirst().getContactName());
        String csv = new String(service.exportTransactionsCsv(start, end, FlowType.EXPENSE, null,
                null, null, null, null, null, name), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(4, csv.lines().count());
        assertFalse(csv.contains("merchant-similar"));
        assertFalse(csv.contains("merchant-foreign"));
        assertFalse(csv.contains("merchant-income"));
        assertFalse(csv.contains("merchant-future"));
    }

    @Test void categoryBreakdownIncludesUnclassifiedExpensesAndRespectsOwnerPeriodAndFlow() {
        Transaction first = store(owner, "category-food", " Comida ", FlowType.EXPENSE);
        first.setAmount(new BigDecimal("60.25"));
        Transaction second = store(owner, "category-food-two", "Comida", FlowType.EXPENSE);
        second.setAmount(new BigDecimal("19.75"));
        Transaction blank = store(owner, "category-blank", "  ", FlowType.EXPENSE);
        blank.setAmount(new BigDecimal("15.00"));
        Transaction missing = store(owner, "category-null", null, FlowType.EXPENSE);
        missing.setAmount(new BigDecimal("5.00"));
        transactions.saveAllAndFlush(List.of(first, second, blank, missing));
        store(other, "category-foreign", "Foreign", FlowType.EXPENSE);
        store(owner, "category-income", "Income", FlowType.INCOME);
        store(owner, "category-transfer", "Transfer", FlowType.INTERNAL_TRANSFER);
        Transaction old = store(owner, "category-old", "Outside", FlowType.EXPENSE);
        old.setTransactionDate(LocalDateTime.of(2025, 12, 31, 23, 59));
        transactions.saveAndFlush(old);
        var result = analytics.getPeriodAnalytics(2026, 1);
        assertEquals(new BigDecimal("100.00"), result.getMonthlyExpense());
        var groups = result.getCategoryBreakdown();
        assertEquals(2, groups.size());
        assertEquals("Comida", groups.getFirst().getCategory());
        assertEquals(new BigDecimal("80.00"), groups.getFirst().getAmount());
        assertEquals(80.0, groups.getFirst().getPercentage());
        assertEquals(2L, groups.getFirst().getCount());
        assertNull(groups.getLast().getCategory());
        assertEquals(new BigDecimal("20.00"), groups.getLast().getAmount());
        assertEquals(20.0, groups.getLast().getPercentage());
        assertTrue(analytics.getPeriodAnalytics(2024, 1).getCategoryBreakdown().isEmpty());
    }

    @Test void merchantRankingSelectsHighestSpendBeforeLimitingAndScopesExpensesToUserAndPeriod() {
        storeMerchant(owner, "Frequent", "1.00", 7);
        storeMerchant(owner, "Largest", "700.00", 1);
        storeMerchant(owner, "Second", "200.00", 1);
        storeMerchant(owner, "Tie B", "50.00", 1);
        storeMerchant(owner, "Tie A", "50.00", 1);
        storeMerchant(owner, "Fifth", "20.00", 1);
        storeMerchant(owner, "Outside top five", "10.00", 1);
        storeMerchant(other, "Foreign", "10000.00", 1);
        Transaction transfer = store(owner, "ranking-transfer", null, FlowType.INTERNAL_TRANSFER);
        transfer.setAmount(new BigDecimal("9000.00"));
        Transaction income = store(owner, "ranking-income", null, FlowType.INCOME);
        income.setAmount(new BigDecimal("8000.00"));
        Transaction old = store(owner, "ranking-old", null, FlowType.EXPENSE);
        old.setTransactionDate(LocalDateTime.of(2025, 12, 31, 12, 0));
        old.setAmount(new BigDecimal("7000.00"));
        transactions.saveAllAndFlush(List.of(transfer, income, old));

        var result = analytics.getPeriodAnalytics(2026, 1);
        assertEquals(new BigDecimal("1037.00"), result.getMonthlyExpense());
        assertEquals(List.of("Largest", "Second", "Tie A", "Tie B", "Fifth"),
                result.getTopMerchants().stream().map(item -> item.getMerchantName()).toList());
        assertEquals(List.of(new BigDecimal("700.00"), new BigDecimal("200.00"),
                        new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("20.00")),
                result.getTopMerchants().stream().map(item -> item.getTotalAmount()).toList());
        assertEquals(1L, result.getTopMerchants().getFirst().getTransactionCount());
    }

    private void storeMerchant(User user, String name, String amount, int count) {
        for (int index = 0; index < count; index++) {
            Transaction movement = store(user, "ranking-" + name + "-" + index, null, FlowType.EXPENSE);
            movement.setContactName(name);
            movement.setAmount(new BigDecimal(amount));
            transactions.saveAndFlush(movement);
        }
    }

    @Test void listingSearchAndCsvWorkWithoutMetadataColumnsAndRemainUserScoped() {
        Transaction mine = store(owner, "search-owner", "Ocio", FlowType.EXPENSE);
        Transaction foreign = store(other, "search-other", "Ocio", FlowType.EXPENSE);
        foreign.setContactName("Foreign merchant");
        transactions.saveAndFlush(foreign);
        var page = service.getTransactions(null, null, null, "ocio", PageRequest.of(0, 10));
        assertEquals(List.of(mine.getId()), page.getContent().stream().map(item -> item.getId()).toList());
        assertEquals(1, service.getTransactions(null, null, null, "merchant", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, service.getTransactions(null, null, null, "no match", PageRequest.of(0, 10)).getTotalElements());
        String csv = new String(service.exportTransactionsCsv(null, null, null, "ocio", null, null, null, null),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Medio,Tarjeta,Hash\n"));
        assertTrue(csv.contains("\"10.25\""));
        assertTrue(csv.contains("\"search-owner\""));
        assertFalse(csv.contains("search-other"));
        assertEquals(2, csv.lines().count());
    }

    @Test void inboxCombinesMissingFieldsScopesUserAndProtectsTransfers() {
        Transaction categoryOnly = store(owner, "category", "  ", FlowType.EXPENSE);
        store(owner, "complete-expense", "Ocio", FlowType.EXPENSE);
        Transaction both = store(owner, "both", null, FlowType.INCOME);
        store(owner, "transfer-complete", null, FlowType.INTERNAL_TRANSFER);
        store(owner, "transfer-missing", null, FlowType.INTERNAL_TRANSFER);
        store(owner, "complete", "Ocio", FlowType.INCOME);
        Transaction foreign = store(other, "foreign", null, FlowType.EXPENSE);
        assertEquals(List.of(categoryOnly.getId(), both.getId()), pending(PendingReason.ALL));
        assertEquals(List.of(categoryOnly.getId(), both.getId()), pending(PendingReason.CATEGORY));
        service.updateClassification(categoryOnly.getId(), TransactionClassificationRequest.builder().category("Ocio").build());
        assertEquals(List.of(both.getId()), pending(PendingReason.CATEGORY));
        UserContext.setCurrentUser(other);
        assertEquals(List.of(foreign.getId()), pending(PendingReason.ALL));
    }

    @Test void ingestionUsesPriorityThenOldestRuleAndRetriesPreserveManualClassification() {
        Category broad = categories.saveAndFlush(Category.builder().user(owner).name("Compras").build());
        rules.create(ClassificationRuleRequest.builder().matchText("CINE").categoryId(broad.getId()).priority(100).enabled(true).build());
        var first = rules.create(ClassificationRuleRequest.builder().matchText("cinemark").categoryId(target.getId()).priority(1).enabled(true).build());
        rules.create(ClassificationRuleRequest.builder().matchText("CINE").categoryId(broad.getId()).priority(1).enabled(true).build());
        assertEquals(first.getId(), rules.list().getFirst().getId());
        TransactionSyncRequest request = new TransactionSyncRequest();
        request.setContactName("Pago CINEMARK"); request.setAmount(new BigDecimal("25.50"));
        request.setFlowType(FlowType.EXPENSE); request.setTransactionHash("captured");
        request.setTransactionDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        var saved = service.processAndSave(request);
        assertEquals("Ocio", saved.getCategory());
        service.updateClassification(saved.getId(), TransactionClassificationRequest.builder().category("Compras").build());
        assertEquals("Compras", service.processAndSave(request).getCategory());
        assertEquals(1, transactions.count());
        assertEquals(new BigDecimal("25.50"), service.processAndSave(request).getAmount());
        target.setActive(false); categories.saveAndFlush(target);
        request.setTransactionHash("next");
        assertEquals("Compras", service.processAndSave(request).getCategory());
        request.setTransactionHash("transfer"); request.setFlowType(FlowType.INTERNAL_TRANSFER);
        assertNull(service.processAndSave(request).getCategory());
        UserContext.setCurrentUser(other);
        assertTrue(rules.list().isEmpty());
    }

    @Test void referencedCategoryCannotBeDeletedAndRenamingKeepsRuleTarget() {
        rules.create(ClassificationRuleRequest.builder().matchText("CINE").categoryId(target.getId()).priority(10).enabled(true).build());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> categoryService.delete(target.getId()));
        target.setName("Tiempo libre"); categories.saveAndFlush(target);
        assertEquals("Tiempo libre", rules.list().getFirst().getCategoryName());
    }
}
