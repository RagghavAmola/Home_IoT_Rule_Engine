package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated smart door lock actuator.
 */
public class SimulatedDoorLock extends AbstractSimulatedDevice {

    public static final String KEY_STATUS = "status";
    public static final String KEY_LOCK = "lock";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_STATUS, KEY_LOCK);

    public SimulatedDoorLock(String id, String name, String room) {
        super(id, name, DeviceType.DOOR_LOCK, room);
    }

    @Override
    public Set<String> getSupportedKeys() {
        return SUPPORTED_KEYS;
    }

    @Override
    public void validateState(String stateKey, String value) throws IllegalArgumentException {
        if (stateKey == null || value == null) {
            throw new IllegalArgumentException("State key and value cannot be null");
        }
        String key = stateKey.trim().toLowerCase();
        String val = value.trim();

        if (KEY_STATUS.equalsIgnoreCase(key) || KEY_LOCK.equalsIgnoreCase(key)) {
            if (!val.equalsIgnoreCase("LOCKED") && !val.equalsIgnoreCase("UNLOCKED")) {
                throw new IllegalArgumentException("Door lock state must be LOCKED or UNLOCKED, but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Door Lock");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_STATUS, "LOCKED",
                KEY_LOCK, "LOCKED"
        );
    }
}

