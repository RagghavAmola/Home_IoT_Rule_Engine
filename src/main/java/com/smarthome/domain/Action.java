package com.smarthome.domain;

import java.util.Objects;

/**
 * Immutable action describing an actuation command to execute on an IoT device.
 *
 * @param deviceId    unique identifier of the target device
 * @param stateKey    attribute key to mutate (e.g. "power", "temperature", "status")
 * @param targetValue target state value to apply (e.g. "ON", "22", "LOCKED")
 */
public record Action(
        String deviceId,
        String stateKey,
        String targetValue
) {

    public Action {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(targetValue, "targetValue cannot be null");

        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be blank");
        }
        if (stateKey.isBlank()) {
            throw new IllegalArgumentException("stateKey cannot be blank");
        }
        if (targetValue.isBlank()) {
            throw new IllegalArgumentException("targetValue cannot be blank");
        }

        deviceId = deviceId.trim();
        stateKey = stateKey.trim();
        targetValue = targetValue.trim();
    }

    /**
     * Resolves the DeviceStateKey targeted by this action.
     *
     * @return the DeviceStateKey targeted for actuation
     */
    public DeviceStateKey getTargetStateKey() {
        return new DeviceStateKey(deviceId, stateKey);
    }
}
