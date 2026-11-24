package com.smarthome.ui;

import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.DeviceType;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.service.DeviceControlService;
import com.smarthome.service.RuleValidationService;
import com.smarthome.service.monitor.RuleCache;
import com.smarthome.service.monitor.RuleMonitorService;
import com.smarthome.ui.viewmodel.ActivityLogItemViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MainPresenterTest {

    private DeviceControlService deviceControlService;
    private RuleValidationService ruleValidationService;
    private RuleMonitorService ruleMonitorService;
    private RuleCache ruleCache;
    private MainPresenter presenter;

    private Rule ruleSafety;
    private Rule ruleComfort;

    @BeforeEach
    void setUp() {
        deviceControlService = Mockito.mock(DeviceControlService.class);
        ruleValidationService = Mockito.mock(RuleValidationService.class);
        ruleMonitorService = Mockito.mock(RuleMonitorService.class);
        ruleCache = new RuleCache();

        when(deviceControlService.getAllDevices()).thenReturn(List.of(
                new Device("therm-1", "Living Room Thermostat", DeviceType.THERMOSTAT, "Living Room")
        ));
        when(deviceControlService.getAllStates()).thenReturn(Map.of(
                new DeviceStateKey("therm-1", "temperature"), "21.0"
        ));

        ruleSafety = new Rule(
                "R_SAFETY_ID_101",
                "Freeze Protection",
                true,
                PriorityTier.SAFETY,
                0,
                new LeafCondition("therm-1", "temperature", Operator.LT, "5.0"),
                List.of(new Action("therm-1", "target_temp", "18.0")),
                Instant.now()
        );

        ruleComfort = new Rule(
                "R_COMFORT_ID_202",
                "Comfort Heating",
                true,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("therm-1", "temperature", Operator.LT, "20.0"),
                List.of(new Action("therm-1", "target_temp", "22.0")),
                Instant.now()
        );

        when(ruleValidationService.findAllRules()).thenReturn(List.of(ruleSafety, ruleComfort));
        when(ruleValidationService.findRuleById("R_SAFETY_ID_101")).thenReturn(Optional.of(ruleSafety));
        when(ruleValidationService.findRuleById("R_COMFORT_ID_202")).thenReturn(Optional.of(ruleComfort));
        when(ruleMonitorService.getRuleCache()).thenReturn(ruleCache);
        when(ruleMonitorService.getMonitorIntervalMs()).thenReturn(2000L);

        ruleCache.updateRule(ruleSafety);
        ruleCache.updateRule(ruleComfort);

        presenter = new MainPresenter(deviceControlService, ruleValidationService, ruleMonitorService);
    }

    @Test
    @DisplayName("resolveRuleName correctly resolves rule name from in-memory rule map and cache")
    void testResolveRuleName() {
        assertEquals("Freeze Protection", presenter.resolveRuleName("R_SAFETY_ID_101"));
        assertEquals("Comfort Heating", presenter.resolveRuleName("R_COMFORT_ID_202"));

        // Fallback to raw ID when rule is unknown
        assertEquals("UNKNOWN_RULE_999", presenter.resolveRuleName("UNKNOWN_RULE_999"));
        assertEquals("", presenter.resolveRuleName(""));
        assertEquals(null, presenter.resolveRuleName(null));
    }

    @Test
    @DisplayName("onOverride shows human-readable rule names in summary and details while keeping IDs intact")
    void testOnOverrideShowsRuleNames() {
        OverrideRecord overrideRecord = new OverrideRecord(
                "override-uuid-1",
                "R_SAFETY_ID_101",
                "R_COMFORT_ID_202",
                PriorityTier.SAFETY,
                PriorityTier.COMFORT,
                new DeviceStateKey("therm-1", "target_temp"),
                "22.0",
                "18.0",
                Instant.now(),
                "Priority tier override: Rule 'R_SAFETY_ID_101' (SAFETY) prevailed over Rule 'R_COMFORT_ID_202' (COMFORT) on therm-1.target_temp (applied: '18.0', dropped: '22.0')"
        );

        // Verify underlying domain record IDs are intact
        assertEquals("R_SAFETY_ID_101", overrideRecord.winningRuleId());
        assertEquals("R_COMFORT_ID_202", overrideRecord.overriddenRuleId());

        presenter.onOverride(overrideRecord);

        // Retrieve the latest logged activity item
        List<ActivityLogItemViewModel> logs = presenter.getActivityLogs();
        ActivityLogItemViewModel overrideLog = logs.get(logs.size() - 1);

        assertEquals("OVERRIDE", overrideLog.getType());

        // Check human-readable rule names in summary text
        assertTrue(overrideLog.getSummary().contains("Freeze Protection"), "Summary must display winning rule name");
        assertTrue(overrideLog.getSummary().contains("Comfort Heating"), "Summary must display overridden rule name");
        assertFalse(overrideLog.getSummary().contains("R_SAFETY_ID_101"), "Summary must not display raw winning rule ID");
        assertFalse(overrideLog.getSummary().contains("R_COMFORT_ID_202"), "Summary must not display raw overridden rule ID");

        // Check details text
        assertTrue(overrideLog.getDetails().contains("Applied: '18.0'"));
        assertTrue(overrideLog.getDetails().contains("Dropped: '22.0'"));
        assertTrue(overrideLog.getDetails().contains("Freeze Protection"));
        assertTrue(overrideLog.getDetails().contains("Comfort Heating"));
    }

    @Test
    @DisplayName("onOverride falls back cleanly to raw rule ID when rule is not found in lookup")
    void testOnOverrideFallbackToIdWhenUnknown() {
        OverrideRecord unknownOverride = new OverrideRecord(
                "override-uuid-2",
                "R_UNKNOWN_WINNER",
                "R_UNKNOWN_LOSER",
                PriorityTier.SAFETY,
                PriorityTier.COMFORT,
                new DeviceStateKey("plug-1", "power"),
                "ON",
                "OFF",
                Instant.now(),
                "Priority tier override"
        );

        presenter.onOverride(unknownOverride);

        List<ActivityLogItemViewModel> logs = presenter.getActivityLogs();
        ActivityLogItemViewModel log = logs.get(logs.size() - 1);

        assertEquals("OVERRIDE", log.getType());
        assertTrue(log.getSummary().contains("R_UNKNOWN_WINNER"));
        assertTrue(log.getSummary().contains("R_UNKNOWN_LOSER"));
    }

    @Test
    @DisplayName("Activity log items are strictly maintained in ascending timestamp order")
    void testActivityLogsAscendingTimestampOrder() {
        presenter.getActivityLogs().clear();

        Instant t1 = Instant.parse("2026-11-25T10:00:01.000Z");
        Instant t2 = Instant.parse("2026-11-25T10:00:02.000Z");
        Instant t3 = Instant.parse("2026-11-25T10:00:03.000Z");
        Instant t4 = Instant.parse("2026-11-25T10:00:04.000Z");

        // Add items out of order: t3, then t1, then t4, then t2
        presenter.addLogEntry(new ActivityLogItemViewModel(t3, "INFO", "Event at t3", ""));
        presenter.addLogEntry(new ActivityLogItemViewModel(t1, "INFO", "Event at t1", ""));
        presenter.addLogEntry(new ActivityLogItemViewModel(t4, "INFO", "Event at t4", ""));
        presenter.addLogEntry(new ActivityLogItemViewModel(t2, "INFO", "Event at t2", ""));

        List<ActivityLogItemViewModel> logs = presenter.getActivityLogs();
        assertEquals(4, logs.size());

        // Verify strictly ascending order
        assertEquals(t1, logs.get(0).getRawTimestamp());
        assertEquals("Event at t1", logs.get(0).getSummary());

        assertEquals(t2, logs.get(1).getRawTimestamp());
        assertEquals("Event at t2", logs.get(1).getSummary());

        assertEquals(t3, logs.get(2).getRawTimestamp());
        assertEquals("Event at t3", logs.get(2).getSummary());

        assertEquals(t4, logs.get(3).getRawTimestamp());
        assertEquals("Event at t4", logs.get(3).getSummary());
    }
}

