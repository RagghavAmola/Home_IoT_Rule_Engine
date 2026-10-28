package com.smarthome.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public record RuleExecutionRecord(
        String id,
        String ruleId,
        String ruleName,
        PriorityTier tier,
        Instant timestamp,
        boolean conditionSatisfied,
        List<Action> triggeredActions,
        Map<DeviceStateKey, String> triggeredStateSnapshot,
        String message
) {

    public RuleExecutionRecord {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(tier, "tier cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        triggeredActions = triggeredActions != null ? List.copyOf(triggeredActions) : List.of();
        triggeredStateSnapshot = triggeredStateSnapshot != null ? Map.copyOf(triggeredStateSnapshot) : Map.of();
        message = message != null ? message : "";
    }
}
