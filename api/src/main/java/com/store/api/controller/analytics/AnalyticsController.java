package com.store.api.controller.analytics;

import com.store.api.model.dto.analytics.FinancialSummaryResponse;
import com.store.api.service.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryResponse> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/period")
    public ResponseEntity<com.store.api.model.dto.analytics.PeriodAnalyticsResponse> getPeriodAnalytics(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer year,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(analyticsService.getPeriodAnalytics(year, month));
    }
}