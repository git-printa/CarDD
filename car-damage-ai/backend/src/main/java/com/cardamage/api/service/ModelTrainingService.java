package com.cardamage.api.service;

import com.cardamage.api.config.AppProperties;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainingService.class);

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    private volatile Map<String, Object> lastStatus = defaultStatus();

    public ModelTrainingService(RestTemplate restTemplate, AppProperties appProperties) {
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
    }

    public synchronized Map<String, Object> startTraining(String dataYamlPath) {
        if (dataYamlPath == null || dataYamlPath.isBlank()) {
            throw new IllegalArgumentException("data_yaml is required");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("data_yaml", dataYamlPath);
        request.put("model_path", envOr("TRAIN_MODEL_PATH", "/workspace/model-training/cache/base-weights/yolov8m.pt"));
        request.put("epochs", envInt("TRAIN_EPOCHS", 30));
        request.put("imgsz", envInt("TRAIN_IMGSZ", 640));
        request.put("project", envOr("TRAIN_PROJECT", "/workspace/model-training/runs"));
        request.put("name", envOr("TRAIN_RUN_NAME", "cardd_6class"));
        request.put("patience", envInt("TRAIN_PATIENCE", 15));
        request.put("deploy_path", envOr("TRAIN_DEPLOY_PATH", "/docker-weights/car_damage_best.pt"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                appProperties.getAiServiceUrl() + "/train/start",
                request,
                Map.class
            );
            if (response == null) {
                throw new IllegalArgumentException("AI training start returned empty response");
            }
            lastStatus = mergeKnownStatus(lastStatus, response);
            return response;
        } catch (RestClientException ex) {
            log.error("Failed to start remote training: {}", ex.getMessage());
            throw new IllegalArgumentException("Failed to start training on ai-service: " + ex.getMessage(), ex);
        }
    }

    public synchronized Map<String, Object> status() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                appProperties.getAiServiceUrl() + "/train/status",
                Map.class
            );
            if (response != null) {
                lastStatus = mergeKnownStatus(lastStatus, response);
                return response;
            }
        } catch (RestClientException ex) {
            log.warn("Remote training status unavailable: {}", ex.getMessage());
            lastStatus.put("last_error", "Status unavailable: " + ex.getMessage());
        }
        return new LinkedHashMap<>(lastStatus);
    }

    private static Map<String, Object> defaultStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", false);
        out.put("started_at", null);
        out.put("finished_at", null);
        out.put("exit_code", null);
        out.put("command", "remote:ai-service/train");
        out.put("data_yaml", null);
        out.put("best_pt", null);
        out.put("last_error", null);
        out.put("run_id", null);
        out.put("log_tail", new ArrayList<>());
        return out;
    }

    private static Map<String, Object> mergeKnownStatus(Map<String, Object> base, Map<String, Object> update) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        for (String key : defaultStatus().keySet()) {
            if (update.containsKey(key)) {
                out.put(key, update.get(key));
            }
        }
        if (!out.containsKey("command")) {
            out.put("command", "remote:ai-service/train");
        }
        return out;
    }

    private static int envInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v;
    }
}
