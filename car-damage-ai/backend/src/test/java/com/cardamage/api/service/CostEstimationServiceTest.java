package com.cardamage.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostEstimationServiceTest {

    private final CostEstimationService service = new CostEstimationService();

    @Test
    void shouldEstimateKnownTypeAndPart() {
        assertEquals(180, service.estimate("scratch", "door"));
        assertEquals(580, service.estimate("crack", "bumper"));
        assertEquals(980, service.estimate("crack", "headlight"));
    }

    @Test
    void shouldUseDefaultForUnknownPart() {
        assertEquals(320, service.estimate("dent", "mirror"));
    }

    @Test
    void shouldInferPartFrom2DPosition() {
        assertEquals("bumper", service.inferPartFromBox(900, 600, 30, 520, 60, 50));
        assertEquals("hood", service.inferPartFromBox(900, 600, 760, 40, 80, 55));
        assertEquals("headlight", service.inferPartFromBox(900, 600, 40, 250, 80, 70));
        assertEquals("door", service.inferPartFromBox(900, 600, 380, 200, 100, 80));
    }

    @Test
    void estimateWithContextShouldScaleByBoxAreaAndConfidence() {
        int small = service.estimateWithContext("scratch", "door", 0.5, 10, 10, 1000, 1000);
        int large = service.estimateWithContext("scratch", "door", 0.5, 200, 200, 1000, 1000);
        assertTrue(large > small);

        int lowConf = service.estimateWithContext("dent", "bumper", 0.2, 50, 50, 800, 600);
        int highConf = service.estimateWithContext("dent", "bumper", 0.95, 50, 50, 800, 600);
        assertTrue(highConf >= lowConf);
    }

    @Test
    void estimateWithContextShouldMatchBaseWhenImageDimensionsMissing() {
        int base = service.estimate("crack", "hood");
        assertEquals(base, service.estimateWithContext("crack", "hood", 0.9, 40, 40, 0, 800));
        assertEquals(base, service.estimateWithContext("crack", "hood", 0.9, 0, 40, 800, 800));
    }
}
