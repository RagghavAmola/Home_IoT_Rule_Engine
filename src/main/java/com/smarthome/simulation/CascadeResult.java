package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;

import java.util.List;

/**
 * Result record from evaluating cascading rule actuations across a simulation tick.
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