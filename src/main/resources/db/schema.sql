-- Smart Home IoT Rule Engine Database Schema

CREATE TABLE IF NOT EXISTS device_type (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS device (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type_id VARCHAR(32) NOT NULL,
    room VARCHAR(64) NOT NULL,
    FOREIGN KEY (type_id) REFERENCES device_type(id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS device_state (
    device_id VARCHAR(64) NOT NULL,
    state_key VARCHAR(64) NOT NULL,
    state_value VARCHAR(255) NOT NULL,
    updated_at VARCHAR(64) NOT NULL,
    PRIMARY KEY (device_id, state_key),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS priority_tier (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    tier_rank INT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS rule (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT 1,
    tier_id VARCHAR(32) NOT NULL,
    tie_break_order INT NOT NULL DEFAULT 0,
    root_group_id VARCHAR(64),
    created_at VARCHAR(64) NOT NULL,
    FOREIGN KEY (tier_id) REFERENCES priority_tier(id),
    FOREIGN KEY (root_group_id) REFERENCES condition_group(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS condition_group (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    parent_group_id VARCHAR(64),
    logical_operator VARCHAR(16) NOT NULL CHECK (logical_operator IN ('AND', 'OR')),
    position INT NOT NULL DEFAULT 0,
    FOREIGN KEY (rule_id) REFERENCES rule(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_group_id) REFERENCES condition_group(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS condition_leaf (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    state_key VARCHAR(64) NOT NULL,
    operator VARCHAR(16) NOT NULL CHECK (operator IN ('EQ', 'NEQ', 'GT', 'LT', 'GTE', 'LTE')),
    compare_value VARCHAR(255) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    FOREIGN KEY (group_id) REFERENCES condition_group(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS rule_action (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    state_key VARCHAR(64) NOT NULL,
    target_value VARCHAR(255) NOT NULL,
    seq_no INT NOT NULL DEFAULT 0,
    FOREIGN KEY (rule_id) REFERENCES rule(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS rule_dependency_edge (
    from_rule_id VARCHAR(64) NOT NULL,
    to_rule_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (from_rule_id, to_rule_id),
    FOREIGN KEY (from_rule_id) REFERENCES rule(id) ON DELETE CASCADE,
    FOREIGN KEY (to_rule_id) REFERENCES rule(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS execution_log (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64),
    executed_at VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    details TEXT,
    FOREIGN KEY (rule_id) REFERENCES rule(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS override_log (
    id VARCHAR(64) PRIMARY KEY,
    winning_rule_id VARCHAR(64),
    dropped_rule_id VARCHAR(64),
    device_id VARCHAR(64) NOT NULL,
    state_key VARCHAR(64) NOT NULL,
    occurred_at VARCHAR(64) NOT NULL,
    reason TEXT,
    FOREIGN KEY (winning_rule_id) REFERENCES rule(id) ON DELETE SET NULL,
    FOREIGN KEY (dropped_rule_id) REFERENCES rule(id) ON DELETE SET NULL
);

-- Indexes for performance and foreign key lookups
CREATE INDEX IF NOT EXISTS idx_device_room ON device(room);
CREATE INDEX IF NOT EXISTS idx_device_type ON device(type_id);
CREATE INDEX IF NOT EXISTS idx_rule_tier ON rule(tier_id);
CREATE INDEX IF NOT EXISTS idx_condition_group_rule ON condition_group(rule_id);
CREATE INDEX IF NOT EXISTS idx_condition_group_parent ON condition_group(parent_group_id);
CREATE INDEX IF NOT EXISTS idx_condition_leaf_group ON condition_leaf(group_id);
CREATE INDEX IF NOT EXISTS idx_condition_leaf_device ON condition_leaf(device_id);
CREATE INDEX IF NOT EXISTS idx_rule_action_rule ON rule_action(rule_id);
CREATE INDEX IF NOT EXISTS idx_rule_action_device ON rule_action(device_id);
CREATE INDEX IF NOT EXISTS idx_execution_log_rule ON execution_log(rule_id);
CREATE INDEX IF NOT EXISTS idx_execution_log_time ON execution_log(executed_at);
CREATE INDEX IF NOT EXISTS idx_override_log_time ON override_log(occurred_at);

