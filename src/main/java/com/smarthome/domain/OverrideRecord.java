package com.smarthome.domain;

import java.time.Instant;
import java.util.Objects;


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
