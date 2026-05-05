package com.cardamage.api.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CostEstimationService {

    private static final Set<String> KNOWN_PARTS = Set.of("door", "bumper", "hood", "headlight", "fender");

    private final Map<String, Integer> table = new HashMap<>();

    public CostEstimationService() {
        // scratch
        table.put(key("scratch", "door"), 180);
        table.put(key("scratch", "bumper"), 320);
        table.put(key("scratch", "hood"), 400);
        table.put(key("scratch", "headlight"), 450);
        table.put(key("scratch", "fender"), 380);

        // dent
        table.put(key("dent", "door"), 350);
        table.put(key("dent", "bumper"), 1050);
        table.put(key("dent", "hood"), 720);
        table.put(key("dent", "headlight"), 820);
        table.put(key("dent", "fender"), 650);

        // crack
        table.put(key("crack", "door"), 380);
        table.put(key("crack", "bumper"), 580);
        table.put(key("crack", "hood"), 780);
        table.put(key("crack", "headlight"), 980);
        table.put(key("crack", "fender"), 740);
    }

    public int estimate(String damageType, String part) {
        String normalizedType = normalize(damageType);
        String normalizedPart = normalize(part);
        return table.getOrDefault(key(normalizedType, normalizedPart), defaultCost(normalizedType));
    }

    /**
     * Rule-based base cost scaled by how large the damage region is on the image and model confidence.
     */
    public int estimateWithContext(
        String damageType,
        String part,
        double confidence,
        int boxWidth,
        int boxHeight,
        int imageWidth,
        int imageHeight
    ) {
        int base = estimate(damageType, part);
        if (imageWidth <= 0 || imageHeight <= 0) {
            return base;
        }
        long imageArea = (long) imageWidth * imageHeight;
        if (imageArea <= 0) {
            return base;
        }
        int bw = Math.max(0, boxWidth);
        int bh = Math.max(0, boxHeight);
        long boxArea = (long) bw * bh;
        if (boxArea <= 0) {
            return base;
        }
        double frac = Math.min(0.5, (double) boxArea / (double) imageArea);
        double refFrac = 0.05;
        double sizeScore = Math.sqrt(Math.min(1.0, frac / refFrac));
        double areaMul = 0.75 + 0.5 * sizeScore;
        double c = Math.max(0.0, Math.min(1.0, confidence));
        double confMul = 0.88 + 0.24 * c;
        long adjusted = Math.round(base * areaMul * confMul);
        long floor = Math.round(base * 0.35);
        return (int) Math.max(floor, adjusted);
    }

    /**
     * Map a bounding box to a body region using 2D position (front photos: lower = bumper, upper = hood).
     */
    public String inferPartFromBox(int imageWidth, int imageHeight, int x, int y, int w, int h) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return "door";
        }
        int safeW = Math.max(1, w);
        int safeH = Math.max(1, h);
        int cx = x + (safeW / 2);
        int cy = y + (safeH / 2);
        double nx = cx / (double) imageWidth;
        double ny = cy / (double) imageHeight;
        double boxFrac = Math.min(1.0, (safeW * (double) safeH) / (imageWidth * (double) imageHeight));

        if (ny >= 0.72 || (ny >= 0.64 && boxFrac >= 0.08)) {
            return "bumper";
        }
        if (ny <= 0.28 || (ny <= 0.35 && boxFrac >= 0.09)) {
            return "hood";
        }
        if (ny > 0.34 && ny < 0.7 && (nx < 0.2 || nx > 0.8) && boxFrac <= 0.08) {
            return "headlight";
        }
        if (nx < 0.23 || nx > 0.77) {
            return "fender";
        }
        return "door";
    }

    /**
     * If the model label contains a known car section token (e.g. dent_bumper),
     * prefer that section; otherwise fallback to geometric inference.
     */
    public String inferPartFromLabelOrBox(String sourceLabel, int imageWidth, int imageHeight, int x, int y, int w, int h) {
        String labelPart = inferPartFromLabel(sourceLabel);
        if (!labelPart.isEmpty()) {
            return labelPart;
        }
        return inferPartFromBox(imageWidth, imageHeight, x, y, w, h);
    }

    private String inferPartFromLabel(String sourceLabel) {
        String normalized = normalize(sourceLabel);
        if (normalized.isEmpty()) {
            return "";
        }
        if (KNOWN_PARTS.contains(normalized)) {
            return normalized;
        }
        String[] tokens = normalized.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (KNOWN_PARTS.contains(token)) {
                return token;
            }
        }
        return "";
    }

    private int defaultCost(String damageType) {
        switch (damageType) {
            case "scratch":
                return 170;
            case "dent":
                return 320;
            case "crack":
                return 470;
            default:
                return 250;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String key(String damageType, String part) {
        return damageType + "::" + part;
    }
}
