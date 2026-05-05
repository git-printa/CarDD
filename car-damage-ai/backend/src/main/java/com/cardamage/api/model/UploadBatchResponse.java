package com.cardamage.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UploadBatchResponse {

    private String batchId;
    private String vehicleId;
    private int totalFiles;
    private Instant createdAt;
    private List<AssessmentItem> items = new ArrayList<>();

    public UploadBatchResponse() {
    }

    public UploadBatchResponse(String batchId, String vehicleId, int totalFiles, Instant createdAt, List<AssessmentItem> items) {
        this.batchId = batchId;
        this.vehicleId = vehicleId;
        this.totalFiles = totalFiles;
        this.createdAt = createdAt;
        this.items = items != null ? items : new ArrayList<>();
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    @JsonProperty("totalFiles")
    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<AssessmentItem> getItems() {
        return items;
    }

    public void setItems(List<AssessmentItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
