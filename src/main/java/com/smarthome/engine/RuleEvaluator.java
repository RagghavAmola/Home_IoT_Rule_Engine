package com.smarthome.engine;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.Rule;
import com.smarthome.engine.priority.TriggeredRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java evaluator that tests a collection of rules against a device state snapshot
 * and identifies all rules whose condition trees evaluate to true.
 * <p>
 * Only active (enabled) rules are evaluated. Disabled rules are ignored.
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b> Stateless service. All evaluation logic operates solely
 * on parameters passed in and stack-confined variables, making it safe for concurrent use.
 * </p>
 */
public class RuleEvaluator {

    /**
     * Finds all enabled rules whose root conditions evaluate to true against the given state snapshot.
     *
     * @param enabledRules collection of candidate rules to evaluate
     * @param snapshot     immutable snapshot of device telemetry states
     * @return unmodifiable list of triggered rules with their proposed actions
     */
    public List<TriggeredRule> findTriggered(Collection<Rule> enabledRules, StateSnapshot snapshot) {
        if (enabledRules == null || enabledRules.isEmpty() || snapshot == null) {
            return Collections.emptyList();
        }

        List<TriggeredRule> triggered = new ArrayList<>();
        for (Rule rule : enabledRules) {
            if (rule != null && rule.isEnabled() && rule.getRootCondition() != null) {
                if (rule.getRootCondition().evaluate(snapshot)) {
                    triggered.add(new TriggeredRule(rule));
                }
            }
        }
        return Collections.unmodifiableList(triggered);
    }

    /**
     * Overload accepting a raw Map snapshot for flexibility.
     *
     * @param enabledRules collection of candidate rules
     * @param snapshot     raw state map snapshot
     * @return unmodifiable list of triggered rules
     */
    public List<TriggeredRule> findTriggered(Collection<Rule> enabledRules, Map<DeviceStateKey, String> snapshot) {
        if (snapshot == null) {
            return Collections.emptyList();
        }
        return findTriggered(enabledRules, snapshot instanceof StateSnapshot s ? s : StateSnapshot.of(snapshot));
    }
}