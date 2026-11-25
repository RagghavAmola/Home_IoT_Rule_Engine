package com.smarthome.engine;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.Rule;
import com.smarthome.engine.priority.ConflictResolver;
import com.smarthome.engine.priority.ResolutionResult;
import com.smarthome.engine.priority.TriggeredRule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core pure-Java rule evaluation engine orchestrating evaluation and conflict resolution.
 */
public class RuleEngine {

    private final RuleEvaluator evaluator;
    private final ConflictResolver conflictResolver;

    public RuleEngine() {
        this(new RuleEvaluator(), new ConflictResolver());
    }

    public RuleEngine(RuleEvaluator evaluator, ConflictResolver conflictResolver) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver cannot be null");
    }

    /**
     * Synchronously evaluates rules against a state snapshot and resolves conflicts.
     */
    public EvaluationResult evaluate(StateSnapshot snapshot, Collection<Rule> rules) {
        if (snapshot == null || rules == null || rules.isEmpty()) {
            return new EvaluationResult(List.of(), List.of(), List.of());
        }

        List<TriggeredRule> triggered = evaluator.findTriggered(rules, snapshot);
        ResolutionResult resolution = conflictResolver.resolve(triggered);

        return new EvaluationResult(
                resolution.toExecute(),
                resolution.overrides(),
                triggered
        );
    }

    /**
     * Overload accepting a raw Map snapshot.
     *
     * @param snapshot raw state map snapshot
     * @param rules    candidate rules to evaluate
     * @return evaluation result
     */
    public EvaluationResult evaluate(Map<DeviceStateKey, String> snapshot, Collection<Rule> rules) {
        return evaluate(snapshot instanceof StateSnapshot s ? s : StateSnapshot.of(snapshot), rules);
    }

    public RuleEvaluator getEvaluator() {
        return evaluator;
    }

    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }
}

