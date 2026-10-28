package com.smarthome.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public final class Device {

    private final String id;
    private final String name;
    private final DeviceType type;
    private final String room;
    private final Map<String, String> state;

    /**
     * Constructs a new Device instance with an initial state map.
     *
     * @param id    unique device identifier
     * @param name  human-readable device name
     * @param type  category of the device
     * @param room  room or zone location
     * @param state map of state key-value pairs (defensively copied)
     */
    public Device(String id, String name, DeviceType type, String room, Map<String, String> state) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(room, "room cannot be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (room.isBlank()) {
            throw new IllegalArgumentException("room cannot be blank");
        }

        this.id = id.trim();
        this.name = name.trim();
        this.type = type;
        this.room = room.trim();
        this.state = state != null ? Collections.unmodifiableMap(new LinkedHashMap<>(state)) : Collections.emptyMap();
    }

    /**
     * Constructs a new Device instance with an empty initial state.
     *
     * @param id   unique device identifier
     * @param name human-readable device name
     * @param type category of the device
     * @param room room or zone location
     */
    public Device(String id, String name, DeviceType type, String room) {
        this(id, name, type, room, Collections.emptyMap());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DeviceType getType() {
        return type;
    }

    public String getRoom() {
        return room;
    }

    public Map<String, String> getState() {
        return state;
    }

    /**
     * Retrieves the state value for a given key, or null if not present.
     *
     * @param stateKey the attribute key to look up
     * @return current value or null
     */
    public String getStateValue(String stateKey) {
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        return state.get(stateKey);
    }

    /**
     * Returns a new Device instance with the updated state map.
     *
     * @param newState map of new state values
     * @return new Device instance
     */
    public Device withState(Map<String, String> newState) {
        return new Device(this.id, this.name, this.type, this.room, newState);
    }

    /**
     * Returns a new Device instance with a single state key updated or added.
     *
     * @param stateKey attribute key
     * @param value    attribute value
     * @return new Device instance
     */
    public Device withStateValue(String stateKey, String value) {
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Map<String, String> updated = new LinkedHashMap<>(this.state);
        updated.put(stateKey.trim(), value.trim());
        return new Device(this.id, this.name, this.type, this.room, updated);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Objects.equals(id, device.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Device{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", room='" + room + '\'' +
                ", state=" + state +
                '}';
    }
}
