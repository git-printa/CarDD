package com.cardamage.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiStackInfo {

    private String inferenceMode;
    private String modelPath;
    private String mockLayout;
    private String note;

    public AiStackInfo() {
    }

    public AiStackInfo(String inferenceMode, String modelPath, String mockLayout, String note) {
        this.inferenceMode = inferenceMode;
        this.modelPath = modelPath;
        this.mockLayout = mockLayout;
        this.note = note;
    }

    @JsonProperty("inference_mode")
    public String getInferenceMode() {
        return inferenceMode;
    }

    public void setInferenceMode(String inferenceMode) {
        this.inferenceMode = inferenceMode;
    }

    @JsonProperty("model_path")
    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    @JsonProperty("mock_layout")
    public String getMockLayout() {
        return mockLayout;
    }

    public void setMockLayout(String mockLayout) {
        this.mockLayout = mockLayout;
    }

    @JsonProperty("note")
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
