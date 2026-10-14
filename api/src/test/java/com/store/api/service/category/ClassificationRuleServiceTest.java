package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.ClassificationRuleRequest;
import com.store.api.model.entity.*;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassificationRuleServiceTest {
    private final ClassificationRuleRepository rules = mock(ClassificationRuleRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ClassificationRuleService service = new ClassificationRuleService(rules, categories);
    private final User owner = User.builder().id(1L).build();
    private final Category category = Category.builder().id(10L).user(owner).name("Entretenimiento").active(true).build();

    @AfterEach void clear() { UserContext.clear(); }

    private ClassificationRuleRequest request() {
        return ClassificationRuleRequest.builder().matchText(" Cine ").categoryId(10L).priority(100).enabled(true).build();
    }

    private ClassificationRule rule(String match, Category target) {
        return ClassificationRule.builder().id(2L).user(owner).matchText(match).category(target).priority(100).enabled(true).build();
    }

    private Transaction transaction() {
        UserContext.setCurrentUser(owner);
        return Transaction.builder().user(owner).contactName("Pago CINEMARK Centro")
                .flowType(FlowType.EXPENSE).amount(new BigDecimal("25.50")).transactionHash("original").build();
    }

    @Test void requiresAuthenticationForAllRuleOperations() {
        assertEquals(401, assertThrows(ResponseStatusException.class, service::list).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.create(request()));
        assertThrows(ResponseStatusException.class, () -> service.update(2L, request()));
        assertThrows(ResponseStatusException.class, () -> service.delete(2L));
        assertThrows(ResponseStatusException.class, () -> service.classifyNewTransaction(Transaction.builder().build()));
        verifyNoInteractions(rules, categories);
    }

    @Test void createsRuleOnlyWithOwnedActiveCategoryAndTrimsText() {
        UserContext.setCurrentUser(owner);
        when(categories.findByIdAndUser(10L, owner)).thenReturn(Optional.of(category));
        when(rules.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals("Cine", service.create(request()).getMatchText());
        verify(rules).save(argThat(rule -> rule.getUser() == owner && rule.getCategory() == category));
        category.setActive(false);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(request())).getStatusCode().value());
    }

    @Test void rejectsForeignCategoryAndForeignRuleIds() {
        UserContext.setCurrentUser(owner);
        when(categories.findByIdAndUser(10L, owner)).thenReturn(Optional.empty());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(request())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.update(8L, request())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.delete(8L)).getStatusCode().value());
        verify(rules, never()).save(any());
        verify(rules, never()).delete(any());
    }

    @Test void listsAndDeletesOnlyRulesOfTheCurrentUser() {
        UserContext.setCurrentUser(owner);
        ClassificationRule item = rule("cine", category);
        when(rules.findByUserOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(item));
        when(rules.findByIdAndUser(2L, owner)).thenReturn(Optional.of(item));
        assertEquals(1, service.list().size());
        service.delete(2L);
        verify(rules).delete(item);
        User other = User.builder().id(9L).build();
        UserContext.setCurrentUser(other);
        when(rules.findByUserOrderByPriorityAscIdAsc(other)).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
    }

    @Test void matchesCaseInsensitivelyAndUsesFirstEligibleRuleWithoutChangingMoneyOrIdentity() {
        Transaction tx = transaction();
        Category inactive = Category.builder().id(11L).user(owner).name("Inactiva").active(false).build();
        Category foreign = Category.builder().id(12L).user(User.builder().id(9L).build()).active(true).build();
        when(rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(
                rule("cine", inactive), rule("cine", foreign), rule("CineMark", category), rule("pago", inactive)));
        service.classifyNewTransaction(tx);
        assertEquals("Entretenimiento", tx.getCategory());
        assertEquals(new BigDecimal("25.50"), tx.getAmount());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("original", tx.getTransactionHash());
    }

    @Test void doesNotOverwriteClassificationOrClassifyTransfers() {
        Transaction tx = transaction();
        tx.setCategory("Manual"); service.classifyNewTransaction(tx);
        assertEquals("Manual", tx.getCategory());
        tx.setCategory(null); tx.setFlowType(FlowType.INTERNAL_TRANSFER); service.classifyNewTransaction(tx);
        assertNull(tx.getCategory());
        verifyNoInteractions(rules);
    }

    @Test void treatsWildcardsAsLiteralTextAndAllowsIncome() {
        Transaction tx = transaction(); tx.setFlowType(FlowType.INCOME);
        when(rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(rule("%", category)));
        service.classifyNewTransaction(tx); assertNull(tx.getCategory());
        when(rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(owner)).thenReturn(List.of(rule("cine", category)));
        service.classifyNewTransaction(tx); assertEquals(category.getName(), tx.getCategory());
    }

    @Test void rejectsApplyingRulesToAnotherUsersTransaction() {
        Transaction tx = transaction(); tx.setUser(User.builder().id(9L).build());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.classifyNewTransaction(tx)).getStatusCode().value());
        verifyNoInteractions(rules);
    }

    @Test void canPauseExistingRuleWithInactiveCategoryButCannotEnableIt() {
        UserContext.setCurrentUser(owner); category.setActive(false);
        when(rules.findByIdAndUser(2L, owner)).thenReturn(Optional.of(rule("cine", category)));
        when(categories.findByIdAndUser(10L, owner)).thenReturn(Optional.of(category));
        when(rules.save(any())).thenAnswer(call -> call.getArgument(0));
        ClassificationRuleRequest request = request(); request.setEnabled(false);
        assertFalse(service.update(2L, request).isEnabled());
        request.setEnabled(true);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update(2L, request)).getStatusCode().value());
    }
}
