package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.engine.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceSimulatorTest {

    private DeviceStateStore store;
    private RuleEngine engine;
    private DeviceSimulator simulator;

    @BeforeEach
    void setUp() {
        store = new DeviceStateStore();
        engine = new RuleEngine();
        simulator = new DeviceSimulator(store, engine, 5);
    }

    @Test
    @DisplayName("Telemetry event injection utilities update store appropriately")
    void testEventInjectionUtilities() {
        simulator.triggerSmoke("smoke-1");
        assertEquals("DETECTED", store.getState("smoke-1", "smoke"));

        simulator.clearSmoke("smoke-1");
        assertEquals("CLEAR", store.getState("smoke-1", "smoke"));

        simulator.triggerMotion("motion-1");
        assertEquals("DETECTED", store.getState("motion-1", "motion"));

        simulator.clearMotion("motion-1");
        assertEquals("NONE", store.getState("motion-1", "motion"));

        simulator.setTemperature("therm-1", 24.5);
        assertEquals("24.5", store.getState("therm-1", "temperature"));

        simulator.setLightPower("light-1", true);
        assertEquals("ON", store.getState("light-1", "power"));

        simulator.setLightBrightness("light-1", 70);
        assertEquals("70", store.getState("light-1", "brightness"));

        simulator.setDoorLocked("door-1", false);
        assertEquals("UNLOCKED", store.getState("door-1", "status"));

        simulator.setWindowOpen("win-1", true);
        assertEquals("OPEN", store.getState("win-1", "status"));

        simulator.setPlugPower("plug-1", true);
        assertEquals("ON", store.getState("plug-1", "power"));

        simulator.setSirenAlarm("siren-1", true);
        assertEquals("ON", store.getState("siren-1", "alarm"));
    }

    @Test
    @DisplayName("Cascading rule chain evaluates to steady state across multiple steps")
    void testCascadingRuleChain() {
        // Cascade chain:
        // Rule 1: smoke == DETECTED -> siren.alarm = ON
        // Rule 2: siren.alarm == ON -> door.status = UNLOCKED
        // Rule 3: door.status == UNLOCKED -> light.power = ON

        Rule r1 = new Rule(
                "R1", "Fire Alarm", true, PriorityTier.SAFETY, 0,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );

        Rule r2 = new Rule(
                "R2", "Emergency Unlock", true, PriorityTier.SAFETY, 1,
                new LeafCondition("siren-1", "alarm", Operator.EQ, "ON"),
                List.of(new Action("door-1", "status", "UNLOCKED")),
                Instant.now()
        );

        Rule r3 = new Rule(
                "R3", "Exit Lighting", true, PriorityTier.SAFETY, 2,
                new LeafCondition("door-1", "status", Operator.EQ, "UNLOCKED"),
                List.of(new Action("light-1", "power", "ON")),
                Instant.now()
        );

        List<Rule> rules = List.of(r1, r2, r3);

        // Initial state: smoke is CLEAR, siren OFF, door LOCKED, light OFF
        store.setState("smoke-1", "smoke", "CLEAR");
        store.setState("siren-1", "alarm", "OFF");
        store.setState("door-1", "status", "LOCKED");
        store.setState("light-1", "power", "OFF");

        // Inject smoke event and evaluate cascade tick
        CascadeResult result = simulator.injectEventAndEvaluate("smoke-1", "smoke", "DETECTED", rules);

        // Verifications:
        assertFalse(result.maxDepthReached(), "Should reach steady state before hitting max cascade depth");
        assertEquals(3, result.cascadeSteps(), "Should complete in exactly 3 cascade steps");
        assertEquals(3, result.executedActions().size());

        // Final state verification
        assertEquals("ON", store.getState("siren-1", "alarm"));
        assertEquals("UNLOCKED", store.getState("door-1", "status"));
        assertEquals("ON", store.getState("light-1", "power"));

        // Running another tick when state is already in steady state results in 0 steps
        CascadeResult nextTick = simulator.evaluateTick(rules);
        assertEquals(0, nextTick.cascadeSteps());
        assertTrue(nextTick.executedActions().isEmpty());
    }

    @Test
    @DisplayName("Runtime guard prevents runaway cascades when max depth is exceeded")
    void testRunawayCascadeGuard() {
        // Create simulator with small max depth = 2
        DeviceSimulator guardedSimulator = new DeviceSimulator(store, engine, 2);

        // Chain of 4 rules:
        // R1: dev-0 == TRIGGER -> dev-1 = TRIGGER
        // R2: dev-1 == TRIGGER -> dev-2 = TRIGGER
        // R3: dev-2 == TRIGGER -> dev-3 = TRIGGER
        // R4: dev-3 == TRIGGER -> dev-4 = TRIGGER
        Rule r1 = new Rule("R1", "Step 1", true, PriorityTier.COMFORT, 0,
                new LeafCondition("dev-0", "state", Operator.EQ, "TRIGGER"),
                List.of(new Action("dev-1", "state", "TRIGGER")), Instant.now());
        Rule r2 = new Rule("R2", "Step 2", true, PriorityTier.COMFORT, 0,
                new LeafCondition("dev-1", "state", Operator.EQ, "TRIGGER"),
                List.of(new Action("dev-2", "state", "TRIGGER")), Instant.now());
        Rule r3 = new Rule("R3", "Step 3", true, PriorityTier.COMFORT, 0,
                new LeafCondition("dev-2", "state", Operator.EQ, "TRIGGER"),
                List.of(new Action("dev-3", "state", "TRIGGER")), Instant.now());
        Rule r4 = new Rule("R4", "Step 4", true, PriorityTier.COMFORT, 0,
                new LeafCondition("dev-3", "state", Operator.EQ, "TRIGGER"),
                List.of(new Action("dev-4", "state", "TRIGGER")), Instant.now());

        List<Rule> rules = List.of(r1, r2, r3, r4);

        CascadeResult result = guardedSimulator.injectEventAndEvaluate("dev-0", "state", "TRIGGER", rules);

        // Because max depth = 2, only steps 1 and 2 run.
        assertEquals(2, result.cascadeSteps());
        assertTrue(result.maxDepthReached(), "Guard must report that max depth was reached");
        assertEquals(2, result.executedActions().size());

        // dev-1 and dev-2 got updated; dev-3 and dev-4 were halted by the guard
        assertEquals("TRIGGER", store.getState("dev-1", "state"));
        assertEquals("TRIGGER", store.getState("dev-2", "state"));
        assertEquals(null, store.getState("dev-3", "state"));
        assertEquals(null, store.getState("dev-4", "state"));
    }
}

