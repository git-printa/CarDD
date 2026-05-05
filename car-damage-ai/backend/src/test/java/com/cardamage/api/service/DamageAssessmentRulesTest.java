package com.cardamage.api.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageAssessmentRulesTest {

    @Test
    void cocoCarIsNotBillable() {
        assertFalse(
            DamageAssessmentRules.isBillableDamage("car", Arrays.asList(0, 0, 100, 50), 1000, 800)
        );
    }

    @Test
    void cocoCarIsNotBillableInYoloModeByDefault() {
        assertFalse(
            DamageAssessmentRules.isBillableDamage("car", Arrays.asList(0, 0, 250, 180), 1000, 800, "yolo")
        );
    }

    @Test
    void scratchIsBillableWhenBoxIsSmallEnough() {
        assertTrue(
            DamageAssessmentRules.isBillableDamage("Scratch", Arrays.asList(10, 10, 80, 60), 1000, 800)
        );
    }

    @Test
    void scratchRejectedWhenBoxCoversMostOfImage() {
        assertFalse(
            DamageAssessmentRules.isBillableDamage(
                "scratch",
                Arrays.asList(0, 0, 900, 700),
                1000,
                800
            )
        );
    }

    @Test
    void normalizeTrimsAndLowercases() {
        assertEquals("dent", DamageAssessmentRules.normalizeDamageType("  Dent "));
    }

    @Test
    void emptyBoxStillBillableIfDamageTypeKnown() {
        assertTrue(
            DamageAssessmentRules.isBillableDamage("crack", Collections.emptyList(), 640, 480)
        );
    }

    @Test
    void resolveDamageTypeDoesNotMapVehicleLabelsByDefault() {
        assertEquals(
            "",
            DamageAssessmentRules.resolveDamageType("car", 0.76, Arrays.asList(0, 0, 360, 280), 1000, 800, "yolo")
        );
        assertEquals(
            "",
            DamageAssessmentRules.resolveDamageType("motorcycle", 0.9, Arrays.asList(0, 0, 80, 40), 1000, 800, "yolo")
        );
    }
}
