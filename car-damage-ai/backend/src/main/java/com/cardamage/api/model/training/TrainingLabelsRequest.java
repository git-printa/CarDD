package com.cardamage.api.model.training;

import java.util.ArrayList;
import java.util.List;

public class TrainingLabelsRequest {
    private List<TrainingLabel> labels = new ArrayList<>();

    public List<TrainingLabel> getLabels() {
        return labels;
    }

    public void setLabels(List<TrainingLabel> labels) {
        this.labels = labels != null ? labels : new ArrayList<>();
    }
}
