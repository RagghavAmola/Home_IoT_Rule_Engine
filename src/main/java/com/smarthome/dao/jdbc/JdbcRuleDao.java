package com.smarthome.dao.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.Condition;
import com.smarthome.domain.ConditionGroup;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.exception.DataAccessException;

/**
 * JDBC implementation of {@link RuleDao}.
 * Handles full transactional persistence and recursive reconstruction of hierarchical condition trees.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless DAO relying on the thread-safe {@link ConnectionProvider}.
 * All operations execute within transactional or connection-scoped PreparedStatement blocks.
 * </p>
 */
public class JdbcRuleDao implements RuleDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcRuleDao.class);
    private static final String SYNTHETIC_LEAF_PREFIX = "leaf_group_";

    private final ConnectionProvider connectionProvider;

    public JdbcRuleDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
    }

    @Override
    public Rule save(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");

        try (Connection conn = connectionProvider.getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            if (wasAutoCommit) {
                conn.setAutoCommit(false);
            }
            try {
                boolean exists = existsRule(conn, rule.getId());

                if (exists) {
                    // Detach root group foreign key to allow clean group replacement
                    try (PreparedStatement nullRootStmt = conn.prepareStatement(
                            "UPDATE rule SET root_group_id = NULL WHERE id = ?")) {
                        nullRootStmt.setString(1, rule.getId());
                        nullRootStmt.executeUpdate();
                    }
                    // Delete old groups (cascades to leaves) and actions
                    try (PreparedStatement delGroups = conn.prepareStatement(
                            "DELETE FROM condition_group WHERE rule_id = ?")) {
                        delGroups.setString(1, rule.getId());
                        delGroups.executeUpdate();
                    }
                    try (PreparedStatement delActions = conn.prepareStatement(
                            "DELETE FROM rule_action WHERE rule_id = ?")) {
                        delActions.setString(1, rule.getId());
                        delActions.executeUpdate();
                    }
                    // Update rule attributes
                    try (PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE rule SET name = ?, enabled = ?, tier_id = ?, tie_break_order = ?, created_at = ? WHERE id = ?")) {
                        updateStmt.setString(1, rule.getName());
                        updateStmt.setBoolean(2, rule.isEnabled());
                        updateStmt.setString(3, rule.getTier().name());
                        updateStmt.setInt(4, rule.getTieBreakOrder());
                        updateStmt.setString(5, rule.getCreatedAt().toString());
                        updateStmt.setString(6, rule.getId());
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert initial rule record with null root group
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO rule (id, name, enabled, tier_id, tie_break_order, root_group_id, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, NULL, ?)")) {
                        insertStmt.setString(1, rule.getId());
                        insertStmt.setString(2, rule.getName());
                        insertStmt.setBoolean(3, rule.isEnabled());
                        insertStmt.setString(4, rule.getTier().name());
                        insertStmt.setInt(5, rule.getTieBreakOrder());
                        insertStmt.setString(6, rule.getCreatedAt().toString());
                        insertStmt.executeUpdate();
                    }
                }

                // Recursively persist condition hierarchy
                String rootGroupId = persistConditionTree(conn, rule.getId(), null,0, rule.getRootCondition());

                // Update rule with the persisted root group ID
                try (PreparedStatement updateRootStmt = conn.prepareStatement(
                        "UPDATE rule SET root_group_id = ? WHERE id = ?")) {
                    updateRootStmt.setString(1, rootGroupId);
                    updateRootStmt.setString(2, rule.getId());
                    updateRootStmt.executeUpdate();
                }

                // Persist actions
                List<Action> actions = rule.getActions();
                if (!actions.isEmpty()) {
                    String actionSql = "INSERT INTO rule_action (id, rule_id, device_id, state_key, target_value, seq_no) " +
                                       "VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement actStmt = conn.prepareStatement(actionSql)) {
                        for (int i = 0; i < actions.size(); i++) {
                            Action action = actions.get(i);
                            actStmt.setString(1, UUID.randomUUID().toString());
                            actStmt.setString(2, rule.getId());
                            actStmt.setString(3, action.deviceId());
                            actStmt.setString(4, action.stateKey());
                            actStmt.setString(5, action.targetValue());
                            actStmt.setInt(6, i);
                            actStmt.addBatch();
                        }
                        actStmt.executeBatch();
                    }
                }

                if (wasAutoCommit) {
                    conn.commit();
                }
                return rule;
            } catch (Exception e) {
                if (wasAutoCommit) {
                    conn.rollback();
                }
                throw e;
            } finally {
                if (wasAutoCommit) {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save rule id={}: {}", rule.getId(), e.getMessage(), e);
            throw new DataAccessException("Failed to persist rule: " + rule.getId(), e);
        }
    }

    @Override
    public Optional<Rule> findById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        String sql = "SELECT id, name, enabled, tier_id, tie_break_order, root_group_id, created_at " +
                     "FROM rule WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(hydrateRule(conn, rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to find rule by id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to query rule: " + id, e);
        }
    }

    @Override
    public List<Rule> findAll() {
        String sql = "SELECT id, name, enabled, tier_id, tie_break_order, root_group_id, created_at " +
                     "FROM rule ORDER BY created_at ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Rule> rules = new ArrayList<>();
            while (rs.next()) {
                rules.add(hydrateRule(conn, rs));
            }
            return rules;
        } catch (SQLException e) {
            logger.error("Failed to query all rules: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to retrieve all rules", e);
        }
    }

    @Override
    public List<Rule> findAllEnabledRules() {
        String sql = "SELECT r.id, r.name, r.enabled, r.tier_id, r.tie_break_order, r.root_group_id, r.created_at " +
                     "FROM rule r " +
                     "JOIN priority_tier pt ON r.tier_id = pt.id " +
                     "WHERE r.enabled = 1 " +
                     "ORDER BY pt.tier_rank ASC, r.tie_break_order ASC, r.created_at ASC, r.id ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Rule> rules = new ArrayList<>();
            while (rs.next()) {
                rules.add(hydrateRule(conn, rs));
            }
            return rules;
        } catch (SQLException e) {
            logger.error("Failed to retrieve enabled rules: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to retrieve enabled rules", e);
        }
    }

    @Override
    public List<Rule> findByDeviceId(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");

        String sql = "SELECT DISTINCT r.id, r.name, r.enabled, r.tier_id, r.tie_break_order, r.root_group_id, r.created_at " +
                     "FROM rule r " +
                     "LEFT JOIN condition_group cg ON cg.rule_id = r.id " +
                     "LEFT JOIN condition_leaf cl ON cl.group_id = cg.id " +
                     "LEFT JOIN rule_action ra ON ra.rule_id = r.id " +
                     "WHERE cl.device_id = ? OR ra.device_id = ? " +
                     "ORDER BY r.created_at ASC";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, deviceId.trim());
            stmt.setString(2, deviceId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                List<Rule> rules = new ArrayList<>();
                while (rs.next()) {
                    rules.add(hydrateRule(conn, rs));
                }
                return rules;
            }
        } catch (SQLException e) {
            logger.error("Failed to find rules by deviceId={}: {}", deviceId, e.getMessage(), e);
            throw new DataAccessException("Failed to query rules for device: " + deviceId, e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        String sql = "DELETE FROM rule WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.trim());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete rule id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to delete rule: " + id, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        try (Connection conn = connectionProvider.getConnection()) {
            return existsRule(conn, id.trim());
        } catch (SQLException e) {
            logger.error("Failed to check rule existence id={}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Failed to check rule existence: " + id, e);
        }
    }

    private boolean existsRule(Connection conn, String id) throws SQLException {
        String sql = "SELECT 1 FROM rule WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String persistConditionTree(Connection conn, String ruleId, String parentGroupId,
                                    int position, Condition condition) throws SQLException {
    if (condition instanceof LeafCondition leaf) {
        if (parentGroupId != null) {
            insertLeaf(conn, parentGroupId, position, leaf);
            return null;
        }
        // Standalone root leaf wrapped in synthetic group
        String syntheticGroupId = SYNTHETIC_LEAF_PREFIX + UUID.randomUUID();
        insertGroup(conn, syntheticGroupId, ruleId, null, LogicalOperator.AND, 0);
        insertLeaf(conn, syntheticGroupId, 0, leaf);
        return syntheticGroupId;
    }

    if (condition instanceof ConditionGroup group) {
        String groupId = UUID.randomUUID().toString();
        insertGroup(conn, groupId, ruleId, parentGroupId, group.operator(), position);
        List<Condition> children = group.children();
        for (int i = 0; i < children.size(); i++) {
            persistConditionTree(conn, ruleId, groupId, i, children.get(i));
        }
        return groupId;
    }

    throw new IllegalArgumentException("Unknown condition type: " + condition.getClass());
}

private void insertGroup(Connection conn, String groupId, String ruleId, String parentGroupId,
                         LogicalOperator operator, int position) throws SQLException {
    String sql = "INSERT INTO condition_group (id, rule_id, parent_group_id, logical_operator, position) " +
                 "VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, groupId);
        stmt.setString(2, ruleId);
        if (parentGroupId != null) {
            stmt.setString(3, parentGroupId);
        } else {
            stmt.setNull(3, Types.VARCHAR);
        }
        stmt.setString(4, operator.name());
        stmt.setInt(5, position);
        stmt.executeUpdate();
    }
}

private void insertLeaf(Connection conn, String groupId, int position, LeafCondition leaf)
        throws SQLException {
    String sql = "INSERT INTO condition_leaf (id, group_id, device_id, state_key, operator, compare_value, position) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, UUID.randomUUID().toString());
        stmt.setString(2, groupId);
        stmt.setString(3, leaf.deviceId());
        stmt.setString(4, leaf.stateKey());
        stmt.setString(5, leaf.operator().name());
        stmt.setString(6, leaf.value());
        stmt.setInt(7, position);
        stmt.executeUpdate();
    }
}

    private Rule hydrateRule(Connection conn, ResultSet rs) throws SQLException {
    String ruleId = rs.getString("id");
    String rootGroupId = rs.getString("root_group_id");
    if (rootGroupId == null) {
        throw new DataAccessException(
                "Rule '" + ruleId + "' has no root condition group; stored rule data is corrupt");
    }
    String name = rs.getString("name");
    boolean enabled = rs.getBoolean("enabled");
    String tierId = rs.getString("tier_id");
    int tieBreakOrder = rs.getInt("tie_break_order");
    String createdAtText = rs.getString("created_at");

    try {
        PriorityTier tier = PriorityTier.valueOf(tierId);
        Instant createdAt = Instant.parse(createdAtText);

        Condition rootCondition = loadConditionTree(conn, ruleId, rootGroupId);
        List<Action> actions = loadRuleActions(conn, ruleId);

        return new Rule(ruleId, name, enabled, tier, tieBreakOrder, rootCondition, actions, createdAt);
    } catch (IllegalArgumentException | DateTimeException e) {
        throw new DataAccessException(
                "Stored data for rule '" + ruleId + "' is invalid: " + e.getMessage(), e);
    }
}

    private Condition loadConditionTree(Connection conn, String ruleId, String rootGroupId) throws SQLException {
        if (rootGroupId == null) {
            throw new IllegalStateException("Rule " + ruleId + " has no root condition group");
        }

        // Query all groups for this rule
        String groupsSql = "SELECT id, parent_group_id, logical_operator FROM condition_group WHERE rule_id = ?";
        Map<String, GroupRecord> groupsById = new LinkedHashMap<>();
        Map<String, List<String>> childGroupIdsByParent = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(groupsSql)) {
            stmt.setString(1, ruleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String parentId = rs.getString("parent_group_id");
                    LogicalOperator op = LogicalOperator.valueOf(rs.getString("logical_operator"));
                    groupsById.put(id, new GroupRecord(id, parentId, op));
                    if (parentId != null) {
                        childGroupIdsByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
                    }
                }
            }
        }

        // Query all leaves for this rule
        String leavesSql = "SELECT cl.group_id, cl.device_id, cl.state_key, cl.operator, cl.compare_value " +
                           "FROM condition_leaf cl " +
                           "JOIN condition_group cg ON cl.group_id = cg.id " +
                           "WHERE cg.rule_id = ?";
        Map<String, List<LeafCondition>> leavesByGroupId = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(leavesSql)) {
            stmt.setString(1, ruleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String gid = rs.getString("group_id");
                    String devId = rs.getString("device_id");
                    String stateKey = rs.getString("state_key");
                    Operator op = Operator.valueOf(rs.getString("operator"));
                    String value = rs.getString("compare_value");
                    LeafCondition leaf = new LeafCondition(devId, stateKey, op, value);
                    leavesByGroupId.computeIfAbsent(gid, k -> new ArrayList<>()).add(leaf);
                }
            }
        }

        return reconstructNode(rootGroupId, groupsById, childGroupIdsByParent, leavesByGroupId);
    }

    private Condition reconstructNode(
            String groupId,
            Map<String, GroupRecord> groupsById,
            Map<String, List<String>> childGroupIdsByParent,
            Map<String, List<LeafCondition>> leavesByGroupId
    ) {
        GroupRecord group = groupsById.get(groupId);
        if (group == null) {
            throw new IllegalStateException("Condition group not found: " + groupId);
        }

        List<Condition> children = new ArrayList<>();
        List<LeafCondition> leaves = leavesByGroupId.getOrDefault(groupId, List.of());
        children.addAll(leaves);

        List<String> subGroupIds = childGroupIdsByParent.getOrDefault(groupId, List.of());
        for (String subId : subGroupIds) {
            children.add(reconstructNode(subId, groupsById, childGroupIdsByParent, leavesByGroupId));
        }

        // If this was a synthetic root group created for a single leaf condition, unwrap back to LeafCondition
        if (groupId.startsWith(SYNTHETIC_LEAF_PREFIX) && children.size() == 1 && children.get(0) instanceof LeafCondition leaf) {
            return leaf;
        }

        return new ConditionGroup(group.operator(), children);
    }

    private List<Action> loadRuleActions(Connection conn, String ruleId) throws SQLException {
        String sql = "SELECT device_id, state_key, target_value FROM rule_action WHERE rule_id = ? ORDER BY seq_no ASC, id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ruleId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Action> actions = new ArrayList<>();
                while (rs.next()) {
                    String devId = rs.getString("device_id");
                    String stateKey = rs.getString("state_key");
                    String targetVal = rs.getString("target_value");
                    actions.add(new Action(devId, stateKey, targetVal));
                }
                return actions;
            }
        }
    }

    private record GroupRecord(String id, String parentId, LogicalOperator operator) {}
}

