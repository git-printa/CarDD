package com.cardamage.api.controller;

import com.cardamage.api.model.analytics.AnalyticsSummaryResponse;
import com.cardamage.api.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> summary(
        @RequestParam(name = "days", required = false, defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(analyticsService.summary(days));
    }
}
