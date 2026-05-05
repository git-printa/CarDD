package com.cardamage.api.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filters generic object-detector output (e.g. COCO "car") so only damage classes
 * used by the cost table are billed. Also drops huge boxes that usually mean
 * "whole vehicle", not a localized damage region.
 */
public final class DamageAssessmentRules {

    private static final Set<String> DAMAGE_TYPES = Set.of("scratch", "dent", "crack");
    private static final Map<String, String> DAMAGE_ALIASES = Map.ofEntries(
        Map.entry("scratched", "scratch"),
        Map.entry("scratches", "scratch"),
        Map.entry("dented", "dent"),
        Map.entry("cracked", "crack"),
        Map.entry("fracture", "crack"),
        Map.entry("glass shatter", "crack"),
        Map.entry("glass_shatter", "crack"),
        Map.entry("lamp broken", "crack"),
        Map.entry("lamp_broken", "crack"),
        Map.entry("tire flat", "crack"),
        Map.entry("tire_flat", "crack")
    );
    private static final Set<String> VEHICLE_LIKE = Set.of("car", "truck", "bus", "motorcycle", "bicycle");
    private static final boolean ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC = Boolean.parseBoolean(
        System.getenv().getOrDefault("ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC", "false")
    );

    private static final double MAX_BOX_FRACTION_OF_IMAGE = 0.7;

    private DamageAssessmentRules() {
    }

    public static String normalizeDamageType(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isKnownDamageType(String normalizedLabel) {
        return normalizedLabel != null && DAMAGE_TYPES.contains(normalizedLabel);
    }

    public static boolean isGenericYoloHeuristicEnabled() {
        return ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC;
    }

    public static boolean isBillableDamage(String label, List<Integer> box, int imageWidth, int imageHeight) {
        return isBillableDamage(label, box, imageWidth, imageHeight, "mock");
    }

    public static boolean isBillableDamage(
        String label,
        List<Integer> box,
        int imageWidth,
        int imageHeight,
        String inferenceMode
    ) {
        String resolved = resolveDamageType(label, 0.5, box, imageWidth, imageHeight, inferenceMode);
        if (resolved.isEmpty()) {
            return false;
        }
        return boxFraction(box, imageWidth, imageHeight) < MAX_BOX_FRACTION_OF_IMAGE;
    }

    public static String resolveDamageType(
        String label,
        double confidence,
        List<Integer> box,
        int imageWidth,
        int imageHeight,
        String inferenceMode
    ) {
        String t = normalizeDamageType(label);
        String knownType = resolveKnownDamageType(t);
        if (!knownType.isEmpty()) {
            return knownType;
        }
        if (!"yolo".equalsIgnoreCase(inferenceMode == null ? "" : inferenceMode)) {
            return "";
        }
        if (!ALLOW_GENERIC_YOLO_DAMAGE_HEURISTIC) {
            return "";
        }
        if (t.isEmpty()) {
            return "";
        }
        if (!VEHICLE_LIKE.contains(t)) {
            // For generic checkpoints, still allow a fallback map for medium/high confidence localized boxes.
            if (confidence < 0.45) {
                return "";
            }
        }
        double frac = boxFraction(box, imageWidth, imageHeight);
        if (frac >= 0.12) {
            return "dent";
        }
        if (confidence >= 0.82) {
            return "crack";
        }
        return "scratch";
    }

    private static String resolveKnownDamageType(String normalizedLabel) {
        if (normalizedLabel == null || normalizedLabel.isEmpty()) {
            return "";
        }
        if (isKnownDamageType(normalizedLabel)) {
            return normalizedLabel;
        }
        String alias = DAMAGE_ALIASES.get(normalizedLabel);
        if (alias != null) {
            return alias;
        }
        String[] tokens = normalizedLabel.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (isKnownDamageType(token)) {
                return token;
            }
            String tokenAlias = DAMAGE_ALIASES.get(token);
            if (tokenAlias != null) {
                return tokenAlias;
            }
        }
        return "";
    }

    private static double boxFraction(List<Integer> box, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return 0.0;
        }
        int w = box != null && box.size() > 2 ? box.get(2) : 0;
        int h = box != null && box.size() > 3 ? box.get(3) : 0;
        if (w <= 0 || h <= 0) {
            return 0.0;
        }
        return (w * (double) h) / (imageWidth * (double) imageHeight);
    }
}
