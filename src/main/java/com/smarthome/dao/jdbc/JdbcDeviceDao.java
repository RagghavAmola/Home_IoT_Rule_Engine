package com.smarthome.dao.jdbc;

import com.smarthome.dao.DeviceDao;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;
import com.smarthome.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link DeviceDao}.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless DAO relying on the thread-safe {@link ConnectionProvider}.
 * Each operation uses local try-with-resources for connections and prepared statements.
 * </p>
 */
public class JdbcDeviceDao implements DeviceDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDeviceDao.class);

    private final ConnectionProvider connectionProvider;

    public JdbcDeviceDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    @Override
    public Device save(Device device) {
        Objects.requireNonNull(device, "device cannot be null");

        String checkSql = "SELECT 1 FROM device WHERE id = ?";
        String insertSql = "INSERT INTO device (id, name, type_id, room) VALUES (?, ?, ?, ?)";
        String updateSql = "UPDATE device SET name = ?, type_id = ?, room = ? WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection()) {
            boolean exists;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, device.getId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, device.getName());
                    updateStmt.setString(2, device.getType().name());
                    updateStmt.setString(3, device.getRoom());
                    updateStmt.setString(4, device.getId());
                    updateStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, device.getId());
                    insertStmt.setString(2, device.getName());
                    insertStmt.setString(3, device.getType().name());
                    insertStmt.setString(4, device.getRoom());
                    insertStmt.executeUpdate();
                }
            }
            return device;
        } catch (SQLException e) {
            logger.error("Failed to save device with id={}: {}", device.getId(), e.getMessage(), e);
            throw new DataAccessException("Failed to save device: " + device.getId(), e);
        }
    }

    @Override
    public Optional<Device> findById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        String sql = "SELECT id, name, type_id, room FROM device WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String name = rs.getString("name");
                DeviceType type = DeviceType.valueOf(rs.getString("type_id"));
                String room = rs.getString("room");

                Map<String, String> states = loadDeviceStates(conn, id.trim());
                return Optional.of(new Device(id.trim(), name, type, room, states));
            }
        } catch (SQLException e) {
            logger.error("Failed to find device by id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to query device by id: " + id, e);
        }
    }

    @Override
    public List<Device> findAll() {
        String sql = "SELECT id, name, type_id, room FROM device ORDER BY id ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Device> devices = new ArrayList<>();
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                DeviceType type = DeviceType.valueOf(rs.getString("type_id"));
                String room = rs.getString("room");
                Map<String, String> states = loadDeviceStates(conn, id);
                devices.add(new Device(id, name, type, room, states));
            }
            return devices;
        } catch (SQLException e) {
            logger.error("Failed to retrieve all devices: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to retrieve all devices", e);
        }
    }

    @Override
    public List<Device> findByRoom(String room) {
        Objects.requireNonNull(room, "room cannot be null");

        String sql = "SELECT id, name, type_id, room FROM device WHERE room = ? ORDER BY id ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, room.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                List<Device> devices = new ArrayList<>();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    DeviceType type = DeviceType.valueOf(rs.getString("type_id"));
                    String r = rs.getString("room");
                    Map<String, String> states = loadDeviceStates(conn, id);
                    devices.add(new Device(id, name, type, r, states));
                }
                return devices;
            }
        } catch (SQLException e) {
            logger.error("Failed to find devices by room={}: {}", room, e.getMessage(), e);
            throw new DataAccessException("Failed to find devices by room: " + room, e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        String sql = "DELETE FROM device WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.trim());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete device id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to delete device: " + id, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        String sql = "SELECT 1 FROM device WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Failed to check device existence id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to check device existence: " + id, e);
        }
    }

    private Map<String, String> loadDeviceStates(Connection conn, String deviceId) throws SQLException {
        String sql = "SELECT state_key, state_value FROM device_state WHERE device_id = ? ORDER BY state_key ASC";
        Map<String, String> stateMap = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, deviceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stateMap.put(rs.getString("state_key"), rs.getString("state_value"));
                }
            }
        }
        return stateMap;
    }
}

