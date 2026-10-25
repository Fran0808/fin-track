package com.store.api.model.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PeriodAnalyticsResponse {
    private String periodName;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyIncome;
    private BigDecimal internalTransfersAmount;
    private BigDecimal previousPeriodExpense;
    private LocalDateTime previousPeriodStart;
    private LocalDateTime previousPeriodEnd;
    private BigDecimal expenseDifference;
    private LargestExpenseDto largestExpense;
    private PeakExpenseDayDto peakExpenseDay;
    private Integer comparisonThroughDay;
    private List<DailyExpenseDto> dailyExpenses;
    private long totalMovements;

    private String lastExpenseMerchant;
    private BigDecimal lastExpenseAmount;
    private LocalDateTime lastExpenseDate;

    private String topChannel;
    private BigDecimal topChannelAmount;
    private double topChannelPercentage;

    private List<ChannelBreakdownDto> channelBreakdown;
    private List<CategoryBreakdownDto> categoryBreakdown;

    private List<TopMerchantDto> topMerchants;

    @Data
    @Builder
    public static class DailyExpenseDto {
        private int day;
        private BigDecimal amount;
        private BigDecimal cumulativeAmount;
    }

    @Data
    @Builder
    public static class ChannelBreakdownDto {
        private String channel;
        private String cardLast4;
        private String displayName;
        private BigDecimal amount;
        private double percentage;
        private long count;
    }

    @Data
    @Builder
    public static class TopMerchantDto {
        private String merchantName;
        private BigDecimal totalAmount;
        private long transactionCount;
        private double percentage;
    }

    @Data
    @Builder
    public static class CategoryBreakdownDto {
        private String category;
        private BigDecimal amount;
        private double percentage;
        private long count;
    }

    @Data
    @Builder
    public static class LargestExpenseDto {
        private String merchantName;
        private BigDecimal amount;
        private LocalDateTime transactionDate;
    }

    @Data
    @Builder
    public static class PeakExpenseDayDto {
        private LocalDate date;
        private BigDecimal amount;
    }
}
