package com.smarthome.simulation;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Abstraction representing a simulated IoT device.
 * <p>
 * Enforces validation rules on allowed state keys and values according to the device's
 * physical capabilities and domain characteristics.
 * </p>
 */
public interface SimulatedDevice {

    /**
     * Unique identifier of the device.
     */
    String getId();

    /**
     * Human-readable device name.
     */
    String getName();

    /**
     * Type/category of the device.
     */
    DeviceType getType();

    /**
     * Room or zone where the device is located.
     */
    String getRoom();

    /**
     * Set of supported telemetry and state attribute keys for this device.
     */
    Set<String> getSupportedKeys();

    /**
     * Checks whether a state key and value combination is valid for this device.
     *
     * @param stateKey attribute key
     * @param value    proposed state value
     * @return true if valid, false otherwise
     */
    boolean isValidState(String stateKey, String value);

    /**
     * Validates a state key and value combination, throwing {@link IllegalArgumentException}
     * with a descriptive explanation if invalid.
     *
     * @param stateKey attribute key
     * @param value    proposed state value
     * @throws IllegalArgumentException if the key is unsupported or the value is invalid
     */
    void validateState(String stateKey, String value) throws IllegalArgumentException;

    /**
     * Returns the default initial state map for this device.
     *
     * @return unmodifiable map of default key-value pairs
     */
    Map<String, String> getDefaultState();

    /**
     * Converts this simulated device to a domain {@link Device} object with the given state.
     *
     * @param currentState map of current state values
     * @return immutable domain Device representation
     */
    Device toDevice(Map<String, String> currentState);
}

