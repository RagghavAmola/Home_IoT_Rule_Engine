package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated smart plug outlet device.
 */
public class SimulatedSmartPlug extends AbstractSimulatedDevice {

    public static final String KEY_POWER = "power";
    public static final String KEY_CONSUMPTION = "consumption";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_POWER, KEY_CONSUMPTION);

    public SimulatedSmartPlug(String id, String name, String room) {
        super(id, name, DeviceType.SMART_PLUG, room);
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
                throw new IllegalArgumentException("Smart plug power must be ON or OFF, but was: " + value);
            }
        } else if (KEY_CONSUMPTION.equalsIgnoreCase(key)) {
            try {
                double watts = Double.parseDouble(val);
                if (watts < 0.0) {
                    throw new IllegalArgumentException("Power consumption cannot be negative, but was: " + watts);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Power consumption must be numeric, but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Smart Plug");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_POWER, "OFF",
                KEY_CONSUMPTION, "0.0"
        );
    }
}

