package com.smarthome.simulation;

import com.smarthome.domain.DeviceType;

import java.util.Map;
import java.util.Set;

/**
 * Simulated security camera device.
 */
public class SimulatedCamera extends AbstractSimulatedDevice {

    public static final String KEY_STATUS = "status";
    public static final String KEY_MOTION = "motion";

    private static final Set<String> SUPPORTED_KEYS = Set.of(KEY_STATUS, KEY_MOTION);
    private static final Set<String> ALLOWED_STATUSES = Set.of("STREAMING", "RECORDING", "IDLE", "OFF");
    private static final Set<String> ALLOWED_MOTIONS = Set.of("DETECTED", "NONE");

    public SimulatedCamera(String id, String name, String room) {
        super(id, name, DeviceType.CAMERA, room);
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

        if (KEY_STATUS.equalsIgnoreCase(key)) {
            if (!ALLOWED_STATUSES.contains(val)) {
                throw new IllegalArgumentException("Camera status must be one of " + ALLOWED_STATUSES + ", but was: " + value);
            }
        } else if (KEY_MOTION.equalsIgnoreCase(key)) {
            if (!ALLOWED_MOTIONS.contains(val)) {
                throw new IllegalArgumentException("Camera motion must be one of " + ALLOWED_MOTIONS + ", but was: " + value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported state key '" + stateKey + "' for Camera");
        }
    }

    @Override
    public Map<String, String> getDefaultState() {
        return Map.of(
                KEY_STATUS, "IDLE",
                KEY_MOTION, "NONE"
        );
    }
}

