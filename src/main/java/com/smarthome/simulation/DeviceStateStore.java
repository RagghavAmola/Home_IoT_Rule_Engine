package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.engine.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory repository representing the live operational state of all smart devices.
 * <p>
 * <b>Thread-safety strategy:</b>
 * <ul>
 *   <li><b>Live State:</b> Managed via {@link ConcurrentHashMap}, providing lock-free concurrent reads
 *       and thread-safe atomic updates across concurrent writers.</li>
 *   <li><b>Device Registry:</b> Backed by a {@link ConcurrentHashMap} allowing thread-safe registration
 *       and concurrent lookups.</li>
 *   <li><b>Listeners:</b> Stored in a {@link CopyOnWriteArrayList}, guaranteeing safe lock-free iteration
 *       during event dispatch while allowing concurrent registration/deregistration of listeners.</li>
 *   <li><b>Snapshots:</b> {@link #snapshot()} captures an atomic, unmodifiable point-in-time copy via
 *       {@link StateSnapshot}, completely isolated from concurrent and future mutations.</li>
 * </ul>
 * </p>
 */
public class DeviceStateStore {

    private static final Logger logger = LoggerFactory.getLogger(DeviceStateStore.class);

    private final ConcurrentHashMap<DeviceStateKey, String> liveState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SimulatedDevice> registeredDevices = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    public DeviceStateStore() {
    }

    /**
     * Captures an immutable, detached point-in-time snapshot of the current live device states.
     *
     * @return an immutable {@link StateSnapshot}
     */
    public StateSnapshot snapshot() {
        return StateSnapshot.of(liveState);
    }

    /**
     * Applies an action to the live device state store.
     * <p>
     * If the target device is registered, its state validation rules are strictly enforced.
     * If the new value differs from the existing value, registered listeners are notified.
     * </p>
     *
     * @param action the action command to apply
     * @return true if the state value actually changed; false if it was identical
     * @throws IllegalArgumentException if the action is invalid according to the device's validation rules
     */
    public boolean applyAction(Action action) {
        Objects.requireNonNull(action, "action cannot be null");
        return setState(action.deviceId(), action.stateKey(), action.targetValue());
    }

    /**
     * Updates or inserts a device state attribute.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @param value    target value to store
     * @return true if the state changed, false if the value was already identical
     * @throws IllegalArgumentException if the state is invalid for the registered device
     */
    public boolean setState(String deviceId, String stateKey, String value) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        return setState(new DeviceStateKey(deviceId, stateKey), value);
    }

    /**
     * Updates or inserts a device state attribute using a {@link DeviceStateKey}.
     *
     * @param key   device state key
     * @param value target value to store
     * @return true if the state changed, false if the value was already identical
     * @throws IllegalArgumentException if the state is invalid for the registered device
     */
    public boolean setState(DeviceStateKey key, String value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        String val = value.trim();

        // Validate against registered device if available
        SimulatedDevice device = registeredDevices.get(key.getDeviceId());
        if (device != null) {
            device.validateState(key.getStateKey(), val);
        }

        String previousValue = liveState.put(key, val);
        boolean changed = !Objects.equals(previousValue, val);

        if (changed) {
            notifyListeners(key, previousValue, val);
        }

        return changed;
    }

    /**
     * Retrieves the current live state value for the given device key.
     *
     * @param key device state key
     * @return current value, or null if not yet initialized
     */
    public String getState(DeviceStateKey key) {
        if (key == null) return null;
        return liveState.get(key);
    }

    /**
     * Retrieves the current live state value for the given device ID and attribute key.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @return current value, or null if not yet initialized
     */
    public String getState(String deviceId, String stateKey) {
        if (deviceId == null || stateKey == null) return null;
        return getState(new DeviceStateKey(deviceId, stateKey));
    }

    /**
     * Registers a simulated device with the store and initializes its default states
     * for any attributes not yet present.
     *
     * @param device simulated device to register
     */
    public void registerDevice(SimulatedDevice device) {
        Objects.requireNonNull(device, "device cannot be null");
        registeredDevices.put(device.getId(), device);

        // Populate default state values for uninitialized keys
        Map<String, String> defaults = device.getDefaultState();
        if (defaults != null) {
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                DeviceStateKey key = new DeviceStateKey(device.getId(), entry.getKey());
                liveState.putIfAbsent(key, entry.getValue());
            }
        }
    }

    /**
     * Removes a simulated device from the registry.
     *
     * @param deviceId identifier of the device to unregister
     */
    public void unregisterDevice(String deviceId) {
        if (deviceId != null) {
            registeredDevices.remove(deviceId);
        }
    }

    /**
     * Looks up a registered simulated device by ID.
     *
     * @param deviceId identifier of the device
     * @return simulated device, or null if not found
     */
    public SimulatedDevice getDevice(String deviceId) {
        if (deviceId == null) return null;
        return registeredDevices.get(deviceId);
    }

    /**
     * Returns an unmodifiable collection of all registered simulated devices.
     *
     * @return collection of simulated devices
     */
    public Collection<SimulatedDevice> getRegisteredDevices() {
        return Collections.unmodifiableCollection(registeredDevices.values());
    }

    /**
     * Registers a listener to be notified on state updates if not already present.
     *
     * @param listener state change listener
     */
    public void addListener(StateChangeListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.addIfAbsent(listener);
    }

    /**
     * Deregisters a previously registered listener.
     *
     * @param listener state change listener
     */
    public void removeListener(StateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Returns the number of currently registered listeners.
     *
     * @return listener count
     */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * Number of distinct state attributes currently tracked in the live store.
     *
     * @return attribute count
     */
    public int size() {
        return liveState.size();
    }

    /**
     * Clears all live state, registered devices, and registered listeners.
     */
    public void clear() {
        liveState.clear();
        registeredDevices.clear();
        listeners.clear();
    }

    private void notifyListeners(DeviceStateKey key, String oldValue, String newValue) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(key, oldValue, newValue);
            } catch (Exception e) {
                logger.error("Error in StateChangeListener for key={}: {}", key, e.getMessage(), e);
            }
        }
    }
}