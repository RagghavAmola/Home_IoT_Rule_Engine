package com.smarthome.engine.priority;

import com.smarthome.domain.Rule;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

/**
 * Deterministic total order comparator ordering rules by PriorityTier, tieBreakOrder, createdAt, and ID.
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

        int tierCmp = Integer.compare(a.getTier().getLevel(), b.getTier().getLevel());
        if (tierCmp != 0) {
            return tierCmp;
        }

        int tieCmp = Integer.compare(a.getTieBreakOrder(), b.getTieBreakOrder());
        if (tieCmp != 0) {
            return tieCmp;
        }

        int dateCmp = a.getCreatedAt().compareTo(b.getCreatedAt());
        if (dateCmp != 0) {
            return dateCmp;
        }

        return a.getId().compareTo(b.getId());
    }

    private Object readResolve() {
        return INSTANCE;
    }
}

