package com.smarthome.domain;

import java.util.Map;
import java.util.Set;

/**
 * Sealed condition interface supporting recursive, hierarchical rule conditions.
 */
public sealed interface Condition permits LeafCondition, ConditionGroup {

    /**
     * Evaluates the condition against the current device telemetry state snapshot.
     *
     * @param stateSnapshot map of device state keys to their current string values
     * @return true if the condition evaluates to true, false otherwise
     */
    boolean evaluate(Map<DeviceStateKey, String> stateSnapshot);

    /**
     * Retrieves the set of all device state keys inspected by this condition or its children.
     *
     * @return an unmodifiable set of referenced device state keys
     */
    Set<DeviceStateKey> getReferencedKeys();
}
