package com.smarthome.domain;

import java.util.Objects;

/**
 * Ordered priority tier for smart home automation rules.
 * <p>
 * Lower ordinal represents higher priority:
 * {@link #SAFETY} (0) &gt; {@link #COMFORT} (1) &gt; {@link #ENERGY} (2).
 * </p>
 */
public enum PriorityTier {
    SAFETY(0),
    COMFORT(1),
    ENERGY(2);

    private final int level;

    PriorityTier(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isHigherThan(PriorityTier other) {
        Objects.requireNonNull(other, "PriorityTier to compare cannot be null");
        return this.compareTo(other) < 0;
    }

    public boolean isLowerThan(PriorityTier other) {
        Objects.requireNonNull(other, "PriorityTier to compare cannot be null");
        return this.compareTo(other) > 0;
    }
}
