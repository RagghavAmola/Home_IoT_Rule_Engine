package com.smarthome.engine.priority;

import com.smarthome.domain.Rule;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

/**
 * Deterministic total order comparator for {@link Rule}s.
 * <p>
 * <b>Poset and Linear Extension Design:</b>
 * The domain model defines a partial order (poset) where rules are ordered by {@link com.smarthome.domain.PriorityTier}:
 * {@code SAFETY (level 0) > COMFORT (level 1) > ENERGY (level 2)}.
 * Under this pure poset, rules residing within the same priority tier are incomparable.
 * </p>
 * <p>
 * To ensure reproducible, conflict-free rule evaluation across concurrent or sequential evaluation cycles,
 * this comparator extends the tier poset into a deterministic total order using the following cascade of tie-breakers:
 * <ol>
 *   <li><b>PriorityTier level</b> (ascending): lower numeric level indicates strictly higher priority.</li>
 *   <li><b>tieBreakOrder</b> (ascending): lower integer value indicates higher precedence within the same tier.</li>
 *   <li><b>createdAt</b> (ascending): earlier created rules take precedence.</li>
 *   <li><b>Rule ID</b> (lexicographical): unique identifier string breaks any remaining tie deterministically.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Sign Convention:</b>
 * Consistent with {@link Comparator}, {@code compare(a, b) < 0} indicates that rule {@code a} has
 * strictly higher priority than rule {@code b} (and should therefore prevail in a conflict).
 * </p>
 */
public class TierComparator implements Comparator<Rule>, Serializable {

    public static final TierComparator INSTANCE = new TierComparator();

    @Override
    public int compare(Rule a, Rule b) {
        if (a == b) {
            return 0;
        }
        Objects.requireNonNull(a, "Rule a cannot be null");
        Objects.requireNonNull(b, "Rule b cannot be null");

        // 1. PriorityTier level comparison (lower level = higher priority)
        int tierCmp = Integer.compare(a.getTier().getLevel(), b.getTier().getLevel());
        if (tierCmp != 0) {
            return tierCmp;
        }

        // 2. Tie-break order within the same tier (lower value = higher priority)
        int tieCmp = Integer.compare(a.getTieBreakOrder(), b.getTieBreakOrder());
        if (tieCmp != 0) {
            return tieCmp;
        }

        // 3. Creation timestamp (earlier instant = higher priority)
        int dateCmp = a.getCreatedAt().compareTo(b.getCreatedAt());
        if (dateCmp != 0) {
            return dateCmp;
        }

        // 4. Deterministic fallback on unique Rule ID
        return a.getId().compareTo(b.getId());
    }
    /**
     * Ensures singleton instance identity upon deserialization.
     */
    private Object readResolve() {
        return INSTANCE;
    }
}

