package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated smart motorized window or contact sensor.
 */
public class SimulatedWindow extends AbstractSimulatedDevice {

    public static final String KEY_STATUS = "status";
    public static final String KEY_STATE = "state";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_STATUS, KEY_STATE);

    public SimulatedWindow(String id, String name, String room) {
        super(id, name, DeviceType.WINDOW, room);
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

        if (KEY_STATUS.equalsIgnoreCase(key) || KEY_STATE.equalsIgnoreCase(key)) {
            if (!val.equalsIgnoreCase("OPEN") && !val.equalsIgnoreCase("CLOSED")) {
                throw new IllegalArgumentException("Window status must be OPEN or CLOSED, but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Window");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(KEY_STATUS, "CLOSED");
    }
}

