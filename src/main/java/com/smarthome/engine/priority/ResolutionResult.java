package com.smarthome.engine.priority;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;

import java.util.List;

/**
 * Result of priority-based conflict resolution across all triggered rules in an evaluation cycle.
 *
 * @param toExecute the resolved, non-conflicting actions to apply to devices
 * @param overrides audit log records for any conflicting actions that were overridden
 */
public record ResolutionResult(
        List<Action> toExecute,
        List<OverrideRecord> overrides
) {

    public ResolutionResult {
        toExecute = toExecute != null ? List.copyOf(toExecute) : List.of();
        overrides = overrides != null ? List.copyOf(overrides) : List.of();
    }
}

