package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated alarm siren security actuator.
 */
public class SimulatedSiren extends AbstractSimulatedDevice {

    public static final String KEY_ALARM = "alarm";
    public static final String KEY_POWER = "power";
    public static final String KEY_VOLUME = "volume";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_ALARM, KEY_POWER, KEY_VOLUME);

    public SimulatedSiren(String id, String name, String room) {
        super(id, name, DeviceType.SIREN, room);
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

        if (KEY_ALARM.equalsIgnoreCase(key) || KEY_POWER.equalsIgnoreCase(key)) {
            if (!val.equalsIgnoreCase("ON") && !val.equalsIgnoreCase("OFF")) {
                throw new IllegalArgumentException("Siren alarm/power must be ON or OFF, but was: " + value);
            }
        } else if (KEY_VOLUME.equalsIgnoreCase(key)) {
            try {
                int vol = Integer.parseInt(val);
                if (vol < 0 || vol > 100) {
                    throw new IllegalArgumentException("Siren volume must be between 0 and 100, but was: " + vol);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Siren volume must be an integer, but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Siren");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_ALARM, "OFF",
                KEY_VOLUME, "80"
        );
    }
}

