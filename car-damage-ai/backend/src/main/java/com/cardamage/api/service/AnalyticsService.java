package com.cardamage.api.service;

import com.cardamage.api.model.analytics.AnalyticsBreakdownItem;
import com.cardamage.api.model.analytics.AnalyticsSummaryResponse;
import com.cardamage.api.repository.AssessmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final AssessmentRepository assessmentRepository;

    public AnalyticsService(AssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
    }

    public AnalyticsSummaryResponse summary(int days) {
        int safeDays = Math.max(1, Math.min(days, 3650));
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);
        return new AnalyticsSummaryResponse(
            safeDays,
            assessmentRepository.countBatchesSince(since),
            assessmentRepository.countImagesSince(since),
            assessmentRepository.countDetectionsSince(since),
            assessmentRepository.sumEstimatedCostSince(since),
            assessmentRepository.topDamageTypesSince(since, 5).stream()
                .map(r -> new AnalyticsBreakdownItem(r.getName(), r.getCount(), r.getTotalCost()))
                .collect(Collectors.toList()),
            assessmentRepository.costByPartSince(since, 5).stream()
                .map(r -> new AnalyticsBreakdownItem(r.getName(), r.getCount(), r.getTotalCost()))
                .collect(Collectors.toList())
        );
    }

    public List<AnalyticsBreakdownItem> topDamageTypes(int days, int limit) {
        Instant since = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return assessmentRepository.topDamageTypesSince(since, Math.max(1, limit)).stream()
            .map(r -> new AnalyticsBreakdownItem(r.getName(), r.getCount(), r.getTotalCost()))
            .collect(Collectors.toList());
    }

    public List<AnalyticsBreakdownItem> costByPart(int days, int limit) {
        Instant since = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return assessmentRepository.costByPartSince(since, Math.max(1, limit)).stream()
            .map(r -> new AnalyticsBreakdownItem(r.getName(), r.getCount(), r.getTotalCost()))
            .collect(Collectors.toList());
    }
}
