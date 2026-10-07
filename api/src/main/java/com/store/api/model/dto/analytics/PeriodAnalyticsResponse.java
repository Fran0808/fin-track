package com.store.api.model.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PeriodAnalyticsResponse {
    private String periodName;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyIncome;
    private BigDecimal internalTransfersAmount;
    private BigDecimal previousPeriodExpense;
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
}
