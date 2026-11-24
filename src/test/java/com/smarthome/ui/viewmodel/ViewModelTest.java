package com.smarthome.ui.viewmodel;

import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewModelTest {

    @Test
    @DisplayName("DeviceCardViewModel tracks properties and formats primary state correctly")
    void testDeviceCardViewModel() {
        Device light = new Device("light-1", "Living Light", DeviceType.LIGHT, "Living Room");
        DeviceCardViewModel vm = new DeviceCardViewModel(light, Map.of("power", "OFF"));

        assertEquals("light-1", vm.getId());
        assertEquals("Living Light", vm.getName());
        assertEquals(DeviceType.LIGHT, vm.getType());
        assertEquals("Living Room", vm.getRoom());
        assertEquals("OFF", vm.getPrimaryState());

        vm.updateState("power", "ON");
        assertEquals("ON", vm.getPrimaryState());
        assertEquals("ON", vm.getState("power"));
    }

    @Test
    @DisplayName("RuleItemViewModel binds to Rule entity and generates read/write summaries")
    void testRuleItemViewModel() {
        Rule rule = new Rule(
                "R1",
                "Smoke Trigger",
                true,
                PriorityTier.SAFETY,
                1,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );

        RuleItemViewModel vm = new RuleItemViewModel(rule);
        assertEquals("R1", vm.getId());
        assertEquals("Smoke Trigger", vm.getName());
        assertEquals(PriorityTier.SAFETY, vm.getTier());
        assertEquals(1, vm.getTieBreakOrder());
        assertTrue(vm.isEnabled());
        assertEquals("Never", vm.getLastFired());
        assertEquals("smoke-1.smoke", vm.getReadSetSummary());
        assertEquals("siren-1.alarm", vm.getWriteSetSummary());

        vm.updateLastFired("14:30:00");
        assertEquals("14:30:00", vm.getLastFired());
    }

    @Test
    @DisplayName("ActivityLogItemViewModel formats timestamps, preserves raw timestamp, and sorts chronologically")
    void testActivityLogItemViewModel() {
        Instant t1 = Instant.parse("2026-11-25T10:00:00.100Z");
        Instant t2 = Instant.parse("2026-11-25T10:00:00.200Z");

        ActivityLogItemViewModel log1 = new ActivityLogItemViewModel(t1, "OVERRIDE", "Safety overrode Comfort", "Details 1");
        ActivityLogItemViewModel log2 = new ActivityLogItemViewModel(t2, "RULE_FIRED", "Rule Fired", "Details 2");
        ActivityLogItemViewModel log3 = new ActivityLogItemViewModel(t1, "STATE_CHANGE", "State Changed", "Details 3");

        assertNotNull(log1.getTimestamp());
        assertEquals("OVERRIDE", log1.getType());
        assertEquals("Safety overrode Comfort", log1.getSummary());
        assertEquals("Details 1", log1.getDetails());
        assertEquals(t1, log1.getRawTimestamp());

        // Chronological ordering
        assertTrue(log1.compareTo(log2) < 0, "Earlier timestamp must sort before later timestamp");
        assertTrue(log2.compareTo(log1) > 0, "Later timestamp must sort after earlier timestamp");

        // Equal timestamp ordering determined by sequence arrival
        assertTrue(log1.compareTo(log3) < 0, "Same timestamp must preserve sequence arrival order");
    }
}
