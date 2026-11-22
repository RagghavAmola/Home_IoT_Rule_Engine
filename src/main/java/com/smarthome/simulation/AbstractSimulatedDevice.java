package com.smarthome.simulation;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Objects;

/**
 * Base implementation for simulated IoT devices providing common metadata handling.
 */
public abstract class AbstractSimulatedDevice implements SimulatedDevice {

    private final String id;
    private final String name;
    private final DeviceType type;
    private final String room;

    protected AbstractSimulatedDevice(String id, String name, DeviceType type, String room) {
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
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DeviceType getType() {
        return type;
    }

    @Override
    public String getRoom() {
        return room;
    }

    @Override
    public boolean isValidState(String stateKey, String value) {
        try {
            validateState(stateKey, value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    @Override
    public Device toDevice(Map<String, String> currentState) {
        return new Device(id, name, type, room, currentState != null ? currentState : getDefaultState());
    }

    @Override
    public String toString() {
        return type + "[" + id + " @ " + room + "]";
    }
}