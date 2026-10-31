package com.smarthome.dao;

import com.smarthome.domain.DeviceStateKey;

import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object interface for managing real-time IoT device state telemetry.
 */
public interface DeviceStateDao {

    /**
     * Retrieves the current value of a specific device state key.
     *
     * @param deviceId identifier of the device
     * @param stateKey name of the state key
     * @return Optional containing the state value if present, or empty Optional
     */
    Optional<String> findStateValue(String deviceId, String stateKey);

    /**
     * Retrieves all state key-value pairs for a specific device.
     *
     * @param deviceId identifier of the device
     * @return map of state keys to current values
     */
    Map<String, String> findStatesByDeviceId(String deviceId);

    /**
     * Retrieves a full snapshot of all current device states across the system.
     *
     * @return map of DeviceStateKey to state value
     */
    Map<DeviceStateKey, String> findAllStates();

    /**
     * Inserts or updates the state value for a device and state key with the current timestamp.
     *
     * @param deviceId   identifier of the device
     * @param stateKey   state attribute key
     * @param stateValue new state value
     */
    void upsertState(String deviceId, String stateKey, String stateValue);

    /**
     * Performs a batch upsert of multiple device state attributes.
     *
     * @param states map of DeviceStateKey to state values to upsert
     */
    void batchUpsertStates(Map<DeviceStateKey, String> states);

    /**
     * Deletes a specific state key for a device.
     *
     * @param deviceId identifier of the device
     * @param stateKey state attribute key
     * @return true if deleted, false if not found
     */
    boolean deleteState(String deviceId, String stateKey);

    /**
     * Deletes all state entries associated with a device.
     *
     * @param deviceId identifier of the device
     * @return number of state records deleted
     */
    int deleteStatesByDeviceId(String deviceId);
}

