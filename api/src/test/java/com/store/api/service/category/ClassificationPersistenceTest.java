package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.ClassificationRuleRequest;
import com.store.api.model.dto.transaction.TransactionClassificationRequest;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.*;
import com.store.api.model.enums.*;
import com.store.api.repository.*;
import com.store.api.service.transaction.TransactionService;
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
