package com.smarthome.service.monitor;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;

import java.util.List;

/**
 * Metric summary of an individual evaluation tick executed by {@link RuleMonitorService}.
 */
public record TickResult(
        int rulesEvaluatedCount,
        int triggeredRulesCount,
        List<Action> actionsApplied,
        List<OverrideRecord> overrides,
        long durationMs,
        boolean errorOccurred
) {

    public TickResult {
        if (rulesEvaluatedCount < 0) {
            throw new IllegalArgumentException("rulesEvaluatedCount cannot be negative, but was: " + rulesEvaluatedCount);
        }
        if (triggeredRulesCount < 0) {
            throw new IllegalArgumentException("triggeredRulesCount cannot be negative, but was: " + triggeredRulesCount);
        }
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs cannot be negative, but was: " + durationMs);
        }
        actionsApplied = actionsApplied != null ? List.copyOf(actionsApplied) : List.of();
        overrides = overrides != null ? List.copyOf(overrides) : List.of();
    }

    /**
     * Creates an empty tick result indicating no actions or rules evaluated.
     */
    public static TickResult empty() {
        return new TickResult(0, 0, List.of(), List.of(), 0L, false);
    }
}