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
    @Autowired private FinancialInstrumentRepository instruments;
    @Autowired private ClassificationRuleService rules;
    @Autowired private CategoryService categoryService;
    @Autowired private TransactionService service;
    @Autowired private AnalyticsService analytics;
    private User owner;
    private User other;
    private Category target;
    private FinancialInstrument account;

    @BeforeEach void setup() {
        owner = users.saveAndFlush(User.builder().email("owner@example.test").build());
        other = users.saveAndFlush(User.builder().email("other@example.test").build());
        target = categories.saveAndFlush(Category.builder().user(owner).name("Ocio").active(true).build());
        account = instruments.saveAndFlush(FinancialInstrument.builder().user(owner).alias("Test account")
                .bank(Bank.BCP).type(InstrumentType.BANK_ACCOUNT).build());
        UserContext.setCurrentUser(owner);
    }

    @AfterEach void clear() { UserContext.clear(); }

    private Transaction store(User user, String hash, String category, FlowType flow, FinancialInstrument instrument) {
        return transactions.saveAndFlush(Transaction.builder().user(user).transactionHash(hash).contactName("Test merchant")
                .amount(new BigDecimal("10.25")).flowType(flow).category(category).financialInstrument(instrument)
                .transactionDate(LocalDateTime.of(2026, 1, 1, 12, 0)).build());
    }

    private List<Long> pending(PendingReason reason) {
        return service.getPendingTransactions(reason, PageRequest.of(0, 10, Sort.by("id"))).getContent()
                .stream().map(item -> item.getId()).toList();
    }

    @Test void categoryBreakdownIncludesUnclassifiedExpensesAndRespectsOwnerPeriodAndFlow() {
        Transaction first = store(owner, "category-food", " Comida ", FlowType.EXPENSE, null);
        first.setAmount(new BigDecimal("60.25"));
        Transaction second = store(owner, "category-food-two", "Comida", FlowType.EXPENSE, null);
        second.setAmount(new BigDecimal("19.75"));
        Transaction blank = store(owner, "category-blank", "  ", FlowType.EXPENSE, null);
        blank.setAmount(new BigDecimal("15.00"));
        Transaction missing = store(owner, "category-null", null, FlowType.EXPENSE, null);
        missing.setAmount(new BigDecimal("5.00"));
        transactions.saveAllAndFlush(List.of(first, second, blank, missing));
        store(other, "category-foreign", "Foreign", FlowType.EXPENSE, null);
        store(owner, "category-income", "Income", FlowType.INCOME, null);
        store(owner, "category-transfer", "Transfer", FlowType.INTERNAL_TRANSFER, null);
        Transaction old = store(owner, "category-old", "Outside", FlowType.EXPENSE, null);
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
        Transaction transfer = store(owner, "ranking-transfer", null, FlowType.INTERNAL_TRANSFER, account);
        transfer.setAmount(new BigDecimal("9000.00"));
        Transaction income = store(owner, "ranking-income", null, FlowType.INCOME, account);
        income.setAmount(new BigDecimal("8000.00"));
        Transaction old = store(owner, "ranking-old", null, FlowType.EXPENSE, account);
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
            Transaction movement = store(user, "ranking-" + name + "-" + index, null, FlowType.EXPENSE, null);
            movement.setContactName(name);
            movement.setAmount(new BigDecimal(amount));
            transactions.saveAndFlush(movement);
        }
    }

    @Test void listingSearchAndCsvWorkWithoutMetadataColumnsAndRemainUserScoped() {
        Transaction mine = store(owner, "search-owner", "Ocio", FlowType.EXPENSE, account);
        Transaction foreign = store(other, "search-other", "Ocio", FlowType.EXPENSE, null);
        foreign.setContactName("Foreign merchant");
        transactions.saveAndFlush(foreign);
        var page = service.getTransactions(null, null, null, "ocio", PageRequest.of(0, 10));
        assertEquals(List.of(mine.getId()), page.getContent().stream().map(item -> item.getId()).toList());
        assertEquals(1, service.getTransactions(null, null, null, "merchant", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, service.getTransactions(null, null, null, "no match", PageRequest.of(0, 10)).getTotalElements());
        String csv = new String(service.exportTransactionsCsv(null, null, null, "ocio", null, null, null, null, null),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Medio,Tarjeta,Hash\n"));
        assertTrue(csv.contains("\"10.25\""));
        assertTrue(csv.contains("\"search-owner\""));
        assertFalse(csv.contains("search-other"));
        assertEquals(2, csv.lines().count());
    }

    @Test void inboxCombinesMissingFieldsScopesUserAndProtectsTransfers() {
        Transaction categoryOnly = store(owner, "category", "  ", FlowType.EXPENSE, account);
        Transaction instrumentOnly = store(owner, "instrument", "Ocio", FlowType.EXPENSE, null);
        Transaction both = store(owner, "both", null, FlowType.INCOME, null);
        store(owner, "transfer-complete", null, FlowType.INTERNAL_TRANSFER, account);
        Transaction transferMissing = store(owner, "transfer-missing", null, FlowType.INTERNAL_TRANSFER, null);
        store(owner, "complete", "Ocio", FlowType.INCOME, account);
        Transaction foreign = store(other, "foreign", null, FlowType.EXPENSE, null);
        assertEquals(List.of(categoryOnly.getId(), instrumentOnly.getId(), both.getId(), transferMissing.getId()), pending(PendingReason.ALL));
        assertEquals(List.of(categoryOnly.getId(), both.getId()), pending(PendingReason.CATEGORY));
        assertEquals(List.of(instrumentOnly.getId(), both.getId(), transferMissing.getId()), pending(PendingReason.INSTRUMENT));
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
