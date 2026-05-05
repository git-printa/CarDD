package com.cardamage.api.service;

import com.cardamage.api.model.ai.AiDetection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartDetectionMatcherTest {

    private final PartDetectionMatcher matcher = new PartDetectionMatcher();

    @Test
    void shouldPickPartByHighestOverlap() {
        AiDetection hood = detection("hood", 0.9, 100, 50, 300, 120);
        AiDetection bumper = detection("bumper", 0.9, 80, 260, 340, 110);
        String part = matcher.matchPart(List.of(120, 70, 80, 40), List.of(hood, bumper));
        assertEquals("hood", part);
    }

    @Test
    void shouldReturnEmptyWhenNoOverlap() {
        AiDetection hood = detection("hood", 0.9, 100, 50, 300, 120);
        String part = matcher.matchPart(List.of(10, 260, 40, 20), List.of(hood));
        assertEquals("", part);
    }

    private AiDetection detection(String label, double confidence, int x, int y, int w, int h) {
        AiDetection out = new AiDetection();
        out.setLabel(label);
        out.setConfidence(confidence);
        out.setBox(List.of(x, y, w, h));
        return out;
    }
}
