package com.cardamage.api.service;

import com.cardamage.api.model.ai.AiDetection;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PartDetectionMatcher {

    private static final Set<String> KNOWN_PARTS = Set.of("door", "bumper", "hood", "headlight", "fender");
    private static final double MIN_IOU = 0.08;

    public String matchPart(List<Integer> damageBox, List<AiDetection> partDetections) {
        if (partDetections == null || partDetections.isEmpty()) {
            return "";
        }
        double bestScore = 0.0;
        String bestPart = "";
        for (AiDetection candidate : partDetections) {
            String part = resolvePartLabel(candidate.getLabel());
            if (part.isEmpty()) {
                continue;
            }
            double iou = iou(damageBox, candidate.getBox());
            if (iou <= 0.0) {
                continue;
            }
            double score = iou * Math.max(0.1, candidate.getConfidence());
            if (score > bestScore) {
                bestScore = score;
                bestPart = part;
            }
        }
        if (bestScore < MIN_IOU) {
            return "";
        }
        return bestPart;
    }

    private String resolvePartLabel(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
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

    private double iou(List<Integer> boxA, List<Integer> boxB) {
        int ax = get(boxA, 0);
        int ay = get(boxA, 1);
        int aw = Math.max(0, get(boxA, 2));
        int ah = Math.max(0, get(boxA, 3));
        int bx = get(boxB, 0);
        int by = get(boxB, 1);
        int bw = Math.max(0, get(boxB, 2));
        int bh = Math.max(0, get(boxB, 3));
        if (aw == 0 || ah == 0 || bw == 0 || bh == 0) {
            return 0.0;
        }
        int ax2 = ax + aw;
        int ay2 = ay + ah;
        int bx2 = bx + bw;
        int by2 = by + bh;

        int interW = Math.max(0, Math.min(ax2, bx2) - Math.max(ax, bx));
        int interH = Math.max(0, Math.min(ay2, by2) - Math.max(ay, by));
        long interArea = (long) interW * interH;
        if (interArea <= 0) {
            return 0.0;
        }
        long areaA = (long) aw * ah;
        long areaB = (long) bw * bh;
        long union = areaA + areaB - interArea;
        if (union <= 0) {
            return 0.0;
        }
        return interArea / (double) union;
    }

    private int get(List<Integer> box, int idx) {
        if (box == null || box.size() <= idx || box.get(idx) == null) {
            return 0;
        }
        return box.get(idx);
    }
}
