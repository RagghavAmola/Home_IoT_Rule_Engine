package com.smarthome.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests domain entities, immutability, validation, and helper methods.
 */
class DomainEntitiesTest {

    @Test
    @DisplayName("Device: validation, immutability, and ID-based equality")
    void testDeviceEntity() {
        Device d1 = new Device("dev-1", "Living Room Lamp", DeviceType.LIGHT, "Living Room", Map.of("power", "OFF"));

        assertEquals("dev-1", d1.getId());
        assertEquals("Living Room Lamp", d1.getName());
        assertEquals(DeviceType.LIGHT, d1.getType());
        assertEquals("Living Room", d1.getRoom());
        assertEquals("OFF", d1.getStateValue("power"));

        // State immutability
        assertThrows(UnsupportedOperationException.class, () -> d1.getState().put("power", "ON"));

        // withStateValue creates new instance without mutating original
        Device d2 = d1.withStateValue("power", "ON");
        assertEquals("OFF", d1.getStateValue("power"), "Original device state must remain unchanged");
        assertEquals("ON", d2.getStateValue("power"));

        // ID-based equality: same ID, different state/name -> equals must be true
        Device d3 = new Device("dev-1", "Renamed Lamp", DeviceType.LIGHT, "Den", Map.of("power", "ON"));
        assertEquals(d1, d3);
        assertEquals(d1.hashCode(), d3.hashCode());

        Device dOther = new Device("dev-2", "Living Room Lamp", DeviceType.LIGHT, "Living Room");
        assertNotEquals(d1, dOther);

        // Validation checks
        assertThrows(NullPointerException.class, () -> new Device(null, "Lamp", DeviceType.LIGHT, "Room"));
        assertThrows(IllegalArgumentException.class, () -> new Device("   ", "Lamp", DeviceType.LIGHT, "Room"));
        assertThrows(IllegalArgumentException.class, () -> new Device("d1", "   ", DeviceType.LIGHT, "Room"));
        assertThrows(NullPointerException.class, () -> new Device("d1", "Lamp", null, "Room"));
        assertThrows(IllegalArgumentException.class, () -> new Device("d1", "Lamp", DeviceType.LIGHT, "   "));
    }

    @Test
    @DisplayName("PriorityTier: ordering, level, and isHigherThan/isLowerThan helpers")
    void testPriorityTierOrdering() {
        assertEquals(0, PriorityTier.SAFETY.getLevel());
        assertEquals(1, PriorityTier.COMFORT.getLevel());
        assertEquals(2, PriorityTier.ENERGY.getLevel());

        // SAFETY is strictly higher priority than COMFORT and ENERGY
        assertTrue(PriorityTier.SAFETY.isHigherThan(PriorityTier.COMFORT));
        assertTrue(PriorityTier.SAFETY.isHigherThan(PriorityTier.ENERGY));
        assertTrue(PriorityTier.COMFORT.isHigherThan(PriorityTier.ENERGY));

        // False comparisons
        assertFalse(PriorityTier.COMFORT.isHigherThan(PriorityTier.SAFETY));
        assertFalse(PriorityTier.ENERGY.isHigherThan(PriorityTier.COMFORT));
        assertFalse(PriorityTier.SAFETY.isHigherThan(PriorityTier.SAFETY));

        // isLowerThan
        assertTrue(PriorityTier.ENERGY.isLowerThan(PriorityTier.COMFORT));
        assertTrue(PriorityTier.COMFORT.isLowerThan(PriorityTier.SAFETY));
        assertFalse(PriorityTier.SAFETY.isLowerThan(PriorityTier.COMFORT));

        assertThrows(NullPointerException.class, () -> PriorityTier.SAFETY.isHigherThan(null));
    }

    @Test
    @DisplayName("DeviceStateKey: record equality, normalization, and validation")
    void testDeviceStateKey() {
        DeviceStateKey key1 = new DeviceStateKey("dev-1", "power");
        DeviceStateKey key2 = new DeviceStateKey(" dev-1 ", " power ");
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertEquals("dev-1.power", key1.toString());

        assertThrows(NullPointerException.class, () -> new DeviceStateKey(null, "power"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceStateKey("   ", "power"));
        assertThrows(NullPointerException.class, () -> new DeviceStateKey("dev-1", null));
        assertThrows(IllegalArgumentException.class, () -> new DeviceStateKey("dev-1", "  "));
    }

    @Test
    @DisplayName("Action: validation and target state key resolution")
    void testAction() {
        Action action = new Action("thermostat-1", "temperature", "22");
        assertEquals("thermostat-1", action.deviceId());
        assertEquals("temperature", action.stateKey());
        assertEquals("22", action.targetValue());
        assertEquals(new DeviceStateKey("thermostat-1", "temperature"), action.getTargetStateKey());

        assertThrows(NullPointerException.class, () -> new Action(null, "power", "ON"));
        assertThrows(IllegalArgumentException.class, () -> new Action("   ", "power", "ON"));
        assertThrows(IllegalArgumentException.class, () -> new Action("d1", "   ", "ON"));
        assertThrows(NullPointerException.class, () -> new Action("d1", "power", null));
    }

    @Test
    @DisplayName("Rule: getReadSet, getWriteSet, validation, and ID-based equality")
    void testRuleEntity() {
        Condition cond1 = new LeafCondition("sensor-1", "temp", Operator.GT, "30");
        Condition cond2 = new LeafCondition("sensor-2", "humidity", Operator.GT, "70");
        Condition root = new ConditionGroup(LogicalOperator.AND, List.of(cond1, cond2));

        Action a1 = new Action("fan-1", "power", "ON");
        Action a2 = new Action("ac-1", "mode", "COOL");

        Instant now = Instant.now();
        Rule rule1 = new Rule("rule-1", "Overheat Protection", true, PriorityTier.SAFETY, 10, root, List.of(a1, a2), now);

        assertEquals("rule-1", rule1.getId());
        assertEquals("Overheat Protection", rule1.getName());
        assertTrue(rule1.isEnabled());
        assertEquals(PriorityTier.SAFETY, rule1.getTier());
        assertEquals(10, rule1.getTieBreakOrder());
        assertEquals(now, rule1.getCreatedAt());

        // Read set resolution
        Set<DeviceStateKey> readSet = rule1.getReadSet();
        assertEquals(2, readSet.size());
        assertTrue(readSet.contains(new DeviceStateKey("sensor-1", "temp")));
        assertTrue(readSet.contains(new DeviceStateKey("sensor-2", "humidity")));

        // Write set resolution
        Set<DeviceStateKey> writeSet = rule1.getWriteSet();
        assertEquals(2, writeSet.size());
        assertTrue(writeSet.contains(new DeviceStateKey("fan-1", "power")));
        assertTrue(writeSet.contains(new DeviceStateKey("ac-1", "mode")));

        // withEnabled produces new instance
        Rule disabledRule = rule1.withEnabled(false);
        assertFalse(disabledRule.isEnabled());
        assertTrue(rule1.isEnabled());

        // Equality strictly on ID
        Rule rule1Copy = new Rule("rule-1", "Different Name", false, PriorityTier.ENERGY, 99, cond1, List.of(a1));
        assertEquals(rule1, rule1Copy);
        assertEquals(rule1.hashCode(), rule1Copy.hashCode());

        // Validation
        assertThrows(NullPointerException.class, () -> new Rule(null, "Name", true, PriorityTier.SAFETY, 0, root, List.of(a1)));
        assertThrows(IllegalArgumentException.class, () -> new Rule("   ", "Name", true, PriorityTier.SAFETY, 0, root, List.of(a1)));
        assertThrows(IllegalArgumentException.class, () -> new Rule("r1", "  ", true, PriorityTier.SAFETY, 0, root, List.of(a1)));
        assertThrows(NullPointerException.class, () -> new Rule("r1", "Name", true, null, 0, root, List.of(a1)));
        assertThrows(NullPointerException.class, () -> new Rule("r1", "Name", true, PriorityTier.SAFETY, 0, null, List.of(a1)));
        assertThrows(IllegalArgumentException.class, () -> new Rule("r1", "Name", true, PriorityTier.SAFETY, 0, root, List.of()));
    }

    @Test
    @DisplayName("Audit records: RuleExecutionRecord and OverrideRecord construction and immutability")
    void testAuditRecords() {
        Instant now = Instant.now();
        Action action = new Action("siren-1", "alarm", "ON");
        DeviceStateKey stateKey = new DeviceStateKey("smoke-1", "smoke");

        RuleExecutionRecord execRecord = new RuleExecutionRecord(
                "exec-1",
                "rule-smoke-alarm",
                "Smoke Alarm Rule",
                PriorityTier.SAFETY,
                now,
                true,
                List.of(action),
                Map.of(stateKey, "DETECTED"),
                "Fire condition triggered"
        );

        assertEquals("exec-1", execRecord.id());
        assertEquals("rule-smoke-alarm", execRecord.ruleId());
        assertEquals(PriorityTier.SAFETY, execRecord.tier());
        assertTrue(execRecord.conditionSatisfied());
        assertEquals(1, execRecord.triggeredActions().size());
        assertEquals("DETECTED", execRecord.triggeredStateSnapshot().get(stateKey));

        OverrideRecord overrideRecord = new OverrideRecord(
                "ov-1",
                "rule-safety-lockdown",
                "rule-comfort-vent",
                PriorityTier.SAFETY,
                PriorityTier.COMFORT,
                new DeviceStateKey("window-1", "status"),
                "OPEN",
                "CLOSED",
                now,
                "Safety lockdown overrides comfort ventilation"
        );

        assertEquals("ov-1", overrideRecord.id());
        assertEquals("rule-safety-lockdown", overrideRecord.winningRuleId());
        assertEquals("rule-comfort-vent", overrideRecord.overriddenRuleId());
        assertEquals(PriorityTier.SAFETY, overrideRecord.winningTier());
        assertEquals(PriorityTier.COMFORT, overrideRecord.overriddenTier());
        assertEquals("CLOSED", overrideRecord.appliedTargetValue());
        assertEquals("OPEN", overrideRecord.overriddenTargetValue());
    }

    @Test
    void rejectsContradictoryActionsOnSameTarget() {
        Condition condition = new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED");
        List<Action> actions = List.of(
                new Action("thermostat-1", "temperature", "20"),
                new Action("thermostat-1", "temperature", "25"));

        assertThrows(IllegalArgumentException.class, () ->
                new Rule("rule-01", "Conflicting", true, PriorityTier.SAFETY, 0, condition, actions));
    }

    @Test
    void allowsIdenticalDuplicateActions() {
        Condition condition = new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED");
        List<Action> actions = List.of(
                new Action("thermostat-1", "temperature", "20"),
                new Action("thermostat-1", "temperature", "20"));

        assertDoesNotThrow(() ->
                new Rule("rule-01", "Duplicate", true, PriorityTier.SAFETY, 0, condition, actions));
    }
}
