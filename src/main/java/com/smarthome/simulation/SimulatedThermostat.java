package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated HVAC thermostat device.
 */
public class SimulatedThermostat extends AbstractSimulatedDevice {

    public static final String KEY_TEMPERATURE = "temperature";
    public static final String KEY_TARGET_TEMP = "target_temp";
    public static final String KEY_MODE = "mode";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_TEMPERATURE, KEY_TARGET_TEMP, KEY_MODE);
    private static final Set<String> ALLOWED_MODES = Set.of("HEAT", "COOL", "OFF", "AUTO", "ECO");

    public SimulatedThermostat(String id, String name, String room) {
        super(id, name, DeviceType.THERMOSTAT, room);
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

        if (KEY_TEMPERATURE.equalsIgnoreCase(key)) {
            try {
                double temp = Double.parseDouble(val);
                if (temp < -50.0 || temp > 100.0) {
                    throw new IllegalArgumentException("Thermostat temperature must be between -50 and 100 C, but was: " + temp);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Thermostat temperature must be numeric, but was: " + value);
            }
        } else if (KEY_TARGET_TEMP.equalsIgnoreCase(key)) {
            try {
                double target = Double.parseDouble(val);
                if (target < 0.0 || target > 50.0) {
                    throw new IllegalArgumentException("Thermostat target temperature must be between 0 and 50 C, but was: " + target);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Thermostat target temperature must be numeric, but was: " + value);
            }
        } else if (KEY_MODE.equalsIgnoreCase(key)) {
            if (!ALLOWED_MODES.contains(val.toUpperCase())) {
                throw new IllegalArgumentException("Thermostat mode must be one of " + ALLOWED_MODES + ", but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Thermostat");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_TEMPERATURE, "21.0",
                KEY_TARGET_TEMP, "22.0",
                KEY_MODE, "AUTO"
        );
    }
}

