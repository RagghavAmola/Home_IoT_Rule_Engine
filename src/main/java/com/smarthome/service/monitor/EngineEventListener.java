package com.smarthome.service.monitor;

import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;

import java.util.List;

/**
 * Framework-agnostic listener for engine evaluation and background monitor events.
 * <p>
 * This interface has zero dependencies on JavaFX or database abstractions, allowing it
 * to be implemented by UI presenters (dispatching via {@code Platform.runLater}), logging observers,
 * or test harnesses.
 * </p>
 * <p>
 * <b>Thread-safety note:</b> Callbacks are dispatched from the background {@code rule-monitor}
 * daemon thread. Implementations that update UI components must marshal updates to the appropriate
 * UI event dispatch thread.
 * </p>
 */
public interface EngineEventListener {

    /**
     * Invoked when a device state attribute is mutated in the state store as a result of an applied action.
     *
     * @param key      the device state key that changed
     * @param oldValue the previous value, or {@code null} if unset
     * @param newValue the new state value
     */
    default void onStateChanged(DeviceStateKey key, String oldValue, String newValue) {
    }

    /**
     * Invoked when an enabled rule is satisfied and its actions are applied.
     *
     * @param rule           the rule that fired
     * @param appliedActions non-conflicting actions executed on devices
     */
    default void onRuleFired(Rule rule, List<Action> appliedActions) {
    }

    /**
     * Invoked when a rule's action is dropped or overridden by a higher-priority rule.
     *
     * @param record override audit log record
     */
    default void onOverride(OverrideRecord record) {
    }

    /**
     * Invoked when an unexpected exception occurs during an evaluation tick.
     *
     * @param message human-readable description of the error context
     * @param error   the thrown exception or error
     */
    default void onError(String message, Throwable error) {
    }
}