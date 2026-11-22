package com.smarthome.simulation;

import com.smarthome.domain.DeviceStateKey;

/**
 * Functional listener interface receiving notifications whenever a device state key is updated.
 */
@FunctionalInterface
public interface StateChangeListener {

    /**
     * Invoked when a device state attribute is modified in the state store.
     *
     * @param key      the device state key that changed
     * @param oldValue the previous value, or null if previously unset
     * @param newValue the updated value
     */
    void onStateChanged(DeviceStateKey key, String oldValue, String newValue);
}

