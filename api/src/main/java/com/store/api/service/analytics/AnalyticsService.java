package com.store.api.service.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.analytics.FinancialSummaryResponse;
import com.store.api.model.dto.analytics.PeriodAnalyticsResponse;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.ChannelType;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    @org.springframework.beans.factory.annotation.Value("${app.business-zone:America/Lima}")
    private java.time.ZoneId businessZone = java.time.ZoneId.of("America/Lima");

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public FinancialSummaryResponse getSummary() {
        User currentUser = UserContext.requireCurrentUser();
        Long userId = currentUser.getId();

        BigDecimal totalIncome = transactionRepository.sumAmountByFlowType(FlowType.INCOME, userId);
        BigDecimal totalExpense = transactionRepository.sumAmountByFlowType(FlowType.EXPENSE, userId);
        BigDecimal netBalance = totalIncome.subtract(totalExpense);
        long totalTransactions = transactionRepository.countByUserId(userId);

        return FinancialSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .totalTransactions(totalTransactions)
                .build();
    }

    @Transactional(readOnly = true)
    public PeriodAnalyticsResponse getPeriodAnalytics(Integer year, Integer month) {
        User currentUser = UserContext.requireCurrentUser();
        Long userId = currentUser.getId();

        LocalDate now = LocalDate.now(businessZone);
        int targetYear = (year != null && year > 2000) ? year : now.getYear();
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();

        YearMonth targetYearMonth = YearMonth.of(targetYear, targetMonth);
        LocalDateTime startOfMonth = targetYearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = targetYearMonth.atEndOfMonth().atTime(LocalTime.MAX);
        boolean isCurrentMonth = targetYearMonth.equals(YearMonth.from(now));
        boolean isFutureMonth = targetYearMonth.isAfter(YearMonth.from(now));
        int visibleDays = isFutureMonth ? 0 : isCurrentMonth ? now.getDayOfMonth() : targetYearMonth.lengthOfMonth();
        LocalDateTime visibleEnd = isFutureMonth
                ? startOfMonth.minusNanos(1)
                : isCurrentMonth ? now.atTime(LocalTime.MAX) : endOfMonth;

        YearMonth previousYearMonth = targetYearMonth.minusMonths(1);
        int comparisonDays = isCurrentMonth
                ? Math.min(visibleDays, previousYearMonth.lengthOfMonth())
                : previousYearMonth.lengthOfMonth();
        LocalDateTime previousPeriodStart = isFutureMonth ? null : previousYearMonth.atDay(1).atStartOfDay();
        LocalDateTime previousPeriodEnd = isFutureMonth ? null : previousYearMonth.atDay(comparisonDays).atTime(LocalTime.MAX);
        BigDecimal previousPeriodExpense = isFutureMonth ? null
                : transactionRepository.sumAmountByFlowTypeAndDateRange(
                        FlowType.EXPENSE,
                        previousPeriodStart,
                        previousPeriodEnd,
                        userId
                );

        BigDecimal monthlyExpense = transactionRepository.sumAmountByFlowTypeAndDateRange(FlowType.EXPENSE, startOfMonth, visibleEnd, userId);
        BigDecimal monthlyIncome = transactionRepository.sumAmountByFlowTypeAndDateRange(FlowType.INCOME, startOfMonth, visibleEnd, userId);
        BigDecimal internalTransfers = transactionRepository.sumAmountByFlowTypeAndDateRange(FlowType.INTERNAL_TRANSFER, startOfMonth, visibleEnd, userId);

        BigDecimal[] dailyAmounts = new BigDecimal[visibleDays];
        Arrays.fill(dailyAmounts, BigDecimal.ZERO);
        if (visibleDays > 0) {
            for (Object[] row : transactionRepository.findExpenseAmountsByDateRange(startOfMonth, visibleEnd, userId)) {
                int day = ((LocalDateTime) row[0]).getDayOfMonth();
                if (day <= visibleDays) {
                    dailyAmounts[day - 1] = dailyAmounts[day - 1].add((BigDecimal) row[1]);
                }
            }
        }
        List<PeriodAnalyticsResponse.DailyExpenseDto> dailyExpenses = new ArrayList<>();
        BigDecimal runningExpense = BigDecimal.ZERO;
        PeriodAnalyticsResponse.PeakExpenseDayDto peakExpenseDay = null;
        for (int day = 1; day <= visibleDays; day++) {
            runningExpense = runningExpense.add(dailyAmounts[day - 1]);
            if (dailyAmounts[day - 1].signum() > 0
                    && (peakExpenseDay == null || dailyAmounts[day - 1].compareTo(peakExpenseDay.getAmount()) > 0)) {
                peakExpenseDay = PeriodAnalyticsResponse.PeakExpenseDayDto.builder()
                        .date(targetYearMonth.atDay(day)).amount(dailyAmounts[day - 1]).build();
            }
            dailyExpenses.add(PeriodAnalyticsResponse.DailyExpenseDto.builder()
                    .day(day)
                    .amount(dailyAmounts[day - 1])
                    .cumulativeAmount(runningExpense)
                    .build());
        }

        long totalMovements = transactionRepository.countByUserId(userId);
        PeriodAnalyticsResponse.LargestExpenseDto largestExpense = isFutureMonth ? null : transactionRepository
                .findLargestExpense(startOfMonth, visibleEnd, userId).filter(item -> item.getAmount().signum() > 0)
                .map(item -> PeriodAnalyticsResponse.LargestExpenseDto.builder().merchantName(item.getContactName())
                        .amount(item.getAmount()).transactionDate(item.getTransactionDate()).build()).orElse(null);

        Optional<Transaction> latestOpt = transactionRepository.findLatestExpense(userId);
        String lastMerchant = latestOpt.map(Transaction::getContactName).orElse("Sin gastos");
        BigDecimal lastAmount = latestOpt.map(Transaction::getAmount).orElse(BigDecimal.ZERO);
        LocalDateTime lastDate = latestOpt.map(Transaction::getTransactionDate).orElse(null);

        List<Object[]> rawBreakdown = transactionRepository.findExpenseBreakdownByChannel(startOfMonth, visibleEnd, userId);
        List<PeriodAnalyticsResponse.ChannelBreakdownDto> breakdownList = new ArrayList<>();

        String topChannel = "N/A";
        BigDecimal topChannelAmount = BigDecimal.ZERO;
        double topChannelPercentage = 0.0;

        for (Object[] row : rawBreakdown) {
            String channel = (String) row[0];
            String cardLast4 = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            long count = ((Number) row[3]).longValue();

            double percentage = monthlyExpense.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(monthlyExpense, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;

            String displayName = ChannelType.formatDisplayName(channel, cardLast4);

            breakdownList.add(PeriodAnalyticsResponse.ChannelBreakdownDto.builder()
                    .channel(channel)
                    .cardLast4(cardLast4)
                    .displayName(displayName)
                    .amount(amount)
                    .percentage(Math.round(percentage * 10.0) / 10.0)
                    .count(count)
                    .build());
        }

        if (!breakdownList.isEmpty()) {
            PeriodAnalyticsResponse.ChannelBreakdownDto first = breakdownList.getFirst();
            topChannel = first.getDisplayName() != null ? first.getDisplayName() : first.getChannel();
            topChannelAmount = first.getAmount();
            topChannelPercentage = first.getPercentage();
        }

        List<PeriodAnalyticsResponse.CategoryBreakdownDto> categoryBreakdown = transactionRepository
                .findExpenseBreakdownByCategory(startOfMonth, visibleEnd, userId).stream().map(row -> {
                    BigDecimal amount = (BigDecimal) row[1];
                    double percentage = monthlyExpense.signum() > 0
                            ? amount.multiply(BigDecimal.valueOf(100)).divide(monthlyExpense, 1, RoundingMode.HALF_UP).doubleValue() : 0;
                    return PeriodAnalyticsResponse.CategoryBreakdownDto.builder().category((String) row[0])
                            .amount(amount).percentage(percentage).count(((Number) row[2]).longValue()).build();
                }).toList();

        List<Object[]> rawMerchants = transactionRepository.findTopMerchants(startOfMonth, visibleEnd, userId);
        List<PeriodAnalyticsResponse.TopMerchantDto> topMerchantsList = new ArrayList<>();

        for (Object[] row : rawMerchants) {
            String merchantName = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            double percentage = monthlyExpense.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(monthlyExpense, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;

            topMerchantsList.add(PeriodAnalyticsResponse.TopMerchantDto.builder()
                    .merchantName(merchantName)
                    .totalAmount(amount)
                    .transactionCount(count)
                    .percentage(Math.round(percentage * 10.0) / 10.0)
                    .build());
        }

        return PeriodAnalyticsResponse.builder()
                .periodName(targetYearMonth.getMonth().name())
                .periodStart(startOfMonth)
                .periodEnd(visibleEnd)
                .monthlyExpense(monthlyExpense)
                .monthlyIncome(monthlyIncome)
                .internalTransfersAmount(internalTransfers)
                .previousPeriodExpense(previousPeriodExpense)
                .previousPeriodStart(previousPeriodStart)
                .previousPeriodEnd(previousPeriodEnd)
                .expenseDifference(previousPeriodExpense == null ? null : monthlyExpense.subtract(previousPeriodExpense))
                .largestExpense(largestExpense)
                .peakExpenseDay(peakExpenseDay)
                .comparisonThroughDay(isCurrentMonth ? comparisonDays : null)
                .dailyExpenses(dailyExpenses)
                .totalMovements(totalMovements)
                .lastExpenseMerchant(lastMerchant)
                .lastExpenseAmount(lastAmount)
                .lastExpenseDate(lastDate)
                .topChannel(topChannel)
                .topChannelAmount(topChannelAmount)
                .topChannelPercentage(topChannelPercentage)
                .channelBreakdown(breakdownList)
                .topMerchants(topMerchantsList)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }
}
