package com.smarthome.engine;

import com.smarthome.domain.Action;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.engine.priority.TriggeredRule;

import java.util.List;

/**
 * Immutable outcome of a rule engine evaluation pass.
 *
 * @param actionsToExecute resolved, non-conflicting actions to apply to devices
 * @param overrides        audit log records for any conflicting actions that were dropped/overridden
 * @param triggeredRules   all rules that evaluated to true in this evaluation pass
 */
public record EvaluationResult(
        List<Action> actionsToExecute,
        List<OverrideRecord> overrides,
        List<TriggeredRule> triggeredRules
) {

    public EvaluationResult {
        actionsToExecute = actionsToExecute != null ? List.copyOf(actionsToExecute) : List.of();
        overrides = overrides != null ? List.copyOf(overrides) : List.of();
        triggeredRules = triggeredRules != null ? List.copyOf(triggeredRules) : List.of();
    }

    /**
     * Convenience constructor without explicit triggered rules list.
     *
     * @param actionsToExecute resolved actions
     * @param overrides        overridden records
     */
    public EvaluationResult(List<Action> actionsToExecute, List<OverrideRecord> overrides) {
        this(actionsToExecute, overrides, List.of());
    }

    /**
     * Alias matching prompt specification "actions to apply".
     *
     * @return list of actions to execute
     */
    public List<Action> actions() {
        return actionsToExecute;
    }

    /**
     * Alias matching ResolutionResult convention.
     *
     * @return list of actions to execute
     */
    public List<Action> toExecute() {
        return actionsToExecute;
    }

    public List<Action> getActionsToExecute() {
        return actionsToExecute;
    }

    public List<OverrideRecord> getOverrides() {
        return overrides;
    }

    public List<TriggeredRule> getTriggeredRules() {
        return triggeredRules;
    }
}

