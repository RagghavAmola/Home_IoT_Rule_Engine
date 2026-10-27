package com.smarthome.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing an automation rule with conditions, priority tier, and actuations.
 * <p>
 * Entity equality and hash code are strictly based on the unique rule {@code id}.
 * </p>
 */
public final class Rule {

    private final String id;
    private final String name;
    private final boolean enabled;
    private final PriorityTier tier;
    private final int tieBreakOrder;
    private final Condition rootCondition;
    private final List<Action> actions;
    private final Instant createdAt;

    /**
     * Constructs a Rule with explicit creation timestamp.
     *
     * @param id            unique identifier
     * @param name          human-readable rule name
     * @param enabled       whether the rule is active
     * @param tier          priority tier
     * @param tieBreakOrder tie-break precedence for rules within the same tier (lower = higher priority)
     * @param rootCondition root condition tree
     * @param actions       list of actions executed when the condition is met
     * @param createdAt     rule creation timestamp
     */
    public Rule(
            String id,
            String name,
            boolean enabled,
            PriorityTier tier,
            int tieBreakOrder,
            Condition rootCondition,
            List<Action> actions,
            Instant createdAt
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(tier, "tier cannot be null");
        Objects.requireNonNull(rootCondition, "rootCondition cannot be null");
        Objects.requireNonNull(actions, "actions cannot be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("actions list cannot be empty");
        }
        Map<DeviceStateKey, String> targetsSeen = new HashMap<>();
        for (Action action : actions) {
            Objects.requireNonNull(action, "action element cannot be null");
            String previousValue = targetsSeen.putIfAbsent(
                    action.getTargetStateKey(), action.targetValue());
            if (previousValue != null && !previousValue.equals(action.targetValue())) {
                throw new IllegalArgumentException(
                        "Rule '" + name + "' has contradictory actions on "
                                + action.getTargetStateKey() + ": '"
                                + previousValue + "' vs '" + action.targetValue() + "'");
            }
        }

        this.id = id.trim();
        this.name = name.trim();
        this.enabled = enabled;
        this.tier = tier;
        this.tieBreakOrder = tieBreakOrder;
        this.rootCondition = rootCondition;
        this.actions = List.copyOf(actions);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Constructs a Rule defaulting {@code createdAt} to current instant.
     *
     * @param id            unique identifier
     * @param name          human-readable rule name
     * @param enabled       whether the rule is active
     * @param tier          priority tier
     * @param tieBreakOrder tie-break precedence within tier
     * @param rootCondition root condition tree
     * @param actions       actions to trigger
     */
    public Rule(
            String id,
            String name,
            boolean enabled,
            PriorityTier tier,
            int tieBreakOrder,
            Condition rootCondition,
            List<Action> actions
    ) {
        this(id, name, enabled, tier, tieBreakOrder, rootCondition, actions, Instant.now());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PriorityTier getTier() {
        return tier;
    }

    public int getTieBreakOrder() {
        return tieBreakOrder;
    }

    public Condition getRootCondition() {
        return rootCondition;
    }

    public List<Action> getActions() {
        return actions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Resolves all device state keys referenced/read by this rule's condition hierarchy.
     *
     * @return unmodifiable set of referenced read keys
     */
    public Set<DeviceStateKey> getReadSet() {
        return rootCondition.getReferencedKeys();
    }

    /**
     * Resolves all device state keys targeted/mutated by this rule's actions.
     *
     * @return unmodifiable set of targeted write keys
     */
    public Set<DeviceStateKey> getWriteSet() {
        Set<DeviceStateKey> writeSet = new LinkedHashSet<>();
        for (Action action : actions) {
            writeSet.add(action.getTargetStateKey());
        }
        return Collections.unmodifiableSet(writeSet);
    }

    /**
     * Produces a copy of this rule with an updated {@code enabled} status.
     *
     * @param enabled new active state
     * @return updated Rule instance
     */
    public Rule withEnabled(boolean enabled) {
        return new Rule(this.id, this.name, enabled, this.tier, this.tieBreakOrder, this.rootCondition, this.actions, this.createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Rule{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", tier=" + tier +
                ", tieBreakOrder=" + tieBreakOrder +
                ", actionsCount=" + actions.size() +
                ", createdAt=" + createdAt +
                '}';
    }
}
