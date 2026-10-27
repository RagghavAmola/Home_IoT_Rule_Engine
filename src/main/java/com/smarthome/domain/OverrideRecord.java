package com.smarthome.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit log record capturing an action override when conflicting actions occur across priority tiers.
 *
 * @param id                    unique identifier of this override audit log entry
 * @param winningRuleId         identifier of the higher-priority rule that prevailed
 * @param overriddenRuleId      identifier of the lower-priority rule that was overridden
 * @param winningTier           priority tier of the winning rule
 * @param overriddenTier        priority tier of the overridden rule
 * @param targetKey             device state key targeted by both rules
 * @param overriddenTargetValue value proposed by the overridden rule
 * @param appliedTargetValue    value actually applied by the winning rule
 * @param timestamp             timestamp when the override conflict was resolved
 * @param reason                human-readable explanation of why the override occurred
 */
public record OverrideRecord(
        String id,
        String winningRuleId,
        String overriddenRuleId,
        PriorityTier winningTier,
        PriorityTier overriddenTier,
        DeviceStateKey targetKey,
        String overriddenTargetValue,
        String appliedTargetValue,
        Instant timestamp,
        String reason
) {

    public OverrideRecord {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(winningRuleId, "winningRuleId cannot be null");
        Objects.requireNonNull(overriddenRuleId, "overriddenRuleId cannot be null");
        Objects.requireNonNull(winningTier, "winningTier cannot be null");
        Objects.requireNonNull(overriddenTier, "overriddenTier cannot be null");
        Objects.requireNonNull(targetKey, "targetKey cannot be null");
        Objects.requireNonNull(overriddenTargetValue, "overriddenTargetValue cannot be null");
        Objects.requireNonNull(appliedTargetValue, "appliedTargetValue cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        reason = reason != null ? reason : "";
    }
}
