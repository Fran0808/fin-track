package com.store.api.service.analytics;

import com.store.api.model.dto.analytics.PeriodAnalyticsResponse;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.User;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    public void authenticate() {
        UserContext.setCurrentUser(User.builder().id(42L).build());
    }

    @AfterEach
    public void clearContext() {
        UserContext.clear();
    }

    @Test
    void anonymousAnalyticsAreRejectedBeforeRepositoryAccess() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> analyticsService.getSummary()).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> analyticsService.getPeriodAnalytics(2026, 9)).getStatusCode().value());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void currentMonthUsesDailyExpensesAndComparesThroughSameDay() {
        LocalDate today = LocalDate.now();
        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        int comparisonDay = Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth());

        when(transactionRepository.sumAmountByFlowTypeAndDateRange(any(), any(), any(), eq(42L)))
                .thenAnswer(invocation -> invocation.getArgument(0) == FlowType.EXPENSE
                        && ((LocalDateTime) invocation.getArgument(1)).getMonthValue() == previousMonth.getMonthValue()
                        ? new BigDecimal("7.00") : new BigDecimal("12.00"));
        when(transactionRepository.findExpenseAmountsByDateRange(any(), any(), eq(42L)))
                .thenReturn(List.of(
                        new Object[] { today.withDayOfMonth(1).atTime(9, 0), new BigDecimal("5.00") },
                        new Object[] { today.atTime(10, 0), new BigDecimal("7.00") }
                ));
        when(transactionRepository.findLatestExpense(eq(42L))).thenReturn(Optional.empty());
        when(transactionRepository.findExpenseBreakdownByChannel(any(), any(), eq(42L))).thenReturn(List.of());
        when(transactionRepository.findTopMerchants(any(), any(), eq(42L))).thenReturn(List.of());

        PeriodAnalyticsResponse result = analyticsService.getPeriodAnalytics(today.getYear(), today.getMonthValue());

        assertEquals(new BigDecimal("7.00"), result.getPreviousPeriodExpense());
        assertEquals(comparisonDay, result.getComparisonThroughDay());
        assertEquals(today.getDayOfMonth(), result.getDailyExpenses().size());
        assertEquals(new BigDecimal("12.00"), result.getDailyExpenses().getLast().getCumulativeAmount());
        verify(transactionRepository).sumAmountByFlowTypeAndDateRange(
                FlowType.EXPENSE,
                previousMonth.atDay(1).atStartOfDay(),
                previousMonth.atDay(comparisonDay).atTime(java.time.LocalTime.MAX),
                42L
        );
    }

    @Test
    void futureMonthHasNoVisibleExpenseOrComparison() {
        YearMonth future = YearMonth.now().plusMonths(1);
        when(transactionRepository.sumAmountByFlowTypeAndDateRange(any(), any(), any(), eq(42L)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findLatestExpense(eq(42L))).thenReturn(Optional.empty());
        when(transactionRepository.findExpenseBreakdownByChannel(any(), any(), eq(42L))).thenReturn(List.of());
        when(transactionRepository.findTopMerchants(any(), any(), eq(42L))).thenReturn(List.of());

        PeriodAnalyticsResponse result = analyticsService.getPeriodAnalytics(future.getYear(), future.getMonthValue());

        assertNull(result.getPreviousPeriodExpense());
        assertEquals(List.of(), result.getDailyExpenses());
        assertEquals(BigDecimal.ZERO, result.getMonthlyExpense());
    }
}
