package com.smarthome.engine;

import com.smarthome.domain.Action;
import com.smarthome.domain.ConditionGroup;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.engine.priority.TriggeredRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluatorTest {

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RuleEvaluator();
    }

    @Test
    @DisplayName("Empty or null rules or snapshot returns empty list")
    void testEmptyInputs() {
        assertTrue(evaluator.findTriggered(null, StateSnapshot.empty()).isEmpty());
        assertTrue(evaluator.findTriggered(List.of(), StateSnapshot.empty()).isEmpty());
        assertTrue(evaluator.findTriggered(List.of(), (StateSnapshot) null).isEmpty());
    }

    @Test
    @DisplayName("Finds triggered rules when leaf condition matches snapshot")
    void testSingleLeafConditionTriggered() {
        Rule rule = new Rule(
                "R1",
                "Smoke Alert",
                true,
                PriorityTier.SAFETY,
                1,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );

        StateSnapshot snapshotTriggering = StateSnapshot.empty().with("smoke-1", "smoke", "DETECTED");
        List<TriggeredRule> triggered = evaluator.findTriggered(List.of(rule), snapshotTriggering);

        assertEquals(1, triggered.size());
        assertEquals("R1", triggered.get(0).rule().getId());
        assertEquals(1, triggered.get(0).actions().size());
        assertEquals("siren-1", triggered.get(0).actions().get(0).deviceId());

        StateSnapshot snapshotNotTriggering = StateSnapshot.empty().with("smoke-1", "smoke", "CLEAR");
        List<TriggeredRule> notTriggered = evaluator.findTriggered(List.of(rule), snapshotNotTriggering);
        assertTrue(notTriggered.isEmpty());
    }

    @Test
    @DisplayName("Disabled rules are never triggered even if conditions evaluate to true")
    void testDisabledRulesIgnored() {
        Rule disabledRule = new Rule(
                "R_DISABLED",
                "Disabled Smoke Alert",
                false, // disabled!
                PriorityTier.SAFETY,
                1,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );

        StateSnapshot snapshot = StateSnapshot.empty().with("smoke-1", "smoke", "DETECTED");
        List<TriggeredRule> triggered = evaluator.findTriggered(List.of(disabledRule), snapshot);
        assertTrue(triggered.isEmpty(), "Disabled rules must not be triggered");
    }

    @Test
    @DisplayName("Nested condition groups evaluate correctly")
    void testNestedConditionGroups() {
        // (motion == DETECTED AND light == OFF) OR temperature > 30
        ConditionGroup andGroup = new ConditionGroup(
                LogicalOperator.AND,
                List.of(
                        new LeafCondition("motion-1", "motion", Operator.EQ, "DETECTED"),
                        new LeafCondition("light-1", "power", Operator.EQ, "OFF")
                )
        );

        ConditionGroup rootGroup = new ConditionGroup(
                LogicalOperator.OR,
                List.of(
                        andGroup,
                        new LeafCondition("thermostat-1", "temperature", Operator.GT, "30.0")
                )
        );

        Rule rule = new Rule(
                "R_COMPLEX",
                "Complex Comfort",
                true,
                PriorityTier.COMFORT,
                0,
                rootGroup,
                List.of(new Action("light-1", "power", "ON")),
                Instant.now()
        );

        // Case 1: AND branch true
        StateSnapshot snap1 = StateSnapshot.empty()
                .with("motion-1", "motion", "DETECTED")
                .with("light-1", "power", "OFF")
                .with("thermostat-1", "temperature", "20.0");
        assertEquals(1, evaluator.findTriggered(List.of(rule), snap1).size());

        // Case 2: OR branch true (temperature > 30)
        StateSnapshot snap2 = StateSnapshot.empty()
                .with("motion-1", "motion", "NONE")
                .with("light-1", "power", "ON")
                .with("thermostat-1", "temperature", "31.5");
        assertEquals(1, evaluator.findTriggered(List.of(rule), snap2).size());

        // Case 3: Neither branch true
        StateSnapshot snap3 = StateSnapshot.empty()
                .with("motion-1", "motion", "NONE")
                .with("light-1", "power", "ON")
                .with("thermostat-1", "temperature", "25.0");
        assertTrue(evaluator.findTriggered(List.of(rule), snap3).isEmpty());
    }
}

