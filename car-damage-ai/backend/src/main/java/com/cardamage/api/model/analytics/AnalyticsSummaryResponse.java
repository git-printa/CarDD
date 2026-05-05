package com.cardamage.api.model.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AnalyticsSummaryResponse {

    public static final String DEFAULT_CURRENCY = "ILS";

    @JsonProperty("time_range_days")
    private int timeRangeDays;
    private long batches;
    private long images;
    private long detections;
    @JsonProperty("total_cost")
    private long totalCost;
    @JsonProperty("top_damage_types")
    private List<AnalyticsBreakdownItem> topDamageTypes;
    @JsonProperty("cost_by_part")
    private List<AnalyticsBreakdownItem> costByPart;
    private String currency;

    public AnalyticsSummaryResponse(
        int timeRangeDays,
        long batches,
        long images,
        long detections,
        long totalCost,
        List<AnalyticsBreakdownItem> topDamageTypes,
        List<AnalyticsBreakdownItem> costByPart
    ) {
        this.timeRangeDays = timeRangeDays;
        this.batches = batches;
        this.images = images;
        this.detections = detections;
        this.totalCost = totalCost;
        this.topDamageTypes = topDamageTypes;
        this.costByPart = costByPart;
        this.currency = DEFAULT_CURRENCY;
    }

    public int getTimeRangeDays() {
        return timeRangeDays;
    }

    public long getBatches() {
        return batches;
    }

    public long getImages() {
        return images;
    }

    public long getDetections() {
        return detections;
    }

    public long getTotalCost() {
        return totalCost;
    }

    @JsonProperty("total_cost_nis")
    public long getTotalCostNis() {
        return totalCost;
    }

    public List<AnalyticsBreakdownItem> getTopDamageTypes() {
        return topDamageTypes;
    }

    public List<AnalyticsBreakdownItem> getCostByPart() {
        return costByPart;
    }

    public String getCurrency() {
        return currency;
    }
}
