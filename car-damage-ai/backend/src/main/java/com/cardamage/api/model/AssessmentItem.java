package com.cardamage.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class AssessmentItem {

    private String imageId;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private long sizeBytes;
    private int width;
    private int height;
    private String status;
    private Instant uploadedAt;

    public AssessmentItem() {
    }

    public AssessmentItem(String imageId, String originalFilename, String storedFilename, String contentType,
                          long sizeBytes, int width, int height, String status, Instant uploadedAt) {
        this.imageId = imageId;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.status = status;
        this.uploadedAt = uploadedAt;
    }

    @JsonProperty("imageId")
    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @JsonProperty("sizeBytes")
    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
