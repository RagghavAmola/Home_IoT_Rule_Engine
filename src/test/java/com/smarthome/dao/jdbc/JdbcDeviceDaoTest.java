package com.smarthome.dao.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;

class JdbcDeviceDaoTest {

    private JdbcConnectionProvider connectionProvider;
    private JdbcDeviceDao deviceDao;
    private JdbcDeviceStateDao stateDao;

    @BeforeEach
    void setUp() throws IOException {
        File tempDb = File.createTempFile("test-device-" + UUID.randomUUID(), ".db");
        tempDb.deleteOnExit();
        String url = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        connectionProvider = new JdbcConnectionProvider(url, "", "");
        DatabaseInitializer initializer = new DatabaseInitializer(connectionProvider);
        initializer.initialize(true);

        deviceDao = new JdbcDeviceDao(connectionProvider);
        stateDao = new JdbcDeviceStateDao(connectionProvider);
    }

    @Test
    @DisplayName("Device CRUD and state loading")
    void testDeviceCrud() {
        Device newDevice = new Device("dev-test-1", "Porch Light", DeviceType.LIGHT, "Porch", Map.of("power", "OFF"));
        deviceDao.save(newDevice);

        assertTrue(deviceDao.existsById("dev-test-1"));

        // Upsert state and verify findById loads state
        stateDao.upsertState("dev-test-1", "power", "ON");
        stateDao.upsertState("dev-test-1", "brightness", "75");

        Optional<Device> loadedOpt = deviceDao.findById("dev-test-1");
        assertTrue(loadedOpt.isPresent());
        Device loaded = loadedOpt.get();
        assertEquals("dev-test-1", loaded.getId());
        assertEquals("Porch Light", loaded.getName());
        assertEquals(DeviceType.LIGHT, loaded.getType());
        assertEquals("Porch", loaded.getRoom());
        assertEquals("ON", loaded.getStateValue("power"));
        assertEquals("75", loaded.getStateValue("brightness"));

        // Update device metadata
        Device updated = new Device("dev-test-1", "Front Porch Light", DeviceType.LIGHT, "Front Entrance");
        deviceDao.save(updated);

        Device reloaded = deviceDao.findById("dev-test-1").orElseThrow();
        assertEquals("Front Porch Light", reloaded.getName());
        assertEquals("Front Entrance", reloaded.getRoom());
    }

    @Test
    @DisplayName("Query devices by room and query all")
    void testFindByRoomAndAll() {
        List<Device> all = deviceDao.findAll();
        assertFalse(all.isEmpty(), "Seed data should populate initial devices");

        List<Device> livingRoom = deviceDao.findByRoom("Living Room");
        assertFalse(livingRoom.isEmpty());
        assertTrue(livingRoom.stream().allMatch(d -> "Living Room".equals(d.getRoom())));
    }

    @Test
    @DisplayName("Cascade delete: deleting device cascades to device_state")
    void testCascadeDelete() throws SQLException {
        Device d = new Device("dev-cascade-test", "Test Sensor", DeviceType.MOTION_SENSOR, "Garage");
        deviceDao.save(d);
        stateDao.upsertState("dev-cascade-test", "motion", "DETECTED");

        assertTrue(deviceDao.existsById("dev-cascade-test"));
        assertTrue(stateDao.findStateValue("dev-cascade-test", "motion").isPresent());

        // Delete device
        boolean deleted = deviceDao.deleteById("dev-cascade-test");
        assertTrue(deleted);
        assertFalse(deviceDao.existsById("dev-cascade-test"));

        // Verify device_state row was cascaded
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM device_state WHERE device_id = ?")) {
            stmt.setString(1, "dev-cascade-test");
            try (ResultSet rs = stmt.executeQuery()) {
                assertFalse(rs.next(), "Device states must be deleted via foreign key cascade");
            }
        }
    }
}

