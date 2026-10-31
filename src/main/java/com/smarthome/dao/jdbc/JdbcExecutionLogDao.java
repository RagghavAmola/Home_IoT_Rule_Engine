package com.smarthome.dao.jdbc;

import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.RuleExecutionRecord;
import com.smarthome.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC implementation of {@link ExecutionLogDao}.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless DAO relying on the thread-safe {@link ConnectionProvider}.
 * All operations execute within connection-scoped PreparedStatement blocks.
 * </p>
 */
public class JdbcExecutionLogDao implements ExecutionLogDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcExecutionLogDao.class);

    private final ConnectionProvider connectionProvider;

    public JdbcExecutionLogDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    @Override
    public void saveExecutionRecord(RuleExecutionRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        String sql = "INSERT INTO execution_log (id, rule_id, executed_at, outcome, details) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String outcome = record.conditionSatisfied() ? "TRIGGERED" : "EVALUATED_FALSE";
            String details = serializeExecutionDetails(record);

            stmt.setString(1, record.id());
            if (record.ruleId() != null && !record.ruleId().isBlank()) {
                stmt.setString(2, record.ruleId().trim());
            } else {
                stmt.setNull(2, java.sql.Types.VARCHAR);
            }
            stmt.setString(3, record.timestamp().toString());
            stmt.setString(4, outcome);
            stmt.setString(5, details);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save execution record id={}: {}", record.id(), e.getMessage(), e);
            throw new DataAccessException("Failed to persist execution log", e);
        }
    }

    @Override
    public List<RuleExecutionRecord> findExecutionRecordsByRuleId(String ruleId, int limit) {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        String sql = "SELECT id, rule_id, executed_at, outcome, details FROM execution_log " +
                     "WHERE rule_id = ? ORDER BY executed_at DESC LIMIT ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ruleId.trim());
            stmt.setInt(2, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                List<RuleExecutionRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(hydrateExecutionRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            logger.error("Failed to query execution logs for rule {}: {}", ruleId, e.getMessage(), e);
            throw new DataAccessException("Failed to query execution logs", e);
        }
    }

    @Override
    public List<RuleExecutionRecord> findRecentExecutionRecords(int limit) {
        String sql = "SELECT id, rule_id, executed_at, outcome, details FROM execution_log " +
                     "ORDER BY executed_at DESC LIMIT ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                List<RuleExecutionRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(hydrateExecutionRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            logger.error("Failed to query recent execution records: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to query recent execution records", e);
        }
    }

    @Override
    public void saveOverrideRecord(OverrideRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        String sql = "INSERT INTO override_log (id, winning_rule_id, dropped_rule_id, device_id, state_key, occurred_at, reason) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String encodedReason = serializeOverrideDetails(record);

            stmt.setString(1, record.id());
            if (record.winningRuleId() != null && !record.winningRuleId().isBlank()) {
                stmt.setString(2, record.winningRuleId().trim());
            } else {
                stmt.setNull(2, java.sql.Types.VARCHAR);
            }
            if (record.overriddenRuleId() != null && !record.overriddenRuleId().isBlank()) {
                stmt.setString(3, record.overriddenRuleId().trim());
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }
            stmt.setString(4, record.targetKey().deviceId());
            stmt.setString(5, record.targetKey().stateKey());
            stmt.setString(6, record.timestamp().toString());
            stmt.setString(7, encodedReason);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save override record id={}: {}", record.id(), e.getMessage(), e);
            throw new DataAccessException("Failed to persist override log", e);
        }
    }

    @Override
    public List<OverrideRecord> findRecentOverrideRecords(int limit) {
        String sql = "SELECT id, winning_rule_id, dropped_rule_id, device_id, state_key, occurred_at, reason " +
                     "FROM override_log ORDER BY occurred_at DESC LIMIT ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                List<OverrideRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(hydrateOverrideRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            logger.error("Failed to query recent override records: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to query override records", e);
        }
    }

    @Override
    public int clearLogsOlderThan(Instant threshold) {
        Objects.requireNonNull(threshold, "threshold cannot be null");
        String isoTime = threshold.toString();

        int deleted = 0;
        String sqlExec = "DELETE FROM execution_log WHERE executed_at < ?";
        String sqlOver = "DELETE FROM override_log WHERE occurred_at < ?";

        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlExec)) {
                stmt.setString(1, isoTime);
                deleted += stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sqlOver)) {
                stmt.setString(1, isoTime);
                deleted += stmt.executeUpdate();
            }
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to clear logs older than {}: {}", threshold, e.getMessage(), e);
            throw new DataAccessException("Failed to clear old logs", e);
        }
    }

    private String serializeExecutionDetails(RuleExecutionRecord record) {
        return "name=" + sanitize(record.ruleName()) +
               ";tier=" + record.tier().name() +
               ";msg=" + sanitize(record.message());
    }

    private RuleExecutionRecord hydrateExecutionRecord(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String ruleId = rs.getString("rule_id") != null ? rs.getString("rule_id") : "UNKNOWN";
        Instant time = Instant.parse(rs.getString("executed_at"));
        String outcome = rs.getString("outcome");
        boolean satisfied = "TRIGGERED".equalsIgnoreCase(outcome);
        String details = rs.getString("details");

        String ruleName = ruleId;
        PriorityTier tier = PriorityTier.COMFORT;
        String message = "";

        if (details != null && !details.isEmpty()) {
            String[] parts = details.split(";");
            for (String part : parts) {
                if (part.startsWith("name=")) {
                    ruleName = desanitize(part.substring(5));
                } else if (part.startsWith("tier=")) {
                    try {
                        tier = PriorityTier.valueOf(part.substring(5));
                    } catch (Exception ignored) {}
                } else if (part.startsWith("msg=")) {
                    message = desanitize(part.substring(4));
                }
            }
        }

        return new RuleExecutionRecord(
                id,
                ruleId,
                ruleName,
                tier,
                time,
                satisfied,
                Collections.emptyList(),
                Collections.emptyMap(),
                message
        );
    }

    private String serializeOverrideDetails(OverrideRecord record) {
        return "winningTier=" + record.winningTier().name() +
               ";overriddenTier=" + record.overriddenTier().name() +
               ";overriddenVal=" + sanitize(record.overriddenTargetValue()) +
               ";appliedVal=" + sanitize(record.appliedTargetValue()) +
               ";reason=" + sanitize(record.reason());
    }

    private OverrideRecord hydrateOverrideRecord(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String winningRuleId = rs.getString("winning_rule_id") != null ? rs.getString("winning_rule_id") : "UNKNOWN";
        String droppedRuleId = rs.getString("dropped_rule_id") != null ? rs.getString("dropped_rule_id") : "UNKNOWN";
        String deviceId = rs.getString("device_id");
        String stateKey = rs.getString("state_key");
        Instant time = Instant.parse(rs.getString("occurred_at"));
        String reasonStr = rs.getString("reason");

        PriorityTier winTier = PriorityTier.SAFETY;
        PriorityTier ovTier = PriorityTier.COMFORT;
        String ovVal = "";
        String appVal = "";
        String reason = "";

        if (reasonStr != null && !reasonStr.isEmpty()) {
            String[] parts = reasonStr.split(";");
            for (String part : parts) {
                if (part.startsWith("winningTier=")) {
                    try { winTier = PriorityTier.valueOf(part.substring(12)); } catch (Exception ignored) {}
                } else if (part.startsWith("overriddenTier=")) {
                    try { ovTier = PriorityTier.valueOf(part.substring(15)); } catch (Exception ignored) {}
                } else if (part.startsWith("overriddenVal=")) {
                    ovVal = desanitize(part.substring(14));
                } else if (part.startsWith("appliedVal=")) {
                    appVal = desanitize(part.substring(11));
                } else if (part.startsWith("reason=")) {
                    reason = desanitize(part.substring(7));
                }
            }
        }

        return new OverrideRecord(
                id,
                winningRuleId,
                droppedRuleId,
                winTier,
                ovTier,
                new DeviceStateKey(deviceId, stateKey),
                ovVal,
                appVal,
                time,
                reason
        );
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace(";", "%3B").replace("=", "%3D");
    }

    private String desanitize(String input) {
        if (input == null) return "";
        return input.replace("%3B", ";").replace("%3D", "=");
    }
}

