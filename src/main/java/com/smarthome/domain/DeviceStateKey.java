package com.smarthome.domain;

import java.util.Objects;

/**
 * Immutable value identifier pairing a device ID with a specific state attribute key.
 *
 * @param deviceId unique identifier of the target device
 * @param stateKey name of the state attribute (e.g., "power", "temperature", "smoke")
 */
public record DeviceStateKey(String deviceId, String stateKey) {

    public DeviceStateKey {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be blank");
        }
        if (stateKey.isBlank()) {
            throw new IllegalArgumentException("stateKey cannot be blank");
        }
        deviceId = deviceId.trim();
        stateKey = stateKey.trim();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getStateKey() {
        return stateKey;
    }

    @Override
    public String toString() {
        return deviceId + "." + stateKey;
    }
}
