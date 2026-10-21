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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private MockedStatic<Clock> clocks;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    public void authenticate() {
        UserContext.setCurrentUser(User.builder().id(42L).build());
        clocks = mockStatic(Clock.class, CALLS_REAL_METHODS);
        freezeAt("2026-10-20T03:25:39.113Z");
    }

    @AfterEach
    public void clearContext() {
        clocks.close();
        UserContext.clear();
    }

    private void freezeAt(String instant) {
        Clock fixed = Clock.fixed(Instant.parse(instant), BUSINESS_ZONE);
        clocks.when(() -> Clock.system(BUSINESS_ZONE)).thenReturn(fixed);
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

    @ParameterizedTest
    @CsvSource({
            "2026-10-20T03:25:39.113Z, 2026-10-19",
            "2026-03-01T02:00:00Z, 2026-02-28",
            "2024-03-31T15:00:00Z, 2024-03-31",
            "2027-01-01T05:00:00Z, 2027-01-01"
    })
    void currentMonthUsesDailyExpensesAndComparesThroughSameDay(String instant, String businessDate) {
        freezeAt(instant);
        LocalDate today = LocalDate.parse(businessDate);
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
        YearMonth future = YearMonth.from(TODAY).plusMonths(1);
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
