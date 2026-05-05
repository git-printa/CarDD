package com.cardamage.api.model.training;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrainingImageSummary {
    private String imageId;
    private String batchId;
    private String originalFilename;
    private int width;
    private int height;

    public TrainingImageSummary() {
    }

    public TrainingImageSummary(String imageId, String batchId, String originalFilename, int width, int height) {
        this.imageId = imageId;
        this.batchId = batchId;
        this.originalFilename = originalFilename;
        this.width = width;
        this.height = height;
    }

    @JsonProperty("image_id")
    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    @JsonProperty("batch_id")
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    @JsonProperty("original_filename")
    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
