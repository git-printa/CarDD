package com.cardamage.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AssessmentResultResponse {

    public static final String DEFAULT_CURRENCY = "ILS";

    private List<DamageCostItem> damages;
    private int totalCost;
    private String currency;

    @JsonProperty("ai_stack")
    private AiStackInfo aiStack;

    public AssessmentResultResponse(List<DamageCostItem> damages, int totalCost, AiStackInfo aiStack) {
        this.damages = damages;
        this.totalCost = totalCost;
        this.currency = DEFAULT_CURRENCY;
        this.aiStack = aiStack;
    }

    public List<DamageCostItem> getDamages() {
        return damages;
    }

    @JsonProperty("total_cost")
    public int getTotalCost() {
        return totalCost;
    }

    @JsonProperty("total_cost_nis")
    public int getTotalCostNis() {
        return totalCost;
    }

    public String getCurrency() {
        return currency;
    }

    public AiStackInfo getAiStack() {
        return aiStack;
    }
}
