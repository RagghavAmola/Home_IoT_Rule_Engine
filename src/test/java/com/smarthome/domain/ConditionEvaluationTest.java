package com.smarthome.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for condition evaluation against device state snapshots,
 * verifying numeric vs string comparisons, null/missing telemetry, and nested AND/OR condition hierarchies.
 */
class ConditionEvaluationTest {

    private static final String DEV_THERMOSTAT = "thermostat-1";
    private static final String DEV_MOTION = "motion-sensor-1";
    private static final String DEV_LIGHT = "light-living-room";
    private static final String DEV_DOOR = "front-door";
    private static final String DEV_SMOKE = "smoke-detector-1";

    @Test
    @DisplayName("Numeric comparisons: EQ and NEQ with integer and floating point representations")
    void testNumericEquality() {
        DeviceStateKey tempKey = new DeviceStateKey(DEV_THERMOSTAT, "temperature");
        Map<DeviceStateKey, String> snapshot = Map.of(tempKey, "22.0");

        LeafCondition condEqExact = new LeafCondition(DEV_THERMOSTAT, "temperature", Operator.EQ, "22.0");
        LeafCondition condEqInt = new LeafCondition(DEV_THERMOSTAT, "temperature", Operator.EQ, "22");
        LeafCondition condEqDiff = new LeafCondition(DEV_THERMOSTAT, "temperature", Operator.EQ, "22.5");
        LeafCondition condNeq = new LeafCondition(DEV_THERMOSTAT, "temperature", Operator.NEQ, "25");

        assertTrue(condEqExact.evaluate(snapshot), "22.0 should numerically equal 22.0");
        assertTrue(condEqInt.evaluate(snapshot), "22.0 should numerically equal 22");
        assertFalse(condEqDiff.evaluate(snapshot), "22.0 should not equal 22.5");
        assertTrue(condNeq.evaluate(snapshot), "22.0 should not equal 25");
    }

    @ParameterizedTest(name = "Operator {0}: actual={1}, expected={2} -> expectedResult={3}")
    @CsvSource({
            "GT, 25.5, 20.0, true",
            "GT, 20.0, 25.5, false",
            "GT, 20.0, 20.0, false",
            "GT, 10, 9, true",            // Crucial: 10 > 9 numerically (lexicographically "10" < "9")
            "LT, 18.5, 20.0, true",
            "LT, 20.0, 18.5, false",
            "LT, 20.0, 20.0, false",
            "GTE, 20.0, 20.0, true",
            "GTE, 21.0, 20.0, true",
            "GTE, 19.9, 20.0, false",
            "LTE, 20.0, 20.0, true",
            "LTE, 19.9, 20.0, true",
            "LTE, 20.1, 20.0, false",
            "LT, -5.0, 0.0, true",
            "GT, 0.0, -5.0, true"
    })
    @DisplayName("Numeric relational operators: GT, LT, GTE, LTE")
    void testNumericRelationalOperators(Operator operator, String actual, String expected, boolean expectedResult) {
        DeviceStateKey key = new DeviceStateKey(DEV_THERMOSTAT, "temp");
        Map<DeviceStateKey, String> snapshot = Map.of(key, actual);
        LeafCondition condition = new LeafCondition(DEV_THERMOSTAT, "temp", operator, expected);

        assertEquals(expectedResult, condition.evaluate(snapshot));
    }

    @Test
    @DisplayName("String comparisons: case-insensitive EQ/NEQ and string ordering")
    void testStringComparisons() {
        DeviceStateKey powerKey = new DeviceStateKey(DEV_LIGHT, "power");
        DeviceStateKey modeKey = new DeviceStateKey(DEV_THERMOSTAT, "mode");
        Map<DeviceStateKey, String> snapshot = Map.of(
                powerKey, "ON",
                modeKey, "HEAT"
        );

        LeafCondition eqExact = new LeafCondition(DEV_LIGHT, "power", Operator.EQ, "ON");
        LeafCondition eqCase = new LeafCondition(DEV_LIGHT, "power", Operator.EQ, "on");
        LeafCondition neqDiff = new LeafCondition(DEV_LIGHT, "power", Operator.NEQ, "OFF");
        LeafCondition neqSame = new LeafCondition(DEV_LIGHT, "power", Operator.NEQ, "ON");

        assertTrue(eqExact.evaluate(snapshot));
        assertTrue(eqCase.evaluate(snapshot), "EQ should be case-insensitive for strings");
        assertTrue(neqDiff.evaluate(snapshot));
        assertFalse(neqSame.evaluate(snapshot));

        LeafCondition stringGt = new LeafCondition(DEV_THERMOSTAT, "mode", Operator.GT, "COOL");
        LeafCondition stringLt = new LeafCondition(DEV_THERMOSTAT, "mode", Operator.LT, "VENT");
        assertTrue(stringGt.evaluate(snapshot), "'HEAT' is lexicographically greater than 'COOL'");
        assertTrue(stringLt.evaluate(snapshot), "'HEAT' is lexicographically less than 'VENT'");
    }

    @Test
    @DisplayName("Missing state in snapshot evaluates safely")
    void testMissingStateHandling() {
        Map<DeviceStateKey, String> emptySnapshot = Map.of();
        LeafCondition eqCond = new LeafCondition(DEV_LIGHT, "power", Operator.EQ, "ON");
        LeafCondition neqCond = new LeafCondition(DEV_LIGHT, "power", Operator.NEQ, "ON");
        LeafCondition gtCond = new LeafCondition(DEV_THERMOSTAT, "temp", Operator.GT, "20");

        assertFalse(eqCond.evaluate(emptySnapshot), "Missing state key should evaluate to false for EQ");
        assertTrue(neqCond.evaluate(emptySnapshot), "Missing state key should evaluate to true for NEQ");
        assertFalse(gtCond.evaluate(emptySnapshot), "Missing state key cannot satisfy GT");
    }

    @Test
    @DisplayName("ConditionGroup AND: satisfies only when all children evaluate to true")
    void testConditionGroupAnd() {
        DeviceStateKey motionKey = new DeviceStateKey(DEV_MOTION, "motion");
        DeviceStateKey lightKey = new DeviceStateKey(DEV_LIGHT, "brightness");

        Map<DeviceStateKey, String> snapshotBothMet = Map.of(
                motionKey, "DETECTED",
                lightKey, "30"
        );
        Map<DeviceStateKey, String> snapshotOneFailed = Map.of(
                motionKey, "DETECTED",
                lightKey, "80"
        );

        Condition cond1 = new LeafCondition(DEV_MOTION, "motion", Operator.EQ, "DETECTED");
        Condition cond2 = new LeafCondition(DEV_LIGHT, "brightness", Operator.LT, "50");
        ConditionGroup andGroup = new ConditionGroup(LogicalOperator.AND, List.of(cond1, cond2));

        assertTrue(andGroup.evaluate(snapshotBothMet));
        assertFalse(andGroup.evaluate(snapshotOneFailed));
    }

    @Test
    @DisplayName("ConditionGroup OR: satisfies when at least one child evaluates to true")
    void testConditionGroupOr() {
        DeviceStateKey doorKey = new DeviceStateKey(DEV_DOOR, "contact");
        DeviceStateKey smokeKey = new DeviceStateKey(DEV_SMOKE, "smoke");

        Map<DeviceStateKey, String> snapshotDoorOpen = Map.of(
                doorKey, "OPEN",
                smokeKey, "CLEAR"
        );
        Map<DeviceStateKey, String> snapshotSmokeDetected = Map.of(
                doorKey, "CLOSED",
                smokeKey, "DETECTED"
        );
        Map<DeviceStateKey, String> snapshotNeither = Map.of(
                doorKey, "CLOSED",
                smokeKey, "CLEAR"
        );

        Condition condDoor = new LeafCondition(DEV_DOOR, "contact", Operator.EQ, "OPEN");
        Condition condSmoke = new LeafCondition(DEV_SMOKE, "smoke", Operator.EQ, "DETECTED");
        ConditionGroup orGroup = new ConditionGroup(LogicalOperator.OR, List.of(condDoor, condSmoke));

        assertTrue(orGroup.evaluate(snapshotDoorOpen));
        assertTrue(orGroup.evaluate(snapshotSmokeDetected));
        assertFalse(orGroup.evaluate(snapshotNeither));
    }

    @Test
    @DisplayName("Nested hierarchical condition: (motion AND dark) OR (door == open AND evening)")
    void testNestedConditionHierarchy() {
        DeviceStateKey motionKey = new DeviceStateKey(DEV_MOTION, "motion");
        DeviceStateKey ambientKey = new DeviceStateKey(DEV_LIGHT, "ambient_lux");
        DeviceStateKey doorKey = new DeviceStateKey(DEV_DOOR, "status");
        DeviceStateKey timeKey = new DeviceStateKey("clock-1", "phase");

        Condition branchA = new ConditionGroup(LogicalOperator.AND, List.of(
                new LeafCondition(DEV_MOTION, "motion", Operator.EQ, "DETECTED"),
                new LeafCondition(DEV_LIGHT, "ambient_lux", Operator.LT, "100")
        ));

        Condition branchB = new ConditionGroup(LogicalOperator.AND, List.of(
                new LeafCondition(DEV_DOOR, "status", Operator.EQ, "OPEN"),
                new LeafCondition("clock-1", "phase", Operator.EQ, "EVENING")
        ));

        Condition rootCondition = new ConditionGroup(LogicalOperator.OR, List.of(branchA, branchB));

        // Test Branch A true, Branch B false
        Map<DeviceStateKey, String> snapA = Map.of(
                motionKey, "DETECTED",
                ambientKey, "45",
                doorKey, "CLOSED",
                timeKey, "DAY"
        );
        assertTrue(rootCondition.evaluate(snapA));

        // Test Branch A false, Branch B true
        Map<DeviceStateKey, String> snapB = Map.of(
                motionKey, "NONE",
                ambientKey, "300",
                doorKey, "OPEN",
                timeKey, "EVENING"
        );
        assertTrue(rootCondition.evaluate(snapB));

        // Test both false
        Map<DeviceStateKey, String> snapNone = Map.of(
                motionKey, "NONE",
                ambientKey, "300",
                doorKey, "CLOSED",
                timeKey, "DAY"
        );
        assertFalse(rootCondition.evaluate(snapNone));
    }

    @Test
    @DisplayName("getReferencedKeys gathers all DeviceStateKey pairs across nested conditions")
    void testGetReferencedKeysRecursive() {
        Condition c1 = new LeafCondition(DEV_MOTION, "motion", Operator.EQ, "DETECTED");
        Condition c2 = new LeafCondition(DEV_LIGHT, "lux", Operator.LT, "100");
        Condition c3 = new LeafCondition(DEV_DOOR, "status", Operator.EQ, "OPEN");

        Condition nested = new ConditionGroup(LogicalOperator.OR, List.of(
                new ConditionGroup(LogicalOperator.AND, List.of(c1, c2)),
                c3
        ));

        Set<DeviceStateKey> referenced = nested.getReferencedKeys();
        assertEquals(3, referenced.size());
        assertTrue(referenced.contains(new DeviceStateKey(DEV_MOTION, "motion")));
        assertTrue(referenced.contains(new DeviceStateKey(DEV_LIGHT, "lux")));
        assertTrue(referenced.contains(new DeviceStateKey(DEV_DOOR, "status")));
    }

    @Test
    @DisplayName("Validation prevents invalid arguments")
    void testValidation() {
        assertThrows(NullPointerException.class, () -> new LeafCondition(null, "temp", Operator.EQ, "20"));
        assertThrows(IllegalArgumentException.class, () -> new LeafCondition("   ", "temp", Operator.EQ, "20"));
        assertThrows(NullPointerException.class, () -> new LeafCondition("dev-1", "temp", null, "20"));
        assertThrows(IllegalArgumentException.class, () -> new ConditionGroup(LogicalOperator.AND, List.of()));
    }
}
