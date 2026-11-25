package com.smarthome.service.monitor;

import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;

import java.util.List;

/**
 * Framework-agnostic listener for engine evaluation and background monitor events.
 */
public interface EngineEventListener {

    /**
     * Invoked when a device state attribute is mutated in the state store.
     */
    default void onStateChanged(DeviceStateKey key, String oldValue, String newValue) {
    }

    /**
     * Invoked when an enabled rule is satisfied and its actions are applied.
     */
    default void onRuleFired(Rule rule, List<Action> appliedActions) {
    }

    /**
     * Invoked when a rule's action is dropped or overridden by a higher-priority rule.
     */
    default void onOverride(OverrideRecord record) {
    }

    /**
     * Invoked when an unexpected exception occurs during an evaluation tick.
     */
    default void onError(String message, Throwable error) {
    }
}