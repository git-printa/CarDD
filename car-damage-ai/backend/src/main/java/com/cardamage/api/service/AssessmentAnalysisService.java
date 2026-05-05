package com.cardamage.api.service;

import com.cardamage.api.client.AiInferenceClient;
import com.cardamage.api.config.AppProperties;
import com.cardamage.api.model.AiStackInfo;
import com.cardamage.api.model.AssessmentItem;
import com.cardamage.api.model.AssessmentResultResponse;
import com.cardamage.api.model.DamageCostItem;
import com.cardamage.api.model.UploadBatchResponse;
import com.cardamage.api.model.ai.AiDetection;
import com.cardamage.api.model.ai.AiPredictResponse;
import com.cardamage.api.repository.AssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AssessmentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentAnalysisService.class);

    private final AssessmentStoreService assessmentStoreService;
    private final AiInferenceClient aiInferenceClient;
    private final CostEstimationService costEstimationService;
    private final PartDetectionMatcher partDetectionMatcher;
    private final AppProperties appProperties;
    private final AssessmentRepository assessmentRepository;

    public AssessmentAnalysisService(
        AssessmentStoreService assessmentStoreService,
        AiInferenceClient aiInferenceClient,
        CostEstimationService costEstimationService,
        PartDetectionMatcher partDetectionMatcher,
        AppProperties appProperties,
        AssessmentRepository assessmentRepository
    ) {
        this.assessmentStoreService = assessmentStoreService;
        this.aiInferenceClient = aiInferenceClient;
        this.costEstimationService = costEstimationService;
        this.partDetectionMatcher = partDetectionMatcher;
        this.appProperties = appProperties;
        this.assessmentRepository = assessmentRepository;
    }

    public AssessmentResultResponse analyzeBatch(String batchId) {
        UploadBatchResponse batch = assessmentStoreService.getBatch(batchId);

        List<DamageCostItem> damageItems = new ArrayList<>();
        int total = 0;
        AiPredictResponse stackSource = null;

        for (AssessmentItem item : batch.getItems()) {
            Path imagePath = Path.of(appProperties.getUploadDir(), batch.getBatchId(), item.getStoredFilename());
            AiPredictResponse aiResponse = aiInferenceClient.predict(imagePath, item.getOriginalFilename(), item.getContentType());
            if (stackSource == null) {
                stackSource = aiResponse;
            }

            int imgW = aiResponse.getImageWidth();
            int imgH = aiResponse.getImageHeight();
            String inferenceMode = aiResponse.getInferenceMode() == null ? "unknown" : aiResponse.getInferenceMode();
            List<AiDetection> partDetections = safePartDetections(aiResponse);
            if (imgH <= 0) {
                imgH = imgW > 0 ? imgW : 1;
            }

            for (AiDetection detection : safeDetections(aiResponse)) {
                List<Integer> box = safeBox(detection.getBox());
                if (!DamageAssessmentRules.isBillableDamage(detection.getLabel(), box, imgW, imgH, inferenceMode)) {
                    continue;
                }
                String damageType = DamageAssessmentRules.resolveDamageType(
                    detection.getLabel(),
                    detection.getConfidence(),
                    box,
                    imgW,
                    imgH,
                    inferenceMode
                );
                if (damageType.isEmpty()) {
                    continue;
                }
                int x = box.size() > 0 ? box.get(0) : 0;
                int y = box.size() > 1 ? box.get(1) : 0;
                int w = box.size() > 2 ? box.get(2) : 0;
                int h = box.size() > 3 ? box.get(3) : 0;

                String part = partDetectionMatcher.matchPart(box, partDetections);
                if (part.isEmpty()) {
                    part = costEstimationService.inferPartFromLabelOrBox(
                        detection.getLabel(),
                        imgW,
                        imgH,
                        x,
                        y,
                        w,
                        h
                    );
                }
                int estimatedCost = costEstimationService.estimateWithContext(
                    damageType,
                    part,
                    detection.getConfidence(),
                    w,
                    h,
                    imgW,
                    imgH
                );

                total += estimatedCost;
                damageItems.add(new DamageCostItem(
                    damageType,
                    detection.getConfidence(),
                    box,
                    part,
                    estimatedCost,
                    item.getImageId()
                ));
            }
        }

        assessmentRepository.replaceDetections(batchId, damageItems, Instant.now());
        log.info("Analyzed batch {} -> {} damages, total cost {}", batchId, damageItems.size(), total);
        return new AssessmentResultResponse(damageItems, total, buildAiStack(stackSource));
    }

    private AiStackInfo buildAiStack(AiPredictResponse src) {
        if (src == null) {
            return new AiStackInfo("unknown", null, null, "No AI response — check ai-service connectivity.");
        }
        String mode = src.getInferenceMode() != null ? src.getInferenceMode() : "unknown";
        String path = src.getModelPath();
        String layout = src.getMockLayout();
        String note;
        if ("mock".equalsIgnoreCase(mode)) {
            if ("front_collision".equalsIgnoreCase(layout != null ? layout : "")) {
                note =
                    "Demo layout: synthetic front hood, headlight, and bumper regions (not read from pixels). "
                        + "For real estimates, train YOLO on your labeled uploads and set INFERENCE_MODE=yolo.";
            } else {
                note =
                    "Synthetic damage boxes for testing. "
                        + "Set MOCK_LAYOUT=front_collision on ai-service for a front-impact demo, or train a damage model.";
            }
        } else {
            if (DamageAssessmentRules.isGenericYoloHeuristicEnabled()) {
                note =
                    "Running real YOLO inference with fallback generic-label mapping enabled "
                        + "(ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC=true). "
                        + "For best accuracy, use custom damage weights.";
            } else {
                note =
                    "Running real YOLO inference. Best results require custom weights trained for scratch/dent/crack. "
                        + "Generic object-detection checkpoints are not mapped to damage classes unless "
                        + "ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC=true is explicitly set on backend.";
            }
        }
        return new AiStackInfo(mode, path, layout, note);
    }

    private List<AiDetection> safeDetections(AiPredictResponse response) {
        if (response == null || response.getDetections() == null) {
            return Collections.emptyList();
        }
        return response.getDetections();
    }

    private List<AiDetection> safePartDetections(AiPredictResponse response) {
        if (response == null || response.getPartDetections() == null) {
            return Collections.emptyList();
        }
        return response.getPartDetections();
    }

    private List<Integer> safeBox(List<Integer> box) {
        if (box == null) {
            return List.of(0, 0, 0, 0);
        }
        return box;
    }
}
