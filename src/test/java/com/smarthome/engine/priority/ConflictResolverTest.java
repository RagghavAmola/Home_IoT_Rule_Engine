package com.smarthome.engine.priority;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictResolverTest {

    private ConflictResolver resolver;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        resolver = new ConflictResolver();
        auditLogger = (Logger) LoggerFactory.getLogger("RULE_AUDIT");
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    private Rule createRule(String id, String name, PriorityTier tier, int tieBreakOrder, Instant createdAt, Action action) {
        LeafCondition dummyCond = new LeafCondition("sensor", "status", Operator.EQ, "ACTIVE");
        return new Rule(id, name, true, tier, tieBreakOrder, dummyCond, List.of(action), createdAt);
    }

    @Test
    @DisplayName("Safety vs Comfort on the same thermostat: Safety wins and Comfort is overridden")
    void testSafetyVsComfortThermostat() {
        Action safetyAction = new Action("thermostat-1", "temperature", "18");
        Action comfortAction = new Action("thermostat-1", "temperature", "24");

        Instant now = Instant.now();
        Rule safetyRule = createRule("R_SAFETY", "Freeze Protection", PriorityTier.SAFETY, 0, now, safetyAction);
        Rule comfortRule = createRule("R_COMFORT", "Comfort Heating", PriorityTier.COMFORT, 0, now, comfortAction);

        List<TriggeredRule> triggered = List.of(
                new TriggeredRule(comfortRule),
                new TriggeredRule(safetyRule)
        );

        ResolutionResult result = resolver.resolve(triggered);

        // Verify actions to execute contains only the Safety action
        assertEquals(1, result.toExecute().size());
        assertEquals("thermostat-1", result.toExecute().get(0).deviceId());
        assertEquals("temperature", result.toExecute().get(0).stateKey());
        assertEquals("18", result.toExecute().get(0).targetValue());

        // Verify override record
        assertEquals(1, result.overrides().size());
        OverrideRecord override = result.overrides().get(0);
        assertEquals("R_SAFETY", override.winningRuleId());
        assertEquals("R_COMFORT", override.overriddenRuleId());
        assertEquals(PriorityTier.SAFETY, override.winningTier());
        assertEquals(PriorityTier.COMFORT, override.overriddenTier());
        assertEquals(new DeviceStateKey("thermostat-1", "temperature"), override.targetKey());
        assertEquals("18", override.appliedTargetValue());
        assertEquals("24", override.overriddenTargetValue());
        assertTrue(override.reason().contains("Priority tier override"));
    }

    @Test
    @DisplayName("Three-way conflict (Safety vs Comfort vs Energy on same device/key): Safety wins, both others overridden")
    void testThreeWayConflict() {
        Action safeAct = new Action("thermostat-1", "temperature", "18");
        Action comfortAct = new Action("thermostat-1", "temperature", "22");
        Action energyAct = new Action("thermostat-1", "temperature", "20");

        Instant now = Instant.now();
        Rule safeRule = createRule("R_SAFE", "Safety Thermostat", PriorityTier.SAFETY, 0, now, safeAct);
        Rule comfortRule = createRule("R_COMFORT", "Comfort Thermostat", PriorityTier.COMFORT, 0, now, comfortAct);
        Rule energyRule = createRule("R_ENERGY", "Energy Thermostat", PriorityTier.ENERGY, 0, now, energyAct);

        List<TriggeredRule> triggered = List.of(
                new TriggeredRule(comfortRule),
                new TriggeredRule(energyRule),
                new TriggeredRule(safeRule)
        );

        ResolutionResult result = resolver.resolve(triggered);

        // Safety action prevails
        assertEquals(1, result.toExecute().size());
        assertEquals("18", result.toExecute().get(0).targetValue());

        // Both Comfort and Energy overridden
        assertEquals(2, result.overrides().size());
        assertTrue(result.overrides().stream().anyMatch(o -> "R_COMFORT".equals(o.overriddenRuleId())));
        assertTrue(result.overrides().stream().anyMatch(o -> "R_ENERGY".equals(o.overriddenRuleId())));
        assertTrue(result.overrides().stream().allMatch(o -> "R_SAFE".equals(o.winningRuleId())));
    }

    @Test
    @DisplayName("Same-tier tie-break determinism: tieBreakOrder decides winner")
    void testSameTierTieBreakDeterminism() {
        Action act1 = new Action("light-1", "power", "ON");
        Action act2 = new Action("light-1", "power", "OFF");

        Instant now = Instant.now();
        // Both COMFORT tier; R1 has lower tieBreakOrder (1 vs 2)
        Rule r1 = createRule("R1", "Morning Light", PriorityTier.COMFORT, 1, now, act1);
        Rule r2 = createRule("R2", "Cinema Dim", PriorityTier.COMFORT, 2, now, act2);

        List<TriggeredRule> triggered = List.of(
                new TriggeredRule(r2),
                new TriggeredRule(r1)
        );

        ResolutionResult result = resolver.resolve(triggered);

        assertEquals(1, result.toExecute().size());
        assertEquals("ON", result.toExecute().get(0).targetValue(), "R1 with tieBreakOrder 1 must win over R2");

        assertEquals(1, result.overrides().size());
        OverrideRecord override = result.overrides().get(0);
        assertEquals("R1", override.winningRuleId());
        assertEquals("R2", override.overriddenRuleId());
        assertEquals(PriorityTier.COMFORT, override.winningTier());
        assertEquals(PriorityTier.COMFORT, override.overriddenTier());
        assertTrue(override.reason().contains("Same-tier tie-break override"));
    }

    @Test
    @DisplayName("No-conflict passthrough: independent devices and harmonizing actions pass untouched")
    void testNoConflictPassthrough() {
        Action act1 = new Action("light-1", "power", "ON");
        Action act2 = new Action("thermostat-1", "temperature", "21");
        Action act3 = new Action("door-1", "lock", "LOCKED");

        Instant now = Instant.now();
        Rule r1 = createRule("R1", "Rule 1", PriorityTier.COMFORT, 0, now, act1);
        Rule r2 = createRule("R2", "Rule 2", PriorityTier.COMFORT, 0, now, act2);
        Rule r3 = createRule("R3", "Rule 3", PriorityTier.SAFETY, 0, now, act3);

        ResolutionResult result = resolver.resolve(List.of(
                new TriggeredRule(r1),
                new TriggeredRule(r2),
                new TriggeredRule(r3)
        ));

        assertEquals(3, result.toExecute().size());
        assertTrue(result.overrides().isEmpty(), "No overrides should be generated for independent targets");

        // Harmonious agreement on same key
        Action actAgree1 = new Action("siren-1", "alarm", "ON");
        Action actAgree2 = new Action("siren-1", "alarm", "ON");
        Rule rAgree1 = createRule("RA1", "Fire Alarm", PriorityTier.SAFETY, 0, now, actAgree1);
        Rule rAgree2 = createRule("RA2", "Burglary Alarm", PriorityTier.SAFETY, 0, now, actAgree2);

        ResolutionResult agreeResult = resolver.resolve(List.of(
                new TriggeredRule(rAgree1),
                new TriggeredRule(rAgree2)
        ));

        assertEquals(1, agreeResult.toExecute().size());
        assertEquals("ON", agreeResult.toExecute().get(0).targetValue());
        assertTrue(agreeResult.overrides().isEmpty(), "Agreeing target values must not produce an override");
    }

    @Test
    @DisplayName("Multiple independent conflicts in one cycle: each target resolved independently")
    void testMultipleIndependentConflictsInOneCycle() {
        // Conflict 1 on thermostat-1.temperature: Safety ("18") vs Comfort ("24")
        Action safeTemp = new Action("thermostat-1", "temperature", "18");
        Action comfortTemp = new Action("thermostat-1", "temperature", "24");

        // Conflict 2 on light-1.power: Comfort ("ON") vs Energy ("OFF")
        Action comfortLight = new Action("light-1", "power", "ON");
        Action energyLight = new Action("light-1", "power", "OFF");

        // Non-conflicting action on window-1.status
        Action windowAction = new Action("window-1", "status", "CLOSED");

        Instant now = Instant.now();
        Rule rSafe = createRule("R_SAFE", "Safety Rule", PriorityTier.SAFETY, 0, now, safeTemp);
        Rule rComfort = new Rule("R_COMFORT", "Comfort Rule", true, PriorityTier.COMFORT, 0,
                new LeafCondition("s", "k", Operator.EQ, "1"), List.of(comfortTemp, comfortLight), now);
        Rule rEnergy = new Rule("R_ENERGY", "Energy Rule", true, PriorityTier.ENERGY, 0,
                new LeafCondition("s", "k", Operator.EQ, "1"), List.of(energyLight, windowAction), now);

        ResolutionResult result = resolver.resolve(List.of(
                new TriggeredRule(rComfort),
                new TriggeredRule(rEnergy),
                new TriggeredRule(rSafe)
        ));

        // 3 distinct targets resolved
        assertEquals(3, result.toExecute().size());

        // Verify thermostat: 18 (Safety)
        Action appliedTemp = result.toExecute().stream()
                .filter(a -> "thermostat-1".equals(a.deviceId()))
                .findFirst().orElseThrow();
        assertEquals("18", appliedTemp.targetValue());

        // Verify light: ON (Comfort > Energy)
        Action appliedLight = result.toExecute().stream()
                .filter(a -> "light-1".equals(a.deviceId()))
                .findFirst().orElseThrow();
        assertEquals("ON", appliedLight.targetValue());

        // Verify window: CLOSED (uncontested)
        Action appliedWindow = result.toExecute().stream()
                .filter(a -> "window-1".equals(a.deviceId()))
                .findFirst().orElseThrow();
        assertEquals("CLOSED", appliedWindow.targetValue());

        // 2 overrides recorded
        assertEquals(2, result.overrides().size());
    }

    @Test
    @DisplayName("Assertion that audit logger emitted structured events for overrides via Logback ListAppender")
    void testAuditLoggerEmission() {
        Action safetyAction = new Action("door-1", "lock", "LOCKED");
        Action comfortAction = new Action("door-1", "lock", "UNLOCKED");

        Instant now = Instant.now();
        Rule safetyRule = createRule("R_LOCK_SAFE", "Safety Lockdown", PriorityTier.SAFETY, 0, now, safetyAction);
        Rule comfortRule = createRule("R_LOCK_COMFORT", "Welcome Unlock", PriorityTier.COMFORT, 0, now, comfortAction);

        resolver.resolve(List.of(
                new TriggeredRule(comfortRule),
                new TriggeredRule(safetyRule)
        ));

        // Verify events captured by ListAppender on RULE_AUDIT logger
        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty(), "Audit logger must capture override events");

        ILoggingEvent event = events.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("OVERRIDE:"));
        assertTrue(msg.contains("winnerId=R_LOCK_SAFE"));
        assertTrue(msg.contains("tier=SAFETY"));
        assertTrue(msg.contains("loserId=R_LOCK_COMFORT"));
        assertTrue(msg.contains("applied='LOCKED'"));
        assertTrue(msg.contains("dropped='UNLOCKED'"));
    }
    @Test
    @DisplayName("Null or empty triggered rules list returns empty resolution result safely")
    void testNullOrEmptyTriggeredRules() {
        ResolutionResult nullResult = resolver.resolve(null);
        assertNotNull(nullResult);
        assertTrue(nullResult.toExecute().isEmpty());
        assertTrue(nullResult.overrides().isEmpty());

        ResolutionResult emptyResult = resolver.resolve(List.of());
        assertNotNull(emptyResult);
        assertTrue(emptyResult.toExecute().isEmpty());
        assertTrue(emptyResult.overrides().isEmpty());
    }
}

