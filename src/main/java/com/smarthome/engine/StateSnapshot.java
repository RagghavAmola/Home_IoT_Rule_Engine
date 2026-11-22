package com.smarthome.engine;

import com.smarthome.domain.DeviceStateKey;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Immutable snapshot of IoT device telemetry states at a specific point in time.
 * <p>
 * Implements {@link Map} delegating to an unmodifiable defensive copy, ensuring seamless
 * interoperability with condition evaluation, logging, and collection algorithms.
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b> Immutable value object. All internal collections are
 * defensively copied and unmodifiable. Arbitrary concurrent readers can safely access
 * instances across threads without synchronization.
 * </p>
 */
public final class StateSnapshot implements Map<DeviceStateKey, String> {

    private static final StateSnapshot EMPTY = new StateSnapshot(Collections.emptyMap());

    private final Map<DeviceStateKey, String> state;

    /**
     * Constructs an immutable StateSnapshot defensively copying the supplied map.
     *
     * @param state map of device state keys to string values (may be empty or null)
     */
    public StateSnapshot(Map<DeviceStateKey, String> state) {
        if (state == null || state.isEmpty()) {
            this.state = Collections.emptyMap();
        } else {
            this.state = Collections.unmodifiableMap(new HashMap<>(state));
        }
    }

    /**
     * Factory method creating a StateSnapshot from a map.
     *
     * @param state source state map
     * @return immutable StateSnapshot
     */
    public static StateSnapshot of(Map<DeviceStateKey, String> state) {
        if (state instanceof StateSnapshot snapshot) {
            return snapshot;
        }
        if (state == null || state.isEmpty()) {
            return EMPTY;
        }
        return new StateSnapshot(state);
    }

    /**
     * Returns an empty StateSnapshot instance.
     *
     * @return empty snapshot
     */
    public static StateSnapshot empty() {
        return EMPTY;
    }

    /**
     * Retrieves the state value for a deviceId and stateKey pair.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @return string value or null if absent
     */
    public String get(String deviceId, String stateKey) {
        if (deviceId == null || stateKey == null) {
            return null;
        }
        return state.get(new DeviceStateKey(deviceId, stateKey));
    }

    /**
     * Returns a new StateSnapshot incorporating the given state mapping.
     *
     * @param key   device state key
     * @param value state attribute value
     * @return new StateSnapshot with updated value
     */
    public StateSnapshot with(DeviceStateKey key, String value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Map<DeviceStateKey, String> copy = new HashMap<>(this.state);
        copy.put(key, value);
        return new StateSnapshot(copy);
    }

    /**
     * Returns a new StateSnapshot incorporating the given deviceId, stateKey, and value.
     *
     * @param deviceId identifier of the device
     * @param stateKey attribute key
     * @param value    state attribute value
     * @return new StateSnapshot with updated value
     */
    public StateSnapshot with(String deviceId, String stateKey, String value) {
        return with(new DeviceStateKey(deviceId, stateKey), value);
    }

    /**
     * Returns the underlying unmodifiable map.
     *
     * @return unmodifiable map of device state keys to values
     */
    public Map<DeviceStateKey, String> asMap() {
        return state;
    }

    // --- Map interface delegation ---

    @Override
    public int size() {
        return state.size();
    }

    @Override
    public boolean isEmpty() {
        return state.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return state.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return state.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return state.get(key);
    }

    @Override
    public String put(DeviceStateKey key, String value) {
        throw new UnsupportedOperationException("StateSnapshot is immutable");
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException("StateSnapshot is immutable");
    }

    @Override
    public void putAll(Map<? extends DeviceStateKey, ? extends String> m) {
        throw new UnsupportedOperationException("StateSnapshot is immutable");
    }

    @Override
    public void replaceAll(BiFunction<? super DeviceStateKey, ? super String, ? extends String> function) {
        throw new UnsupportedOperationException("StateSnapshot is immutable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("StateSnapshot is immutable");
    }

    @Override
    public Set<DeviceStateKey> keySet() {
        return state.keySet();
    }

    @Override
    public Collection<String> values() {
        return state.values();
    }

    @Override
    public Set<Entry<DeviceStateKey, String>> entrySet() {
        return state.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Map<?, ?> that)) return false;
        return state.equals(that);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    @Override
    public String toString() {
        return "StateSnapshot" + state;
    }
}