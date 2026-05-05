package com.cardamage.api.model.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AiPredictResponse {

    private List<AiDetection> detections = new ArrayList<>();
    @JsonProperty("part_detections")
    private List<AiDetection> partDetections = new ArrayList<>();
    @JsonProperty("image_width")
    private int imageWidth;
    @JsonProperty("image_height")
    private int imageHeight;
    @JsonProperty("inference_mode")
    private String inferenceMode;

    @JsonProperty("model_path")
    private String modelPath;

    @JsonProperty("mock_layout")
    private String mockLayout;

    public List<AiDetection> getDetections() {
        return detections;
    }

    public void setDetections(List<AiDetection> detections) {
        this.detections = detections;
    }

    public List<AiDetection> getPartDetections() {
        return partDetections;
    }

    public void setPartDetections(List<AiDetection> partDetections) {
        this.partDetections = partDetections;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public String getInferenceMode() {
        return inferenceMode;
    }

    public void setInferenceMode(String inferenceMode) {
        this.inferenceMode = inferenceMode;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getMockLayout() {
        return mockLayout;
    }

    public void setMockLayout(String mockLayout) {
        this.mockLayout = mockLayout;
    }
}
