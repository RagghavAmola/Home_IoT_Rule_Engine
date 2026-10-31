package com.smarthome.dao.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smarthome.domain.Action;
import com.smarthome.domain.Condition;
import com.smarthome.domain.ConditionGroup;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;

class JdbcRuleDaoTest {

    private JdbcConnectionProvider connectionProvider;
    private JdbcRuleDao ruleDao;

    @BeforeEach
    void setUp() throws IOException {
        File tempDb = File.createTempFile("test-rule-" + UUID.randomUUID(), ".db");
        tempDb.deleteOnExit();
        String url = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        connectionProvider = new JdbcConnectionProvider(url, "", "");
        DatabaseInitializer initializer = new DatabaseInitializer(connectionProvider);
        initializer.initialize(true);

        ruleDao = new JdbcRuleDao(connectionProvider);
    }

    @Test
    @DisplayName("Round-trip rule with nested condition groups (AND inside OR)")
    void testRoundTripNestedConditionRule() {
        // Construct nested condition tree:
        // (motion == DETECTED AND brightness < 30) OR (door == UNLOCKED AND smoke == DETECTED)
        LeafCondition cond1 = new LeafCondition("motion-sensor-hallway", "motion", Operator.EQ, "DETECTED");
        LeafCondition cond2 = new LeafCondition("light-living-room", "brightness", Operator.LT, "30");
        Condition group1 = new ConditionGroup(LogicalOperator.AND, List.of(cond1, cond2));

        LeafCondition cond3 = new LeafCondition("door-lock-front", "status", Operator.EQ, "UNLOCKED");
        LeafCondition cond4 = new LeafCondition("smoke-detector-kitchen", "smoke", Operator.EQ, "DETECTED");
        Condition group2 = new ConditionGroup(LogicalOperator.AND, List.of(cond3, cond4));

        Condition root = new ConditionGroup(LogicalOperator.OR, List.of(group1, group2));

        Action act1 = new Action("light-living-room", "power", "ON");
        Action act2 = new Action("siren-main", "alarm", "ON");

        Instant createdAt = Instant.parse("2026-10-31T12:00:00Z");
        Rule rule = new Rule("rule-complex-1", "Security & Safety Automation", true, PriorityTier.SAFETY, 5, root, List.of(act1, act2), createdAt);

        ruleDao.save(rule);

        Optional<Rule> loadedOpt = ruleDao.findById("rule-complex-1");
        assertTrue(loadedOpt.isPresent(), "Persisted rule must be found");

        Rule loaded = loadedOpt.get();
        assertEquals("rule-complex-1", loaded.getId());
        assertEquals("Security & Safety Automation", loaded.getName());
        assertTrue(loaded.isEnabled());
        assertEquals(PriorityTier.SAFETY, loaded.getTier());
        assertEquals(5, loaded.getTieBreakOrder());
        assertEquals(createdAt, loaded.getCreatedAt());

        // Verify actions
        assertEquals(2, loaded.getActions().size());
        assertEquals(act1, loaded.getActions().get(0));
        assertEquals(act2, loaded.getActions().get(1));

        // Verify readSet and writeSet
        assertEquals(rule.getReadSet(), loaded.getReadSet());
        assertEquals(rule.getWriteSet(), loaded.getWriteSet());

        // Verify condition evaluation equivalence against snapshots
        Map<DeviceStateKey, String> branch1Satisfied = Map.of(
                new DeviceStateKey("motion-sensor-hallway", "motion"), "DETECTED",
                new DeviceStateKey("light-living-room", "brightness"), "20",
                new DeviceStateKey("door-lock-front", "status"), "LOCKED",
                new DeviceStateKey("smoke-detector-kitchen", "smoke"), "CLEAR"
        );
        assertTrue(loaded.getRootCondition().evaluate(branch1Satisfied));

        Map<DeviceStateKey, String> branch2Satisfied = Map.of(
                new DeviceStateKey("motion-sensor-hallway", "motion"), "NONE",
                new DeviceStateKey("light-living-room", "brightness"), "50",
                new DeviceStateKey("door-lock-front", "status"), "UNLOCKED",
                new DeviceStateKey("smoke-detector-kitchen", "smoke"), "DETECTED"
        );
        assertTrue(loaded.getRootCondition().evaluate(branch2Satisfied));

        Map<DeviceStateKey, String> neitherSatisfied = Map.of(
                new DeviceStateKey("motion-sensor-hallway", "motion"), "NONE",
                new DeviceStateKey("light-living-room", "brightness"), "80",
                new DeviceStateKey("door-lock-front", "status"), "LOCKED",
                new DeviceStateKey("smoke-detector-kitchen", "smoke"), "CLEAR"
        );
        assertFalse(loaded.getRootCondition().evaluate(neitherSatisfied));
    }

    @Test
    @DisplayName("Round-trip rule with a standalone LeafCondition as root")
    void testRoundTripLeafConditionRule() {
        LeafCondition leaf = new LeafCondition("thermostat-living-room", "temperature", Operator.GT, "25.0");
        Action action = new Action("thermostat-living-room", "mode", "COOL");
        Rule rule = new Rule("rule-leaf-1", "Cooling Rule", true, PriorityTier.COMFORT, 10, leaf, List.of(action));

        ruleDao.save(rule);

        Optional<Rule> loadedOpt = ruleDao.findById("rule-leaf-1");
        assertTrue(loadedOpt.isPresent());
        Rule loaded = loadedOpt.get();
        assertEquals("rule-leaf-1", loaded.getId());
        assertEquals(1, loaded.getActions().size());

        Map<DeviceStateKey, String> snapTrue = Map.of(new DeviceStateKey("thermostat-living-room", "temperature"), "26.5");
        Map<DeviceStateKey, String> snapFalse = Map.of(new DeviceStateKey("thermostat-living-room", "temperature"), "22.0");

        assertTrue(loaded.getRootCondition().evaluate(snapTrue));
        assertFalse(loaded.getRootCondition().evaluate(snapFalse));
    }

    @Test
    @DisplayName("findAllEnabledRules orders by priority tier rank, then tieBreakOrder, then createdAt")
    void testFindAllEnabledRulesOrdering() {
        LeafCondition cond = new LeafCondition("door-lock-front", "status", Operator.EQ, "LOCKED");
        Action act = new Action("siren-main", "alarm", "OFF");

        Rule energyRule = new Rule("rule-energy", "Energy Saver", true, PriorityTier.ENERGY, 1, cond, List.of(act), Instant.now());
        Rule comfortRule = new Rule("rule-comfort", "Comfort Living", true, PriorityTier.COMFORT, 5, cond, List.of(act), Instant.now());
        Rule safetyRule2 = new Rule("rule-safety-2", "Safety Backup", true, PriorityTier.SAFETY, 10, cond, List.of(act), Instant.now());
        Rule safetyRule1 = new Rule("rule-safety-1", "Safety Main", true, PriorityTier.SAFETY, 1, cond, List.of(act), Instant.now());
        Rule disabledRule = new Rule("rule-disabled", "Disabled Rule", false, PriorityTier.SAFETY, 0, cond, List.of(act), Instant.now());

        ruleDao.save(energyRule);
        ruleDao.save(comfortRule);
        ruleDao.save(safetyRule2);
        ruleDao.save(safetyRule1);
        ruleDao.save(disabledRule);

        List<Rule> enabledRules = ruleDao.findAllEnabledRules();
        assertFalse(enabledRules.isEmpty());

        // Verify disabled rule is excluded
        assertFalse(enabledRules.stream().anyMatch(r -> "rule-disabled".equals(r.getId())));

        // Verify order: safetyRule1 (rank 0, order 1), safetyRule2 (rank 0, order 10), comfortRule (rank 1), energyRule (rank 2)
        assertEquals("rule-safety-1", enabledRules.get(0).getId());
        assertEquals("rule-safety-2", enabledRules.get(1).getId());
        assertEquals("rule-comfort", enabledRules.get(2).getId());
        assertEquals("rule-energy", enabledRules.get(3).getId());
    }

    @Test
    @DisplayName("findByDeviceId finds rules referencing device in condition or action")
    void testFindByDeviceId() {
        LeafCondition cond = new LeafCondition("smoke-detector-kitchen", "smoke", Operator.EQ, "DETECTED");
        Action act = new Action("siren-main", "alarm", "ON");
        Rule r = new Rule("rule-smoke", "Smoke Siren", true, PriorityTier.SAFETY, 0, cond, List.of(act));

        ruleDao.save(r);

        List<Rule> bySmoke = ruleDao.findByDeviceId("smoke-detector-kitchen");
        assertEquals(1, bySmoke.size());
        assertEquals("rule-smoke", bySmoke.get(0).getId());

        List<Rule> bySiren = ruleDao.findByDeviceId("siren-main");
        assertEquals(1, bySiren.size());
        assertEquals("rule-smoke", bySiren.get(0).getId());

        List<Rule> byUnrelated = ruleDao.findByDeviceId("non-existent-device");
        assertTrue(byUnrelated.isEmpty());
    }

    @Test
    @DisplayName("Cascade delete: deleting rule deletes condition groups, leaves, and actions")
    void testRuleCascadeDelete() throws SQLException {
        LeafCondition c1 = new LeafCondition("door-lock-front", "status", Operator.EQ, "OPEN");
        LeafCondition c2 = new LeafCondition("light-living-room", "power", Operator.EQ, "ON");
        Condition root = new ConditionGroup(LogicalOperator.AND, List.of(c1, c2));
        Action act = new Action("siren-main", "alarm", "ON");

        Rule rule = new Rule("rule-to-delete", "Temporary Rule", true, PriorityTier.COMFORT, 1, root, List.of(act));
        ruleDao.save(rule);

        assertTrue(ruleDao.existsById("rule-to-delete"));

        // Delete rule
        boolean deleted = ruleDao.deleteById("rule-to-delete");
        assertTrue(deleted);
        assertFalse(ruleDao.existsById("rule-to-delete"));

        // Verify foreign key cascades
        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM condition_group WHERE rule_id = ?")) {
                stmt.setString(1, "rule-to-delete");
                try (ResultSet rs = stmt.executeQuery()) {
                    assertFalse(rs.next(), "Condition groups must be cascade-deleted");
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM rule_action WHERE rule_id = ?")) {
                stmt.setString(1, "rule-to-delete");
                try (ResultSet rs = stmt.executeQuery()) {
                    assertFalse(rs.next(), "Rule actions must be cascade-deleted");
                }
            }
        }
    }
}

