package com.smarthome.simulation;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;

import java.util.Objects;

/**
 * Factory for creating concrete {@link SimulatedDevice} instances based on {@link DeviceType}.
 */
public final class SimulatedDeviceFactory {

    private SimulatedDeviceFactory() {
    }

    /**
     * Creates a simulated device instance from a domain Device entity.
     */
    public static SimulatedDevice create(Device device) {
        Objects.requireNonNull(device, "device cannot be null");
        return create(device.getId(), device.getName(), device.getType(), device.getRoom());
    }

    /**
     * Creates a simulated device instance from individual device attributes.
     *
     * @param id   unique device ID
     * @param name human-readable device name
     * @param type device type category
     * @param room room or zone location
     * @return concrete simulated device
     */
    public static SimulatedDevice create(String id, String name, DeviceType type, String room) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(room, "room cannot be null");

        return switch (type) {
            case LIGHT -> new SimulatedLight(id, name, room);
            case THERMOSTAT -> new SimulatedThermostat(id, name, room);
            case DOOR_LOCK -> new SimulatedDoorLock(id, name, room);
            case SMOKE_DETECTOR -> new SimulatedSmokeDetector(id, name, room);
            case MOTION_SENSOR -> new SimulatedMotionSensor(id, name, room);
            case WINDOW -> new SimulatedWindow(id, name, room);
            case SMART_PLUG -> new SimulatedSmartPlug(id, name, room);
            case SIREN -> new SimulatedSiren(id, name, room);
            case CAMERA -> new SimulatedCamera(id, name, room);
        };
    }
}