package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.*;
import com.store.api.model.entity.*;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleHistoryServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final ClassificationRuleRepository rules = mock(ClassificationRuleRepository.class);
    private final RuleHistoryService service = new RuleHistoryService(transactions, rules, "synthetic-history-test-secret-32-bytes");
    private final User owner = User.builder().id(1L).build();
    private final Category category = Category.builder().id(10L).user(owner).name("Groceries").active(true).build();
    private final ClassificationRule rule = ClassificationRule.builder().id(7L).user(owner).matchText("shop%_")
            .category(category).enabled(true).priority(100).build();

    @BeforeEach void setup() {
        UserContext.setCurrentUser(owner);
        when(rules.findByIdAndUser(7L, owner)).thenReturn(Optional.of(rule));
        when(rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(rule));
    }
    @AfterEach void clear() { UserContext.clear(); }

    private Transaction row(long id) {
        return Transaction.builder().id(id).user(owner).contactName("SHOP%_ market").amount(new BigDecimal("10.25"))
                .flowType(FlowType.EXPENSE).transactionDate(LocalDateTime.of(2026,1,1,12,0)).transactionHash("synthetic-"+id).build();
    }
    private RuleHistoryPreview preview(List<Transaction> rows) {
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(rows));
        return service.preview(7L);
    }
    private RuleHistoryApplyRequest request(RuleHistoryPreview preview) {
        return new RuleHistoryApplyRequest(preview.getTransactions().stream().map(t -> t.getId()).toList(),
                preview.getExpiresAt(), preview.getPreviewToken());
    }

    @Test void requiresAuthenticationForPreviewAndApply() {
        UserContext.clear(); clearInvocations(rules);
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.preview(7L)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.apply(7L, new RuleHistoryApplyRequest())).getStatusCode().value());
        verifyNoInteractions(rules, transactions);
    }

    @Test void previewIsReadOnlyUsesLiteralMatchingAndRespectsWinningPriority() {
        var expense = row(1); var income = row(2); income.setFlowType(FlowType.INCOME);
        var different = row(3); different.setContactName("SHOPab market");
        var transfer = row(4); transfer.setFlowType(FlowType.INTERNAL_TRANSFER);
        var foreign = row(5); foreign.setUser(User.builder().id(2L).build());
        var preview = preview(List.of(expense, income, different, transfer, foreign));
        assertEquals(List.of(1L,2L), preview.getTransactions().stream().map(t -> t.getId()).toList());
        assertNull(expense.getCategory()); assertNull(income.getCategory());
        assertEquals(64, preview.getPreviewToken().length());
        var prior = ClassificationRule.builder().id(6L).user(owner).category(category).matchText("market").enabled(true).build();
        when(rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(prior,rule));
        assertTrue(service.preview(7L).getTransactions().isEmpty());
        verify(transactions, never()).saveAll(any());
    }

    @Test void appliesOnlyReviewedRowsPreservesManualCategoriesAndIsIdempotentOnRetry() {
        var first = row(1); var manual = row(2);
        var request = request(preview(List.of(first,manual)));
        manual.setCategory("Manual");
        when(transactions.lockHistorySelection(owner,List.of(1L,2L))).thenReturn(List.of(first,manual));
        var result = service.apply(7L,request);
        assertEquals(1,result.getAppliedCount()); assertEquals(1,result.getSkippedCount());
        assertEquals("Groceries",first.getCategory()); assertEquals("Manual",manual.getCategory());
        assertEquals(new BigDecimal("10.25"),first.getAmount()); assertEquals("synthetic-1",first.getTransactionHash());
        var replay = service.apply(7L,request);
        assertEquals(0,replay.getAppliedCount()); assertEquals(2,replay.getSkippedCount());
    }

    @Test void rejectsAlteredSelectionAndDoesNotExposeForeignRecords() {
        var first = row(1); var extra = row(2);
        var request = request(preview(List.of(first)));
        request.setTransactionIds(List.of(1L,2L));
        when(transactions.lockHistorySelection(owner,List.of(1L,2L))).thenReturn(List.of(first,extra));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
        assertNull(first.getCategory()); assertNull(extra.getCategory());
        when(transactions.lockHistorySelection(owner,List.of(1L,2L))).thenReturn(List.of(first));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
        request.setTransactionIds(List.of(1L,1L));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
    }

    @Test void invalidatesExpiredPreviewsAndRuleOrMovementChanges() {
        var first = row(1); var request = request(preview(List.of(first)));
        when(transactions.lockHistorySelection(owner,List.of(1L))).thenReturn(List.of(first));
        category.setName("Changed");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
        category.setName("Groceries"); first.setAmount(new BigDecimal("12.34"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
        first.setAmount(new BigDecimal("10.25")); request.setExpiresAt(1L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.apply(7L,request)).getStatusCode().value());
        assertNull(first.getCategory());
    }

    @Test void rejectsPausedForeignAndInactiveRules() {
        rule.setEnabled(false);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.preview(7L)).getStatusCode().value());
        rule.setEnabled(true); category.setActive(false);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.preview(7L)).getStatusCode().value());
        when(rules.findByIdAndUser(7L,owner)).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.preview(7L)).getStatusCode().value());
        verifyNoInteractions(transactions);
    }

    @Test void boundsTheExactReviewedBatchAndReportsFurtherMatches() {
        var rows = IntStream.rangeClosed(1,200).mapToObj(i->row(i)).toList();
        when(transactions.findAll(any(Specification.class),any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows,PageRequest.of(0,200),201))
                .thenReturn(new PageImpl<>(List.of(row(201)),PageRequest.of(1,200),201));
        var preview = service.preview(7L);
        assertTrue(preview.isHasMore()); assertEquals(200,preview.getTransactions().size());
        assertEquals(200,preview.getLimit());
        assertFalse(preview.getTransactions().stream().anyMatch(t->t.getId()==201L));
    }
}
