package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated light actuator with power, brightness, and optional color controls.
 */
public class SimulatedLight extends AbstractSimulatedDevice {

    public static final String KEY_POWER = "power";
    public static final String KEY_BRIGHTNESS = "brightness";
    public static final String KEY_COLOR = "color";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_POWER, KEY_BRIGHTNESS, KEY_COLOR);

    public SimulatedLight(String id, String name, String room) {
        super(id, name, DeviceType.LIGHT, room);
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

        if (KEY_POWER.equalsIgnoreCase(key)) {
            if (!val.equalsIgnoreCase("ON") && !val.equalsIgnoreCase("OFF")) {
                throw new IllegalArgumentException("Light power must be ON or OFF, but was: " + value);
            }
        } else if (KEY_BRIGHTNESS.equalsIgnoreCase(key)) {
            try {
                int brightness = Integer.parseInt(val);
                if (brightness < 0 || brightness > 100) {
                    throw new IllegalArgumentException("Light brightness must be between 0 and 100, but was: " + brightness);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Light brightness must be an integer, but was: " + value);
            }
        } else if (KEY_COLOR.equalsIgnoreCase(key)) {
            if (val.isBlank()) {
                throw new IllegalArgumentException("Light color cannot be blank");
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Light device");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_POWER, "OFF",
                KEY_BRIGHTNESS, "0"
        );
    }
}

