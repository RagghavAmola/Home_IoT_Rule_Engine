package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;
import com.smarthome.engine.EvaluationResult;
import com.smarthome.engine.RuleEngine;
import com.smarthome.engine.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Simulation controller providing event injection and cascading rule execution guards.
 */
public class DeviceSimulator {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSimulator.class);

    public static final int DEFAULT_MAX_CASCADE_DEPTH = 10;

    private final DeviceStateStore stateStore;
    private final RuleEngine ruleEngine;
    private final int maxCascadeDepth;

    /**
     * Default constructor using standard components and default max cascade depth of 10.
     */
    public DeviceSimulator() {
        this(new DeviceStateStore(), new RuleEngine(), DEFAULT_MAX_CASCADE_DEPTH);
    }

    /**
     * Constructor with state store and default rule engine.
     *
     * @param stateStore backing device state store
     */
    public DeviceSimulator(DeviceStateStore stateStore) {
        this(stateStore, new RuleEngine(), DEFAULT_MAX_CASCADE_DEPTH);
    }

    /**
     * Constructor with state store and rule engine.
     *
     * @param stateStore backing device state store
     * @param ruleEngine rule evaluation engine
     */
    public DeviceSimulator(DeviceStateStore stateStore, RuleEngine ruleEngine) {
        this(stateStore, ruleEngine, DEFAULT_MAX_CASCADE_DEPTH);
    }

    /**
     * Full constructor with custom maximum cascade depth.
     *
     * @param stateStore      backing device state store
     * @param ruleEngine      rule evaluation engine
     * @param maxCascadeDepth maximum cascade depth permitted per evaluation tick (must be >= 1)
     */
    public DeviceSimulator(DeviceStateStore stateStore, RuleEngine ruleEngine, int maxCascadeDepth) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore cannot be null");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine cannot be null");
        if (maxCascadeDepth < 1) {
            throw new IllegalArgumentException("maxCascadeDepth must be at least 1, but was: " + maxCascadeDepth);
        }
        this.maxCascadeDepth = maxCascadeDepth;
    }

    public DeviceStateStore getStateStore() {
        return stateStore;
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    public int getMaxCascadeDepth() {
        return maxCascadeDepth;
    }

    /**
     * Registers a simulated device with the underlying state store.
     *
     * @param device simulated device to register
     */
    public void registerDevice(SimulatedDevice device) {
        Objects.requireNonNull(device, "device cannot be null");
        stateStore.registerDevice(device);
    }

    // --- Telemetry Event Injection Utilities ---

    /**
     * Injects an arbitrary state change event into the device state store.
     *
     * @param deviceId identifier of the target device
     * @param stateKey attribute key
     * @param value    target state value
     */
    public void injectEvent(String deviceId, String stateKey, String value) {
        stateStore.setState(deviceId, stateKey, value);
    }

    /**
     * Injects a smoke detected event.
     *
     * @param deviceId smoke detector device ID
     */
    public void triggerSmoke(String deviceId) {
        injectEvent(deviceId, SimulatedSmokeDetector.KEY_SMOKE, "DETECTED");
    }

    /**
     * Clears a smoke detector alert back to normal.
     *
     * @param deviceId smoke detector device ID
     */
    public void clearSmoke(String deviceId) {
        injectEvent(deviceId, SimulatedSmokeDetector.KEY_SMOKE, "CLEAR");
    }

    /**
     * Injects a motion detected event.
     *
     * @param deviceId motion sensor device ID
     */
    public void triggerMotion(String deviceId) {
        injectEvent(deviceId, SimulatedMotionSensor.KEY_MOTION, "DETECTED");
    }

    /**
     * Clears a motion sensor event.
     *
     * @param deviceId motion sensor device ID
     */
    public void clearMotion(String deviceId) {
        injectEvent(deviceId, SimulatedMotionSensor.KEY_MOTION, "NONE");
    }

    /**
     * Injects a thermostat temperature reading.
     *
     * @param deviceId    thermostat device ID
     * @param temperature temperature in Celsius
     */
    public void setTemperature(String deviceId, double temperature) {
        injectEvent(deviceId, SimulatedThermostat.KEY_TEMPERATURE, String.valueOf(temperature));
    }

    /**
     * Sets door lock status.
     *
     * @param deviceId door lock device ID
     * @param locked   true for LOCKED, false for UNLOCKED
     */
    public void setDoorLocked(String deviceId, boolean locked) {
        String status = locked ? "LOCKED" : "UNLOCKED";
        injectEvent(deviceId, SimulatedDoorLock.KEY_STATUS, status);
    }

    /**
     * Sets light power state.
     *
     * @param deviceId light device ID
     * @param on       true for ON, false for OFF
     */
    public void setLightPower(String deviceId, boolean on) {
        injectEvent(deviceId, SimulatedLight.KEY_POWER, on ? "ON" : "OFF");
    }

    /**
     * Sets light brightness level.
     *
     * @param deviceId   light device ID
     * @param brightness brightness percentage (0-100)
     */
    public void setLightBrightness(String deviceId, int brightness) {
        injectEvent(deviceId, SimulatedLight.KEY_BRIGHTNESS, String.valueOf(brightness));
    }

    /**
     * Sets window open/closed status.
     *
     * @param deviceId window device ID
     * @param open     true for OPEN, false for CLOSED
     */
    public void setWindowOpen(String deviceId, boolean open) {
        injectEvent(deviceId, SimulatedWindow.KEY_STATUS, open ? "OPEN" : "CLOSED");
    }

    /**
     * Sets smart plug power state.
     *
     * @param deviceId plug device ID
     * @param on       true for ON, false for OFF
     */
    public void setPlugPower(String deviceId, boolean on) {
        injectEvent(deviceId, SimulatedSmartPlug.KEY_POWER, on ? "ON" : "OFF");
    }

    /**
     * Sets siren alarm state.
     *
     * @param deviceId siren device ID
     * @param on       true for ON, false for OFF
     */
    public void setSirenAlarm(String deviceId, boolean on) {
        injectEvent(deviceId, SimulatedSiren.KEY_ALARM, on ? "ON" : "OFF");
    }

    // --- Cascade Evaluation & Runaway Chain Defense ---

    /**
     * Injects an event and immediately triggers a cascading evaluation tick.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @param value    target state value
     * @param rules    candidate active rules to evaluate
     * @return {@link CascadeResult} detailing steps, executed actions, overrides, and whether max depth was hit
     */
    public CascadeResult injectEventAndEvaluate(String deviceId, String stateKey, String value, Collection<Rule> rules) {
        injectEvent(deviceId, stateKey, value);
        return evaluateTick(rules);
    }

    /**
     * Executes an evaluation tick, cascading rule actuations until reaching steady state
     * or reaching the maximum cascade depth limit.
     *
     * @param rules active rules to evaluate
     * @return {@link CascadeResult} containing step counts, executed actions, overrides, and safety status
     */
    public CascadeResult evaluateTick(Collection<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new CascadeResult(0, false, List.of(), List.of());
        }

        int step = 0;
        boolean maxDepthReached = false;
        List<Action> allExecutedActions = new ArrayList<>();
        List<OverrideRecord> allOverrides = new ArrayList<>();

        while (step < maxCascadeDepth) {
            StateSnapshot snapshot = stateStore.snapshot();
            EvaluationResult result = ruleEngine.evaluate(snapshot, rules);
            List<Action> effectiveActions = findEffectiveActions(result, snapshot);

            if (effectiveActions.isEmpty()) {
                // System reached steady state (quiescence)
                break;
            }

            allOverrides.addAll(result.overrides());
            step++;
            for (Action action : effectiveActions) {
                stateStore.applyAction(action);
                allExecutedActions.add(action);
            }
        }

        // Check if the loop terminated because it hit the maximum cascade depth
        if (step >= maxCascadeDepth) {
            StateSnapshot finalSnapshot = stateStore.snapshot();
            EvaluationResult checkResult = ruleEngine.evaluate(finalSnapshot, rules);
            if (!findEffectiveActions(checkResult, finalSnapshot).isEmpty()) {
                maxDepthReached = true;
                logger.warn(
                        "Runaway cascade chain guard triggered: exceeded max cascade depth of {} in a single tick. Halting cascade evaluation.",
                        maxCascadeDepth
                );
            }
        }

        return new CascadeResult(step, maxDepthReached, allExecutedActions, allOverrides);
    }

    private List<Action> findEffectiveActions(EvaluationResult result, StateSnapshot snapshot) {
        List<Action> effectiveActions = new ArrayList<>();
        for (Action action : result.actionsToExecute()) {
            String currentValue = snapshot.get(action.getTargetStateKey());
            if (!Objects.equals(currentValue, action.targetValue())) {
                effectiveActions.add(action);
            }
        }
        return effectiveActions;
    }
}