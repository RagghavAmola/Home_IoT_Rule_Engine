package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated smoke safety sensor.
 */
public class SimulatedSmokeDetector extends AbstractSimulatedDevice {

    public static final String KEY_SMOKE = "smoke";
    public static final String KEY_STATUS = "status";
    public static final String KEY_BATTERY = "battery";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_SMOKE, KEY_STATUS, KEY_BATTERY);
    private static final Set<String> ALLOWED_STATES = Set.of("CLEAR", "DETECTED", "NORMAL", "ALERT", "SMOKE_DETECTED");

    public SimulatedSmokeDetector(String id, String name, String room) {
        super(id, name, DeviceType.SMOKE_DETECTOR, room);
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
        String val = value.trim().toUpperCase();

        if (KEY_SMOKE.equalsIgnoreCase(key) || KEY_STATUS.equalsIgnoreCase(key)) {
            if (!ALLOWED_STATES.contains(val)) {
                throw new IllegalArgumentException("Smoke detector state must be one of " + ALLOWED_STATES + ", but was: " + value);
            }
        } else if (KEY_BATTERY.equalsIgnoreCase(key)) {
            try {
                int battery = Integer.parseInt(val);
                if (battery < 0 || battery > 100) {
                    throw new IllegalArgumentException("Battery level must be between 0 and 100, but was: " + battery);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Battery level must be an integer, but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Smoke Detector");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_SMOKE, "CLEAR",
                KEY_BATTERY, "100"
        );
    }
}

