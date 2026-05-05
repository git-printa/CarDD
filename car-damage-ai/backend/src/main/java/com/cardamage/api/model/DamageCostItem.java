package com.cardamage.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DamageCostItem {

    private String type;
    private double confidence;
    private List<Integer> box;
    private String part;
    private int estimatedCost;
    private String imageId;

    public DamageCostItem(String type, double confidence, List<Integer> box, String part, int estimatedCost, String imageId) {
        this.type = type;
        this.confidence = confidence;
        this.box = box;
        this.part = part;
        this.estimatedCost = estimatedCost;
        this.imageId = imageId;
    }

    public String getType() { return type; }
    public double getConfidence() { return confidence; }
    public List<Integer> getBox() { return box; }
    public String getPart() { return part; }

    @JsonProperty("estimated_cost")
    public int getEstimatedCost() { return estimatedCost; }

    @JsonProperty("estimated_cost_nis")
    public int getEstimatedCostNis() { return estimatedCost; }

    @JsonProperty("image_id")
    public String getImageId() { return imageId; }
}
