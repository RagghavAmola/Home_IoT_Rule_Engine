package com.smarthome.engine;

import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngine();
    }

    @Test
    @DisplayName("Empty rules or snapshot returns empty evaluation result")
    void testEmptyEvaluation() {
        EvaluationResult res1 = engine.evaluate(StateSnapshot.empty(), List.of());
        assertTrue(res1.actions().isEmpty());
        assertTrue(res1.overrides().isEmpty());
        assertTrue(res1.triggeredRules().isEmpty());

        EvaluationResult res2 = engine.evaluate((StateSnapshot) null, List.of());
        assertTrue(res2.actions().isEmpty());
    }

    @Test
    @DisplayName("Non-conflicting rules trigger and their actions pass through untouched")
    void testNoConflictPassthrough() {
        Rule r1 = new Rule(
                "R1",
                "Turn On Light",
                true,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("motion-1", "motion", Operator.EQ, "DETECTED"),
                List.of(new Action("light-1", "power", "ON")),
                Instant.now()
        );

        Rule r2 = new Rule(
                "R2",
                "Unlock Door",
                true,
                PriorityTier.COMFORT,
                1,
                new LeafCondition("motion-1", "motion", Operator.EQ, "DETECTED"),
                List.of(new Action("door-1", "status", "UNLOCKED")),
                Instant.now()
        );

        StateSnapshot snapshot = StateSnapshot.empty().with("motion-1", "motion", "DETECTED");
        EvaluationResult result = engine.evaluate(snapshot, List.of(r1, r2));

        assertEquals(2, result.triggeredRules().size());
        assertEquals(2, result.actions().size());
        assertTrue(result.overrides().isEmpty());

        List<String> targetDevices = result.actions().stream().map(Action::deviceId).toList();
        assertTrue(targetDevices.contains("light-1"));
        assertTrue(targetDevices.contains("door-1"));
    }

    @Test
    @DisplayName("Conflict resolution integrates cleanly: Safety overrides Comfort")
    void testPriorityConflictResolution() {
        // Comfort rule: if temp < 20, set heater power to ON
        Rule comfortRule = new Rule(
                "R_COMFORT",
                "Comfort Warmth",
                true,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("thermostat-1", "temperature", Operator.LT, "20.0"),
                List.of(new Action("heater-1", "power", "ON")),
                Instant.now()
        );

        // Safety rule: if smoke detected, set heater power to OFF
        Rule safetyRule = new Rule(
                "R_SAFETY",
                "Fire Safety Shutdown",
                true,
                PriorityTier.SAFETY,
                0,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("heater-1", "power", "OFF")),
                Instant.now()
        );

        StateSnapshot snapshot = StateSnapshot.empty()
                .with("thermostat-1", "temperature", "18.0")
                .with("smoke-1", "smoke", "DETECTED");

        EvaluationResult result = engine.evaluate(snapshot, List.of(comfortRule, safetyRule));

        // Both rules triggered
        assertEquals(2, result.triggeredRules().size());

        // Exactly 1 action executed (Safety's OFF)
        assertEquals(1, result.actions().size());
        Action executed = result.actions().get(0);
        assertEquals("heater-1", executed.deviceId());
        assertEquals("power", executed.stateKey());
        assertEquals("OFF", executed.targetValue());

        // Exactly 1 override record generated (Comfort dropped)
        assertEquals(1, result.overrides().size());
        assertEquals("R_SAFETY", result.overrides().get(0).winningRuleId());
        assertEquals("R_COMFORT", result.overrides().get(0).overriddenRuleId());
    }

    @Test
    @DisplayName("Overload accepting Map snapshot evaluates identically")
    void testMapOverload() {
        Rule r = new Rule(
                "R1",
                "Simple Rule",
                true,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("light-1", "power", Operator.EQ, "OFF"),
                List.of(new Action("light-1", "power", "ON")),
                Instant.now()
        );

        StateSnapshot snapshot = StateSnapshot.empty().with("light-1", "power", "OFF");
        EvaluationResult res = engine.evaluate(snapshot.asMap(), List.of(r));

        assertEquals(1, res.actions().size());
        assertEquals("ON", res.actions().get(0).targetValue());
    }
}

