package com.smarthome.engine.priority;

import com.smarthome.domain.Rule;

import java.util.Objects;

/**
 * Poset utility for rule priority evaluation and deterministic tie-breaking.
 */
public final class PriorityPoset {

    private PriorityPoset() {
    }

    /**
     * Compares two rules according to the total linear order of priority and tie-breakers.
     */
    public static int compare(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return TierComparator.INSTANCE.compare(a, b);
    }

    /**
     * Checks whether rule a has strictly higher priority than rule b under the total order.
     */
    public static boolean isHigherPriority(Rule a, Rule b) {
        return compare(a, b) < 0;
    }

    /**
     * Checks whether rule a has a strictly higher priority tier than rule b.
     */
    public static boolean isStrictlyHigherTier(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return a.getTier().isHigherThan(b.getTier());
    }

    /**
     * Checks whether two rules reside in the exact same priority tier.
     */
    public static boolean isSameTier(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return a.getTier() == b.getTier();
    }

    /**
     * Returns the winning rule under the deterministic total order.
     */
    public static Rule selectWinner(Rule a, Rule b) {
        return isHigherPriority(a, b) ? a : b;
    }

    /**
     * Obtains the shared TierComparator instance.
     */
    public static TierComparator comparator() {
        return TierComparator.INSTANCE;
    }
}

