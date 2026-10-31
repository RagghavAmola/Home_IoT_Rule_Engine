package com.smarthome.dao.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smarthome.domain.DeviceStateKey;

class JdbcDeviceStateDaoTest {

    private JdbcDeviceStateDao stateDao;
    // private JdbcDeviceDao deviceDao;

    @BeforeEach
    void setUp() throws IOException {
        File tempDb = File.createTempFile("test-state-" + UUID.randomUUID(), ".db");
        tempDb.deleteOnExit();
        String url = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider(url, "", "");
        DatabaseInitializer initializer = new DatabaseInitializer(connectionProvider);
        initializer.initialize(true);

        stateDao = new JdbcDeviceStateDao(connectionProvider);
        // deviceDao = new JdbcDeviceDao(connectionProvider);
    }

    @Test
    @DisplayName("Single upsert and findStateValue")
    void testSingleUpsertAndFind() {
        stateDao.upsertState("light-living-room", "power", "ON");
        Optional<String> val = stateDao.findStateValue("light-living-room", "power");
        assertTrue(val.isPresent());
        assertEquals("ON", val.get());

        // Update value
        stateDao.upsertState("light-living-room", "power", "OFF");
        assertEquals("OFF", stateDao.findStateValue("light-living-room", "power").get());
    }

    @Test
    @DisplayName("Batch upsert states across multiple devices")
    void testBatchUpsert() {
        DeviceStateKey k1 = new DeviceStateKey("light-living-room", "brightness");
        DeviceStateKey k2 = new DeviceStateKey("thermostat-living-room", "temperature");
        DeviceStateKey k3 = new DeviceStateKey("motion-sensor-hallway", "motion");

        Map<DeviceStateKey, String> batch = Map.of(
                k1, "90",
                k2, "23.5",
                k3, "DETECTED"
        );

        stateDao.batchUpsertStates(batch);

        assertEquals("90", stateDao.findStateValue("light-living-room", "brightness").get());
        assertEquals("23.5", stateDao.findStateValue("thermostat-living-room", "temperature").get());
        assertEquals("DETECTED", stateDao.findStateValue("motion-sensor-hallway", "motion").get());
    }

    @Test
    @DisplayName("findAllStates snapshot and findStatesByDeviceId")
    void testFindAllAndByDeviceId() {
        Map<String, String> thermoStates = stateDao.findStatesByDeviceId("thermostat-living-room");
        assertFalse(thermoStates.isEmpty());
        assertTrue(thermoStates.containsKey("temperature"));
        assertTrue(thermoStates.containsKey("mode"));

        Map<DeviceStateKey, String> allSnapshot = stateDao.findAllStates();
        assertFalse(allSnapshot.isEmpty());
        assertTrue(allSnapshot.containsKey(new DeviceStateKey("thermostat-living-room", "temperature")));
    }

    @Test
    @DisplayName("Delete state key and delete states by device ID")
    void testDeleteStates() {
        stateDao.upsertState("light-living-room", "color_temp", "4000K");
        assertTrue(stateDao.findStateValue("light-living-room", "color_temp").isPresent());

        boolean deleted = stateDao.deleteState("light-living-room", "color_temp");
        assertTrue(deleted);
        assertFalse(stateDao.findStateValue("light-living-room", "color_temp").isPresent());

        int deletedCount = stateDao.deleteStatesByDeviceId("light-living-room");
        assertTrue(deletedCount > 0);
        assertTrue(stateDao.findStatesByDeviceId("light-living-room").isEmpty());
    }
}

