package com.smarthome.engine.priority;

import com.smarthome.domain.Action;
import com.smarthome.domain.Rule;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a rule that evaluated to true in the current evaluation cycle,
 * alongside the actions it proposes to execute.
 *
 * @param rule    the triggered rule
 * @param actions the actions proposed by this rule
 */
public record TriggeredRule(Rule rule, List<Action> actions) {

    public TriggeredRule {
        Objects.requireNonNull(rule, "rule cannot be null");
        actions = actions != null ? List.copyOf(actions) : rule.getActions();
    }

    /**
     * Convenience constructor using the actions defined on the rule itself.
     *
     * @param rule the triggered rule
     */
    public TriggeredRule(Rule rule) {
        this(rule, rule.getActions());
    }
}

