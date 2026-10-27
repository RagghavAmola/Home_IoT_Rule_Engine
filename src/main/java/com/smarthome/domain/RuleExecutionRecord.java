package com.smarthome.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable audit record capturing the outcome of evaluating and executing an individual rule.
 *
 * @param id                      unique identifier of this execution log entry
 * @param ruleId                  identifier of the evaluated rule
 * @param ruleName                name of the evaluated rule
 * @param tier                    priority tier of the rule
 * @param timestamp               timestamp when execution occurred
 * @param conditionSatisfied      whether the rule condition evaluated to true
 * @param triggeredActions        list of actions executed (empty if rule did not trigger)
 * @param triggeredStateSnapshot  snapshot of device telemetry at the moment of evaluation
 * @param message                 execution details or diagnostic message
 */
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
