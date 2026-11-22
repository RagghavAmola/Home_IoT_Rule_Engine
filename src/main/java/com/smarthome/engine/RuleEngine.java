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
 * Core pure-Java rule evaluation engine.
 * <p>
 * In each evaluation cycle, the engine:
 * <ol>
 *   <li>Evaluates active rules against the given {@link StateSnapshot} via {@link RuleEvaluator}.</li>
 *   <li>Detects and resolves contradictory actuations via {@link ConflictResolver}.</li>
 *   <li>Returns an {@link EvaluationResult} containing the surviving actions and override audit records.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b> Stateless pure-Java engine. Performs zero internal threading or
 * asynchronous dispatch. All operations are stack-confined and operate on immutable snapshots,
 * allowing safe concurrent execution across arbitrary calling threads.
 * </p>
 */
public class RuleEngine {

    private final RuleEvaluator evaluator;
    private final ConflictResolver conflictResolver;

    /**
     * Default constructor wiring standard evaluator and conflict resolver.
     */
    public RuleEngine() {
        this(new RuleEvaluator(), new ConflictResolver());
    }

    /**
     * Parameterized constructor for dependency injection.
     *
     * @param evaluator        evaluator for finding triggered rules
     * @param conflictResolver priority conflict resolver
     */
    public RuleEngine(RuleEvaluator evaluator, ConflictResolver conflictResolver) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver cannot be null");
    }

    /**
     * Synchronously evaluates a collection of rules against an immutable state snapshot.
     *
     * @param snapshot immutable snapshot of device states
     * @param rules    candidate rules to evaluate
     * @return {@link EvaluationResult} containing actions to apply, override records, and triggered rules
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

