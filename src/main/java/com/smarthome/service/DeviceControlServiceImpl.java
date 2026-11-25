package com.smarthome.service;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.DeviceStateDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.simulation.DeviceStateStore;
import com.smarthome.simulation.SimulatedDeviceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of DeviceControlService orchestrating live state store and persistent updates.
 */
public class DeviceControlServiceImpl implements DeviceControlService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceControlServiceImpl.class);

    private final DeviceDao deviceDao;
    private final DeviceStateDao deviceStateDao;
    private final DeviceStateStore stateStore;

    public DeviceControlServiceImpl(
            DeviceDao deviceDao,
            DeviceStateDao deviceStateDao,
            DeviceStateStore stateStore
    ) {
        this.deviceDao = deviceDao;
        this.deviceStateDao = deviceStateDao;
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore cannot be null");
    }

    @Override
    public List<Device> getAllDevices() {
        if (deviceDao != null) {
            List<Device> devices = deviceDao.findAll();
            if (!devices.isEmpty()) {
                return devices;
            }
        }
        // Fallback to simulated devices registered in state store
        List<Device> fallback = new ArrayList<>();
        stateStore.getRegisteredDevices().forEach(sim -> fallback.add(sim.toDevice(null)));
        return Collections.unmodifiableList(fallback);
    }

    @Override
    public Optional<Device> getDevice(String deviceId) {
        if (deviceId == null) return Optional.empty();
        if (deviceDao != null) {
            Optional<Device> device = deviceDao.findById(deviceId);
            if (device.isPresent()) return device;
        }
        var sim = stateStore.getDevice(deviceId);
        return sim != null ? Optional.of(sim.toDevice(null)) : Optional.empty();
    }

    @Override
    public Map<DeviceStateKey, String> getAllStates() {
        return stateStore.snapshot().asMap();
    }

    @Override
    public String getState(String deviceId, String stateKey) {
        return stateStore.getState(deviceId, stateKey);
    }

    @Override
    public void updateState(String deviceId, String stateKey, String value) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        logger.debug("Manual state update: {}.{} = '{}'", deviceId, stateKey, value);
        stateStore.setState(deviceId, stateKey, value);

        if (deviceStateDao != null) {
            try {
                deviceStateDao.upsertState(deviceId, stateKey, value);
            } catch (Exception e) {
                logger.error("Failed to persist state update to database for {}.{}: {}", deviceId, stateKey, e.getMessage(), e);
            }
        }
    }

    @Override
    public void executeAction(Action action) {
        Objects.requireNonNull(action, "action cannot be null");
        logger.debug("Manual action execution: {}", action);
        stateStore.applyAction(action);

        if (deviceStateDao != null) {
            try {
                deviceStateDao.upsertState(action.deviceId(), action.stateKey(), action.targetValue());
            } catch (Exception e) {
                logger.error("Failed to persist action execution to database for {}: {}", action, e.getMessage(), e);
            }
        }
    }

    @Override
    public void registerDevice(Device device) {
        Objects.requireNonNull(device, "device cannot be null");
        stateStore.registerDevice(SimulatedDeviceFactory.create(device));
        if (deviceDao != null) {
            try {
                if (!deviceDao.existsById(device.getId())) {
                    deviceDao.save(device);
                }
            } catch (Exception e) {
                logger.warn("Could not save registered device to DAO: {}", e.getMessage());
            }
        }
    }
}

