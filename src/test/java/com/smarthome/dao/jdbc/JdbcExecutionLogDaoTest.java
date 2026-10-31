package com.smarthome.dao.jdbc;

import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.domain.RuleExecutionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExecutionLogDaoTest {

    private JdbcExecutionLogDao logDao;
    private JdbcRuleDao ruleDao;

    @BeforeEach
    void setUp() throws IOException {
        File tempDb = File.createTempFile("test-log-" + UUID.randomUUID(), ".db");
        tempDb.deleteOnExit();
        String url = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider(url, "", "");
        DatabaseInitializer initializer = new DatabaseInitializer(connectionProvider);
        initializer.initialize(true);

        logDao = new JdbcExecutionLogDao(connectionProvider);
        ruleDao = new JdbcRuleDao(connectionProvider);

        // Seed rules referenced by execution and override logs to satisfy FK constraints
        LeafCondition cond = new LeafCondition("smoke-detector-kitchen", "smoke", Operator.EQ, "CLEAR");
        Action act = new Action("siren-main", "alarm", "OFF");

        ruleDao.save(new Rule("rule-smoke-alarm", "Smoke Alarm Rule", true, PriorityTier.SAFETY, 0, cond, List.of(act)));
        ruleDao.save(new Rule("rule-safety-lockdown", "Safety Lockdown", true, PriorityTier.SAFETY, 1, cond, List.of(act)));
        ruleDao.save(new Rule("rule-comfort-vent", "Comfort Vent", true, PriorityTier.COMFORT, 2, cond, List.of(act)));
        ruleDao.save(new Rule("rule-1", "Sample Rule", true, PriorityTier.ENERGY, 3, cond, List.of(act)));
    }

    @Test
    @DisplayName("Save and retrieve rule execution log records")
    void testExecutionRecordLogging() {
        Instant now = Instant.now();
        RuleExecutionRecord record1 = new RuleExecutionRecord(
                "exec-1",
                "rule-smoke-alarm",
                "Smoke Alarm Rule",
                PriorityTier.SAFETY,
                now,
                true,
                Collections.emptyList(),
                Collections.emptyMap(),
                "Smoke detected in kitchen"
        );

        logDao.saveExecutionRecord(record1);

        List<RuleExecutionRecord> byRule = logDao.findExecutionRecordsByRuleId("rule-smoke-alarm", 10);
        assertEquals(1, byRule.size());
        assertEquals("exec-1", byRule.get(0).id());
        assertEquals("rule-smoke-alarm", byRule.get(0).ruleId());
        assertEquals("Smoke Alarm Rule", byRule.get(0).ruleName());
        assertEquals(PriorityTier.SAFETY, byRule.get(0).tier());
        assertTrue(byRule.get(0).conditionSatisfied());
        assertEquals("Smoke detected in kitchen", byRule.get(0).message());

        List<RuleExecutionRecord> recent = logDao.findRecentExecutionRecords(5);
        assertFalse(recent.isEmpty());
    }

    @Test
    @DisplayName("Save and retrieve priority tier override log records")
    void testOverrideRecordLogging() {
        Instant now = Instant.now();
        OverrideRecord override = new OverrideRecord(
                "over-1",
                "rule-safety-lockdown",
                "rule-comfort-vent",
                PriorityTier.SAFETY,
                PriorityTier.COMFORT,
                new DeviceStateKey("window-bedroom", "status"),
                "OPEN",
                "CLOSED",
                now,
                "Safety lockdown overrides comfort ventilation"
        );

        logDao.saveOverrideRecord(override);

        List<OverrideRecord> recent = logDao.findRecentOverrideRecords(10);
        assertEquals(1, recent.size());
        OverrideRecord loaded = recent.get(0);
        assertEquals("over-1", loaded.id());
        assertEquals("rule-safety-lockdown", loaded.winningRuleId());
        assertEquals("rule-comfort-vent", loaded.overriddenRuleId());
        assertEquals(PriorityTier.SAFETY, loaded.winningTier());
        assertEquals(PriorityTier.COMFORT, loaded.overriddenTier());
        assertEquals("window-bedroom", loaded.targetKey().deviceId());
        assertEquals("status", loaded.targetKey().stateKey());
        assertEquals("OPEN", loaded.overriddenTargetValue());
        assertEquals("CLOSED", loaded.appliedTargetValue());
        assertEquals("Safety lockdown overrides comfort ventilation", loaded.reason());
    }

    @Test
    @DisplayName("Clear audit logs older than threshold")
    void testClearOldLogs() {
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant newTime = Instant.now();

        RuleExecutionRecord oldRecord = new RuleExecutionRecord(
                "old-exec", "rule-1", "Rule 1", PriorityTier.COMFORT, oldTime, true,
                Collections.emptyList(), Collections.emptyMap(), "old"
        );
        RuleExecutionRecord newRecord = new RuleExecutionRecord(
                "new-exec", "rule-1", "Rule 1", PriorityTier.COMFORT, newTime, true,
                Collections.emptyList(), Collections.emptyMap(), "new"
        );

        logDao.saveExecutionRecord(oldRecord);
        logDao.saveExecutionRecord(newRecord);

        int cleared = logDao.clearLogsOlderThan(Instant.now().minus(5, ChronoUnit.DAYS));
        assertEquals(1, cleared);

        List<RuleExecutionRecord> remaining = logDao.findExecutionRecordsByRuleId("rule-1", 10);
        assertEquals(1, remaining.size());
        assertEquals("new-exec", remaining.get(0).id());
    }
}

