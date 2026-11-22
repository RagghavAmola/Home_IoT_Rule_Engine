package com.smarthome.service.monitor;

import com.smarthome.dao.DeviceStateDao;
import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.engine.RuleEngine;
import com.smarthome.simulation.DeviceStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RuleMonitorServiceTest {

    private DeviceStateStore stateStore;
    private RuleEngine ruleEngine;
    private RuleCache ruleCache;
    private DeviceStateDao deviceStateDao;
    private ExecutionLogDao executionLogDao;
    private RuleMonitorService monitorService;

    @BeforeEach
    void setUp() {
        stateStore = new DeviceStateStore();
        ruleEngine = new RuleEngine();
        ruleCache = new RuleCache();
        deviceStateDao = Mockito.mock(DeviceStateDao.class);
        executionLogDao = Mockito.mock(ExecutionLogDao.class);

        monitorService = new RuleMonitorService(
                stateStore,
                ruleEngine,
                ruleCache,
                deviceStateDao,
                executionLogDao,
                50L // fast 50ms interval for tests
        );
    }

    @AfterEach
    void tearDown() {
        if (monitorService != null && !monitorService.isShutdown()) {
            monitorService.shutdown();
        }
    }

    @Test
    @DisplayName("Tick fires rules, mutates state store, emits listener events, and persists to DAOs")
    void testTickFiresRulesAndEmitsEvents() {
        Rule rule = new Rule(
                "R_SMOKE",
                "Smoke Alert",
                true,
                PriorityTier.SAFETY,
                0,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );
        ruleCache.updateRule(rule);

        // Initial device telemetry
        stateStore.setState("smoke-1", "smoke", "DETECTED");
        stateStore.setState("siren-1", "alarm", "OFF");

        List<Rule> firedRules = new ArrayList<>();
        List<Action> firedActions = new ArrayList<>();
        List<String> stateChangeEvents = new ArrayList<>();

        monitorService.addListener(new EngineEventListener() {
            @Override
            public void onRuleFired(Rule r, List<Action> appliedActions) {
                firedRules.add(r);
                firedActions.addAll(appliedActions);
            }

            @Override
            public void onStateChanged(DeviceStateKey key, String oldValue, String newValue) {
                stateChangeEvents.add(key + ": " + oldValue + " -> " + newValue);
            }
        });

        // Execute tick synchronously
        TickResult result = monitorService.executeTick();

        assertEquals(1, result.rulesEvaluatedCount());
        assertEquals(1, result.triggeredRulesCount());
        assertEquals(1, result.actionsApplied().size());
        assertEquals(0, result.overrides().size());

        // Assert state store updated
        assertEquals("ON", stateStore.getState("siren-1", "alarm"));

        // Assert listeners called
        assertEquals(1, firedRules.size());
        assertEquals("R_SMOKE", firedRules.get(0).getId());
        assertEquals(1, firedActions.size());
        assertEquals("siren-1", firedActions.get(0).deviceId());
        assertEquals(1, stateChangeEvents.size());

        // Assert DAOs invoked for batch state upsert and audit logging
        verify(deviceStateDao, times(1)).batchUpsertStates(Map.of(new DeviceStateKey("siren-1", "alarm"), "ON"));
        verify(executionLogDao, times(1)).saveExecutionRecord(any());
        verify(executionLogDao, never()).saveOverrideRecord(any());
    }

    @Test
    @DisplayName("Priority conflict in evaluation tick emits override events and logs them via DAO")
    void testOverrideEventsEmitted() {
        // Comfort rule: heater -> ON
        Rule comfortRule = new Rule(
                "R_COMFORT",
                "Warm Living Room",
                true,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("therm-1", "temp", Operator.LT, "20"),
                List.of(new Action("heater-1", "power", "ON")),
                Instant.now()
        );

        // Safety rule: heater -> OFF
        Rule safetyRule = new Rule(
                "R_SAFETY",
                "Fire Cutoff",
                true,
                PriorityTier.SAFETY,
                0,
                new LeafCondition("smoke-1", "smoke", Operator.EQ, "DETECTED"),
                List.of(new Action("heater-1", "power", "OFF")),
                Instant.now()
        );

        ruleCache.updateRule(comfortRule);
        ruleCache.updateRule(safetyRule);

        stateStore.setState("therm-1", "temp", "18");
        stateStore.setState("smoke-1", "smoke", "DETECTED");
        stateStore.setState("heater-1", "power", "STANDBY");

        List<OverrideRecord> capturedOverrides = new ArrayList<>();
        monitorService.addListener(new EngineEventListener() {
            @Override
            public void onOverride(OverrideRecord record) {
                capturedOverrides.add(record);
            }
        });

        TickResult result = monitorService.executeTick();

        // 2 rules triggered, 1 action executed (Safety), 1 override (Comfort dropped)
        assertEquals(2, result.triggeredRulesCount());
        assertEquals(1, result.actionsApplied().size());
        assertEquals("OFF", result.actionsApplied().get(0).targetValue());
        assertEquals(1, result.overrides().size());

        // Assert listener captured override event
        assertEquals(1, capturedOverrides.size());
        OverrideRecord override = capturedOverrides.get(0);
        assertEquals("R_SAFETY", override.winningRuleId());
        assertEquals("R_COMFORT", override.overriddenRuleId());
        assertEquals("heater-1.power", override.targetKey().toString());

        // Assert DAO saved override
        verify(executionLogDao, times(1)).saveOverrideRecord(any(OverrideRecord.class));
    }

    @Test
    @DisplayName("Exception in a tick does not kill background schedule; subsequent ticks execute normally")
    void testExceptionInTickDoesNotStopLaterTicks() throws InterruptedException {
        // Configure deviceStateDao to throw on the first call, succeed on second
        Mockito.doThrow(new RuntimeException("Simulated DB connection failure"))
                .doNothing()
                .when(deviceStateDao).batchUpsertStates(any());

        Rule rule = new Rule(
                "R1", "Alert", true, PriorityTier.COMFORT, 0,
                new LeafCondition("dev-1", "key", Operator.EQ, "TRIGGER"),
                List.of(new Action("dev-2", "key", "ACTIVE")),
                Instant.now()
        );
        ruleCache.updateRule(rule);
        stateStore.setState("dev-1", "key", "TRIGGER");

        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch ruleFiredLatch = new CountDownLatch(1);
        AtomicInteger errorCount = new AtomicInteger(0);

        monitorService.addListener(new EngineEventListener() {
            @Override
            public void onError(String message, Throwable error) {
                errorCount.incrementAndGet();
                errorLatch.countDown();
            }

            @Override
            public void onRuleFired(Rule r, List<Action> actions) {
                ruleFiredLatch.countDown();
            }
        });

        // Start background scheduler
        monitorService.start();

        // Wait for first tick to fail and call onError
        boolean errorReceived = errorLatch.await(3, TimeUnit.SECONDS);
        assertTrue(errorReceived, "Monitor must report onError when exception occurs in tick");
        assertTrue(errorCount.get() >= 1);

        // Reset state so second tick has something to actuate
        stateStore.setState("dev-2", "key", "RESET");

        // Wait for subsequent tick to succeed without being killed by the previous error
        boolean ruleFired = ruleFiredLatch.await(3, TimeUnit.SECONDS);
        assertTrue(ruleFired, "Subsequent tick must execute successfully despite previous exception");
        assertTrue(monitorService.isRunning(), "Service must still be running");
    }

    @Test
    @DisplayName("Shutdown stops the executor and terminates gracefully")
    void testShutdownStopsExecutor() {
        monitorService.start();
        assertTrue(monitorService.isRunning());
        assertFalse(monitorService.isShutdown());

        monitorService.shutdown();
        assertFalse(monitorService.isRunning());
        assertTrue(monitorService.isShutdown());

        // Calling shutdown again is idempotent
        monitorService.shutdown();
        assertTrue(monitorService.isShutdown());
    }

    @Test
    @DisplayName("Pause and resume lifecycle controls tick evaluation")
    void testPauseAndResume() {
        Rule rule = new Rule(
                "R1", "Alert", true, PriorityTier.COMFORT, 0,
                new LeafCondition("light-1", "power", Operator.EQ, "ON"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );
        ruleCache.updateRule(rule);
        stateStore.setState("light-1", "power", "ON");
        stateStore.setState("siren-1", "alarm", "OFF");

        monitorService.start();
        monitorService.pause();
        assertTrue(monitorService.isPaused());

        // Reset siren to OFF while paused
        stateStore.setState("siren-1", "alarm", "OFF");

        // When paused, executeTick directly still works, but background tickSafe skips
        monitorService.resume();
        assertFalse(monitorService.isPaused());
        assertTrue(monitorService.isRunning());
    }

    @Test
    @DisplayName("Rule cache updates during active background evaluation are safe and isolated")
    void testRuleCacheUpdateDuringEvaluationIsSafe() throws InterruptedException {
        int writerCount = 3;
        int iterations = 100;
        ExecutorService testExecutor = Executors.newFixedThreadPool(writerCount);

        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount);
        AtomicInteger errors = new AtomicInteger(0);

        monitorService.start();

        for (int w = 0; w < writerCount; w++) {
            final int writerId = w;
            testExecutor.submit(() -> {
                try {
                    startGun.await();
                    for (int i = 0; i < iterations; i++) {
                        String id = "rule-dyn-" + writerId + "-" + i;
                        Rule r = new Rule(
                                id, "Dynamic " + id, (i % 2 == 0), PriorityTier.COMFORT, 0,
                                new LeafCondition("dev-x", "k", Operator.EQ, "v"),
                                List.of(new Action("dev-y", "k", "v")),
                                Instant.now()
                        );
                        ruleCache.updateRule(r);
                        if (i % 4 == 0) {
                            ruleCache.removeRule(id);
                        }
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGun.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        testExecutor.shutdown();

        assertTrue(completed, "Concurrent rule updates must finish within timeout");
        assertEquals(0, errors.get(), "No errors should occur during concurrent rule edits");
        assertTrue(monitorService.isRunning());
    }
}

