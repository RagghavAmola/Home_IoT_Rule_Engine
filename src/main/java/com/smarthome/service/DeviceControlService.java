package com.smarthome.service;

import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceStateKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for querying devices and executing manual actuation commands.
 * <p>
 * Ensures all user-initiated telemetry changes and device actions from the UI or external
 * integrations are mediated through a consistent service boundary.
 * </p>
 */
public interface DeviceControlService {

    /**
     * Retrieves all devices registered in the smart home system.
     *
     * @return list of registered devices
     */
    List<Device> getAllDevices();

    /**
     * Looks up a device by its unique identifier.
     *
     * @param deviceId identifier of the device
     * @return Optional containing the device if found, or empty Optional
     */
    Optional<Device> getDevice(String deviceId);

    /**
     * Retrieves a point-in-time snapshot of all live device states across the system.
     *
     * @return map of device state keys to their current values
     */
    Map<DeviceStateKey, String> getAllStates();

    /**
     * Retrieves the current state value for a specific device attribute.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @return current value, or null if unset
     */
    String getState(String deviceId, String stateKey);

    /**
     * Updates a device state attribute manually, updating the live store and persisting to storage.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @param value    target state value
     */
    void updateState(String deviceId, String stateKey, String value);

    /**
     * Executes an actuation action command manually.
     *
     * @param action actuation action to apply
     */
    void executeAction(Action action);

    /**
     * Registers a device with the control service and live state store.
     *
     * @param device device entity to register
     */
    void registerDevice(Device device);
}

