package com.cardamage.api.model.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnalyticsBreakdownItem {
    private String name;
    private long count;
    @JsonProperty("total_cost")
    private long totalCost;

    public AnalyticsBreakdownItem(String name, long count, long totalCost) {
        this.name = name;
        this.count = count;
        this.totalCost = totalCost;
    }

    public String getName() {
        return name;
    }

    public long getCount() {
        return count;
    }

    public long getTotalCost() {
        return totalCost;
    }

    @JsonProperty("total_cost_nis")
    public long getTotalCostNis() {
        return totalCost;
    }
}
