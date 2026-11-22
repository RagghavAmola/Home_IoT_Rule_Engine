package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;

import java.util.List;

/**
 * Result record from evaluating cascading rule actuations across a simulation tick.
 *
 * @param cascadeSteps    total number of cascade steps executed (must be >= 0)
 * @param maxDepthReached true if evaluation was halted by the runtime runaway chain guard
 * @param executedActions ordered list of all actions applied across all cascade steps
 * @param overrides       ordered list of all override records generated across all cascade steps
 */
public record CascadeResult(
        int cascadeSteps,
        boolean maxDepthReached,
        List<Action> executedActions,
        List<OverrideRecord> overrides
) {

    public CascadeResult {
        if (cascadeSteps < 0) {
            throw new IllegalArgumentException("cascadeSteps cannot be negative, but was: " + cascadeSteps);
        }
        executedActions = executedActions != null ? List.copyOf(executedActions) : List.of();
        overrides = overrides != null ? List.copyOf(overrides) : List.of();
    }
}