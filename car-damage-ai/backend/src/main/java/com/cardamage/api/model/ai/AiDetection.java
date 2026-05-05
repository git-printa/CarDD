package com.cardamage.api.model.ai;

import java.util.List;

public class AiDetection {

    private String label;
    private double confidence;
    private List<Integer> box;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<Integer> getBox() {
        return box;
    }

    public void setBox(List<Integer> box) {
        this.box = box;
    }
}
