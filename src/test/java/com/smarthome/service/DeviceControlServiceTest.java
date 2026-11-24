package com.smarthome.service;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.DeviceStateDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;
import com.smarthome.simulation.DeviceStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceControlServiceTest {

    private DeviceDao deviceDao;
    private DeviceStateDao deviceStateDao;
    private DeviceStateStore stateStore;
    private DeviceControlService controlService;

    @BeforeEach
    void setUp() {
        deviceDao = Mockito.mock(DeviceDao.class);
        deviceStateDao = Mockito.mock(DeviceStateDao.class);
        stateStore = new DeviceStateStore();
        controlService = new DeviceControlServiceImpl(deviceDao, deviceStateDao, stateStore);
    }

    @Test
    @DisplayName("Queries devices and states properly")
    void testQueryDevicesAndStates() {
        Device dev = new Device("light-1", "Ceiling Light", DeviceType.LIGHT, "Living Room");
        when(deviceDao.findAll()).thenReturn(List.of(dev));
        when(deviceDao.findById("light-1")).thenReturn(Optional.of(dev));

        List<Device> all = controlService.getAllDevices();
        assertEquals(1, all.size());
        assertEquals("light-1", all.get(0).getId());

        Optional<Device> found = controlService.getDevice("light-1");
        assertTrue(found.isPresent());
        assertEquals("Ceiling Light", found.get().getName());
    }

    @Test
    @DisplayName("updateState mutates in-memory store and updates persistent state DAO")
    void testUpdateState() {
        controlService.updateState("light-1", "power", "ON");

        assertEquals("ON", stateStore.getState("light-1", "power"));
        assertEquals("ON", controlService.getState("light-1", "power"));
        verify(deviceStateDao).upsertState(eq("light-1"), eq("power"), eq("ON"));
    }

    @Test
    @DisplayName("executeAction mutates in-memory store and persists to DAO")
    void testExecuteAction() {
        Action action = new Action("heater-1", "power", "OFF");
        controlService.executeAction(action);

        assertEquals("OFF", stateStore.getState("heater-1", "power"));
        verify(deviceStateDao).upsertState(eq("heater-1"), eq("power"), eq("OFF"));
    }

    @Test
    @DisplayName("registerDevice registers simulated device in store and DAO")
    void testRegisterDevice() {
        Device dev = new Device("fan-1", "Ceiling Fan", DeviceType.SMART_PLUG, "Bedroom");
        when(deviceDao.existsById("fan-1")).thenReturn(false);

        controlService.registerDevice(dev);

        assertNotNull(stateStore.getDevice("fan-1"));
        verify(deviceDao).save(dev);
    }
}

