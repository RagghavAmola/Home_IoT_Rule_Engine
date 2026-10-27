package com.smarthome.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Composite condition node evaluating child conditions using logical AND or OR.
 *
 * @param operator boolean logical operator (AND / OR)
 * @param children list of child condition nodes
 */
public record ConditionGroup(
        LogicalOperator operator,
        List<Condition> children
) implements Condition {

    public ConditionGroup {
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(children, "children cannot be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("children list cannot be empty");
        }
        for (Condition child : children) {
            Objects.requireNonNull(child, "child condition cannot be null");
        }
        children = List.copyOf(children);
    }

    @Override
    public boolean evaluate(Map<DeviceStateKey, String> stateSnapshot) {
        Objects.requireNonNull(stateSnapshot, "stateSnapshot cannot be null");
        return switch (operator) {
            case AND -> children.stream().allMatch(child -> child.evaluate(stateSnapshot));
            case OR -> children.stream().anyMatch(child -> child.evaluate(stateSnapshot));
        };
    }

    @Override
    public Set<DeviceStateKey> getReferencedKeys() {
        Set<DeviceStateKey> keys = new LinkedHashSet<>();
        for (Condition child : children) {
            keys.addAll(child.getReferencedKeys());
        }
        return Collections.unmodifiableSet(keys);
    }
}
