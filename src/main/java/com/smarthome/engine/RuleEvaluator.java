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
 * Evaluates rules against a state snapshot to identify triggered rules.
 */
public class RuleEvaluator {

    /**
     * Finds all enabled rules whose root conditions evaluate to true against the given snapshot.
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