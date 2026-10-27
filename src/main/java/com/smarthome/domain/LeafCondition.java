package com.smarthome.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Leaf condition evaluating a specific device attribute with an operator and target value.
 *
 * @param deviceId identifier of the device being evaluated
 * @param stateKey attribute key on the device (e.g. "temperature", "power")
 * @param operator comparison operator
 * @param value    expected value to compare against
 */
public record LeafCondition(
        String deviceId,
        String stateKey,
        Operator operator,
        String value
) implements Condition {

    public LeafCondition {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be blank");
        }
        if (stateKey.isBlank()) {
            throw new IllegalArgumentException("stateKey cannot be blank");
        }

        deviceId = deviceId.trim();
        stateKey = stateKey.trim();
        value = value.trim();
    }

    public DeviceStateKey getDeviceStateKey() {
        return new DeviceStateKey(deviceId, stateKey);
    }

    @Override
    public boolean evaluate(Map<DeviceStateKey, String> stateSnapshot) {
        Objects.requireNonNull(stateSnapshot, "stateSnapshot cannot be null");
        String actualValue = stateSnapshot.get(getDeviceStateKey());
        return operator.apply(actualValue, value);
    }

    @Override
    public Set<DeviceStateKey> getReferencedKeys() {
        return Set.of(getDeviceStateKey());
    }
}
