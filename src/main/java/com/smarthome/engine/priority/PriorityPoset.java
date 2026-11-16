package com.smarthome.engine.priority;

import com.smarthome.domain.Rule;

import java.util.Objects;

/**
 * Mathematical model and utility for the partially ordered set (poset) of smart home automation rules.
 * <p>
 * <b>Poset Formalism:</b>
 * Let \( R \) be the set of all rules. A partial order \(\prec_{\text{tier}}\) is defined by their
 * {@link com.smarthome.domain.PriorityTier}:
 * \[
 *   a \prec_{\text{tier}} b \iff \text{tier}(a) < \text{tier}(b)
 * \]
 * where \(\text{SAFETY} = 0 < \text{COMFORT} = 1 < \text{ENERGY} = 2\).
 * If \(\text{tier}(a) = \text{tier}(b)\), \(a\) and \(b\) are incomparable in the tier poset (\(a \parallel b\)).
 * </p>
 * <p>
 * <b>Linear Extension (Tie-Breaker Chain):</b>
 * To resolve contradictions deterministically, the poset is extended to a total linear order \(\prec_{\text{total}}\)
 * via {@link TierComparator}:
 * \[
 *   a \prec_{\text{total}} b \iff
 *   \begin{cases}
 *     a \prec_{\text{tier}} b, & \text{if } \text{tier}(a) \neq \text{tier}(b) \\
 *     \text{tieBreak}(a) < \text{tieBreak}(b), & \text{if } \text{tieBreak}(a) \neq \text{tieBreak}(b) \\
 *     \text{created}(a) < \text{created}(b), & \text{if } \text{created}(a) \neq \text{created}(b) \\
 *     \text{id}(a) < \text{id}(b), & \text{otherwise}
 *   \end{cases}
 * \]
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b> Stateless class; safe for concurrent execution across all threads.
 * </p>
 */
public final class PriorityPoset {

    private PriorityPoset() {
        // Prevent instantiation of utility class
    }

    /**
     * Compares two rules according to the total linear extension of the priority poset.
     *
     * @param a first rule
     * @param b second rule
     * @return negative if {@code a} has strictly higher priority than {@code b},
     *         positive if {@code b} has strictly higher priority than {@code a},
     *         zero if both rules are identical in priority and ID
     */
    public static int compare(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return TierComparator.INSTANCE.compare(a, b);
    }

    /**
     * Checks whether rule {@code a} has strictly higher priority than rule {@code b} under the total order.
     *
     * @param a candidate higher-priority rule
     * @param b candidate lower-priority rule
     * @return true if {@code a} strictly prevails over {@code b}
     */
    public static boolean isHigherPriority(Rule a, Rule b) {
        return compare(a, b) < 0;
    }

    /**
     * Checks whether rule {@code a} belongs to a strictly higher priority tier than rule {@code b}
     * under the pure tier partial order (ignoring tie-breakers).
     *
     * @param a rule a
     * @param b rule b
     * @return true if tier(a) is higher than tier(b)
     */
    public static boolean isStrictlyHigherTier(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return a.getTier().isHigherThan(b.getTier());
    }

    /**
     * Checks whether two rules reside in the exact same priority tier.
     *
     * @param a rule a
     * @param b rule b
     * @return true if tier(a) == tier(b)
     */
    public static boolean isSameTier(Rule a, Rule b) {
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");
        return a.getTier() == b.getTier();
    }

    /**
     * Returns whichever rule has higher priority under the deterministic total order.
     *
     * @param a rule a
     * @param b rule b
     * @return the winning rule
     */
    public static Rule selectWinner(Rule a, Rule b) {
        return isHigherPriority(a, b) ? a : b;
    }

    /**
     * Obtains the shared {@link TierComparator} singleton instance.
     *
     * @return comparator instance
     */
    public static TierComparator comparator() {
        return TierComparator.INSTANCE;
    }
}

