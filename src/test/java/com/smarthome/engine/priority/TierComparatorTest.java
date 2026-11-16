package com.smarthome.engine.priority;

import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierComparatorTest {

    private final TierComparator comparator = TierComparator.INSTANCE;

    private Rule createRule(String id, PriorityTier tier, int tieBreakOrder, Instant createdAt) {
        LeafCondition cond = new LeafCondition("dev", "key", Operator.EQ, "1");
        Action act = new Action("dev", "key", "val");
        return new Rule(id, "Rule " + id, true, tier, tieBreakOrder, cond, List.of(act), createdAt);
    }

    @Test
    @DisplayName("PriorityTier ordering: SAFETY > COMFORT > ENERGY")
    void testPriorityTierOrdering() {
        Instant now = Instant.now();
        Rule safety = createRule("R_SAFE", PriorityTier.SAFETY, 10, now);
        Rule comfort = createRule("R_COMFORT", PriorityTier.COMFORT, 0, now);
        Rule energy = createRule("R_ENERGY", PriorityTier.ENERGY, 0, now);

        // Safety is higher priority than Comfort (compare returns negative)
        assertTrue(comparator.compare(safety, comfort) < 0);
        assertTrue(comparator.compare(comfort, safety) > 0);

        // Comfort is higher priority than Energy
        assertTrue(comparator.compare(comfort, energy) < 0);
        assertTrue(comparator.compare(energy, comfort) > 0);

        // Safety is higher priority than Energy
        assertTrue(comparator.compare(safety, energy) < 0);
    }

    @Test
    @DisplayName("Tie-breaker 1: tieBreakOrder breaks ties in the same tier")
    void testTieBreakOrderInSameTier() {
        Instant now = Instant.now();
        Rule r1 = createRule("R1", PriorityTier.COMFORT, 1, now);
        Rule r2 = createRule("R2", PriorityTier.COMFORT, 5, now);

        assertTrue(comparator.compare(r1, r2) < 0, "Lower tieBreakOrder (1) must beat (5)");
        assertTrue(comparator.compare(r2, r1) > 0);
    }

    @Test
    @DisplayName("Tie-breaker 2: createdAt breaks ties when tier and tieBreakOrder are equal")
    void testCreatedAtTieBreak() {
        Instant earlier = Instant.parse("2026-11-01T10:00:00Z");
        Instant later = Instant.parse("2026-11-01T11:00:00Z");

        Rule rEarlier = createRule("R_EARLY", PriorityTier.SAFETY, 0, earlier);
        Rule rLater = createRule("R_LATE", PriorityTier.SAFETY, 0, later);

        assertTrue(comparator.compare(rEarlier, rLater) < 0, "Earlier created rule must beat later created rule");
        assertTrue(comparator.compare(rLater, rEarlier) > 0);
    }

    @Test
    @DisplayName("Tie-breaker 3: ID breaks ties deterministically when all else is equal")
    void testIdTieBreakFallback() {
        Instant now = Instant.now();
        Rule rA = createRule("rule_a", PriorityTier.ENERGY, 0, now);
        Rule rB = createRule("rule_b", PriorityTier.ENERGY, 0, now);

        assertTrue(comparator.compare(rA, rB) < 0, "'rule_a' must beat 'rule_b' lexicographically");
        assertTrue(comparator.compare(rB, rA) > 0);
    }

    @Test
    @DisplayName("Comparing identical rules returns zero")
    void testIdenticalRulesReturnZero() {
        Instant now = Instant.now();
        Rule r = createRule("rule-1", PriorityTier.COMFORT, 0, now);

        assertEquals(0, comparator.compare(r, r));
    }
}

