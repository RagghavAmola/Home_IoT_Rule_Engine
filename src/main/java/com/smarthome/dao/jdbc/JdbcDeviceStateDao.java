package com.smarthome.dao.jdbc;

import com.smarthome.dao.DeviceStateDao;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link DeviceStateDao} managing device state telemetry.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless DAO relying on the thread-safe {@link ConnectionProvider}.
 * All operations use local try-with-resources.
 * </p>
 */
public class JdbcDeviceStateDao implements DeviceStateDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDeviceStateDao.class);

    private static final String UPSERT_SQL =
            "INSERT INTO device_state (device_id, state_key, state_value, updated_at) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(device_id, state_key) DO UPDATE SET " +
            "state_value = excluded.state_value, updated_at = excluded.updated_at";

    private final ConnectionProvider connectionProvider;

    public JdbcDeviceStateDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    @Override
    public Optional<String> findStateValue(String deviceId, String stateKey) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");

        String sql = "SELECT state_value FROM device_state WHERE device_id = ? AND state_key = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, deviceId.trim());
            stmt.setString(2, stateKey.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("state_value"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            logger.error("Failed to query state for {}.{}: {}", deviceId, stateKey, e.getMessage(), e);
            throw new DataAccessException("Failed to query device state", e);
        }
    }

    @Override
    public Map<String, String> findStatesByDeviceId(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");

        String sql = "SELECT state_key, state_value FROM device_state WHERE device_id = ? ORDER BY state_key ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, deviceId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, String> states = new LinkedHashMap<>();
                while (rs.next()) {
                    states.put(rs.getString("state_key"), rs.getString("state_value"));
                }
                return states;
            }
        } catch (SQLException e) {
            logger.error("Failed to query states for device {}: {}", deviceId, e.getMessage(), e);
            throw new DataAccessException("Failed to query device states", e);
        }
    }

    @Override
    public Map<DeviceStateKey, String> findAllStates() {
        String sql = "SELECT device_id, state_key, state_value FROM device_state ORDER BY device_id, state_key ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            Map<DeviceStateKey, String> snapshot = new LinkedHashMap<>();
            while (rs.next()) {
                String devId = rs.getString("device_id");
                String key = rs.getString("state_key");
                String value = rs.getString("state_value");
                snapshot.put(new DeviceStateKey(devId, key), value);
            }
            return snapshot;
        } catch (SQLException e) {
            logger.error("Failed to query all device states: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to query all device states", e);
        }
    }

    @Override
    public void upsertState(String deviceId, String stateKey, String stateValue) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");
        Objects.requireNonNull(stateValue, "stateValue cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {

            stmt.setString(1, deviceId.trim());
            stmt.setString(2, stateKey.trim());
            stmt.setString(3, stateValue.trim());
            stmt.setString(4, Instant.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to upsert state for {}.{}: {}", deviceId, stateKey, e.getMessage(), e);
            throw new DataAccessException("Failed to upsert device state", e);
        }
    }

    @Override
    public void batchUpsertStates(Map<DeviceStateKey, String> states) {
        Objects.requireNonNull(states, "states map cannot be null");
        if (states.isEmpty()) {
            return;
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {

            String now = Instant.now().toString();
            for (Map.Entry<DeviceStateKey, String> entry : states.entrySet()) {
                DeviceStateKey key = entry.getKey();
                String val = entry.getValue();
                if (key != null && val != null) {
                    stmt.setString(1, key.deviceId());
                    stmt.setString(2, key.stateKey());
                    stmt.setString(3, val.trim());
                    stmt.setString(4, now);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logger.error("Failed to execute batch state upsert: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to batch upsert states", e);
        }
    }

    @Override
    public boolean deleteState(String deviceId, String stateKey) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(stateKey, "stateKey cannot be null");

        String sql = "DELETE FROM device_state WHERE device_id = ? AND state_key = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, deviceId.trim());
            stmt.setString(2, stateKey.trim());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete state {}.{}: {}", deviceId, stateKey, e.getMessage(), e);
            throw new DataAccessException("Failed to delete device state", e);
        }
    }

    @Override
    public int deleteStatesByDeviceId(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");

        String sql = "DELETE FROM device_state WHERE device_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, deviceId.trim());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete states for device {}: {}", deviceId, e.getMessage(), e);
            throw new DataAccessException("Failed to delete states for device", e);
        }
    }
}

