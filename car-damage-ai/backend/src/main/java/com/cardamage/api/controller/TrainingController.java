package com.cardamage.api.controller;

import com.cardamage.api.model.training.TrainingImageSummary;
import com.cardamage.api.model.training.TrainingLabel;
import com.cardamage.api.model.training.TrainingLabelsRequest;
import com.cardamage.api.model.training.TrainingStartRequest;
import com.cardamage.api.service.ModelTrainingService;
import com.cardamage.api.service.TrainingDataService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingDataService trainingDataService;
    private final ModelTrainingService modelTrainingService;

    public TrainingController(TrainingDataService trainingDataService, ModelTrainingService modelTrainingService) {
        this.trainingDataService = trainingDataService;
        this.modelTrainingService = modelTrainingService;
    }

    @GetMapping("/images")
    public ResponseEntity<List<TrainingImageSummary>> listImages() {
        return ResponseEntity.ok(trainingDataService.listImages());
    }

    @GetMapping("/images/{imageId}/content")
    public ResponseEntity<byte[]> imageContent(@PathVariable String imageId) throws IOException {
        String mediaType = trainingDataService.imageMediaType(imageId);
        byte[] data = trainingDataService.readImageBytes(imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mediaType)).body(data);
    }

    @GetMapping("/images/{imageId}/labels")
    public ResponseEntity<Map<String, List<TrainingLabel>>> loadLabels(@PathVariable String imageId) {
        return ResponseEntity.ok(Map.of("labels", trainingDataService.loadLabels(imageId)));
    }

    @PostMapping("/images/{imageId}/labels")
    public ResponseEntity<Map<String, List<TrainingLabel>>> saveLabels(
        @PathVariable String imageId,
        @RequestBody TrainingLabelsRequest request
    ) {
        List<TrainingLabel> labels = trainingDataService.saveLabels(imageId, request.getLabels());
        return ResponseEntity.ok(Map.of("labels", labels));
    }

    @PostMapping("/export-yolo")
    public ResponseEntity<Map<String, Object>> exportYolo() {
        TrainingDataService.ExportResult result = trainingDataService.exportYoloDataset();
        return ResponseEntity.ok(Map.of(
            "dataset_path", result.getDatasetPath(),
            "data_yaml", result.getDataYaml(),
            "labeled_images", result.getLabeledImages(),
            "class_names", result.getClassNames()
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startTraining(@RequestBody TrainingStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return ResponseEntity.ok(modelTrainingService.startTraining(request.getDataYaml()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> trainingStatus() {
        return ResponseEntity.ok(modelTrainingService.status());
    }
}
