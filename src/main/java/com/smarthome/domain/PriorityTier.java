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

    /**
     * Checks if this priority tier has strictly higher priority than the other tier.
     * Lower ordinal/level represents higher priority (e.g. SAFETY is higher than COMFORT).
     *
     * @param other the other priority tier to compare against
     * @return true if this priority tier is strictly higher, false otherwise
     */
    public boolean isHigherThan(PriorityTier other) {
        Objects.requireNonNull(other, "PriorityTier to compare cannot be null");
        return this.compareTo(other) < 0;
    }

    /**
     * Checks if this priority tier has strictly lower priority than the other tier.
     * Higher ordinal/level represents lower priority (e.g. ENERGY is lower than COMFORT).
     *
     * @param other the other priority tier to compare against
     * @return true if this priority tier is strictly lower, false otherwise
     */
    public boolean isLowerThan(PriorityTier other) {
        Objects.requireNonNull(other, "PriorityTier to compare cannot be null");
        return this.compareTo(other) > 0;
    }
}
