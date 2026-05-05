package com.cardamage.api.controller;

import com.cardamage.api.model.AssessmentResultResponse;
import com.cardamage.api.model.UploadBatchResponse;
import com.cardamage.api.service.AssessmentAnalysisService;
import com.cardamage.api.service.AssessmentStoreService;
import javax.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping
public class AssessmentController {

    private final AssessmentStoreService assessmentStoreService;
    private final AssessmentAnalysisService assessmentAnalysisService;

    public AssessmentController(AssessmentStoreService assessmentStoreService, AssessmentAnalysisService assessmentAnalysisService) {
        this.assessmentStoreService = assessmentStoreService;
        this.assessmentAnalysisService = assessmentAnalysisService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "service", "backend",
            "timestamp", Instant.now().toString()
        );
    }

    @PostMapping("/api/v1/assessments/upload")
    public ResponseEntity<UploadBatchResponse> uploadImages(
        @RequestParam("files") MultipartFile[] files,
        @RequestParam(value = "vehicleId", required = false) String vehicleId
    ) {
        return ResponseEntity.ok(assessmentStoreService.createBatch(vehicleId, files));
    }


    @PostMapping("/api/v1/assessments/{batchId}/analyze")
    public ResponseEntity<AssessmentResultResponse> analyzeBatch(@PathVariable @NotBlank String batchId) {
        return ResponseEntity.ok(assessmentAnalysisService.analyzeBatch(batchId));
    }

    @GetMapping("/api/v1/assessments/{batchId}")
    public ResponseEntity<UploadBatchResponse> getBatch(@PathVariable @NotBlank String batchId) {
        return ResponseEntity.ok(assessmentStoreService.getBatch(batchId));
    }
}
