package com.smarthome.engine.priority;

import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Priority-based conflict resolver that detects and resolves contradictory actuations
 * across rules triggered within the same evaluation cycle.
 * <p>
 * <b>Contradiction Definition:</b>
 * A contradiction occurs when two or more triggered rules produce actions targeting the exact same
 * {@link DeviceStateKey} with different target values.
 * </p>
 * <p>
 * <b>Resolution Algorithm:</b>
 * <ol>
 *   <li>All proposed actions across triggered rules are partitioned by {@link DeviceStateKey}.</li>
 *   <li>Non-conflicting actions (single actions or multiple actions agreeing on the same target value)
 *       pass through directly to {@code toExecute}.</li>
 *   <li>When a contradiction is detected, actions are sorted deterministically using {@link TierComparator}.</li>
 *   <li>The highest-tier rule's action is preserved and added to {@code toExecute}.</li>
 *   <li>All contradictory lower-precedence actions are dropped, an {@link OverrideRecord} is generated
 *       for each dropped action, and a structured audit log event is emitted on the {@code RULE_AUDIT} logger.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b> Stateless service. All resolution calculations and intermediate
 * state are contained within thread-local stack frames, ensuring safe concurrent execution.
 * </p>
 */
public class ConflictResolver {

    private static final Logger auditLogger = LoggerFactory.getLogger("RULE_AUDIT");
    private static final Logger logger = LoggerFactory.getLogger(ConflictResolver.class);

    private final TierComparator comparator;

    public ConflictResolver() {
        this(TierComparator.INSTANCE);
    }

    public ConflictResolver(TierComparator comparator) {
        this.comparator = Objects.requireNonNull(comparator, "comparator cannot be null");
    }

    /**
     * Resolves priority conflicts among actions proposed by a collection of triggered rules.
     *
     * @param triggeredRules list of rules triggered within the evaluation cycle
     * @return {@link ResolutionResult} containing non-conflicting actions to execute and audit records of overrides
     */
    public ResolutionResult resolve(List<TriggeredRule> triggeredRules) {
        if (triggeredRules == null || triggeredRules.isEmpty()) {
            return new ResolutionResult(Collections.emptyList(), Collections.emptyList());
        }

        // Group proposed actions by their targeted DeviceStateKey
        Map<DeviceStateKey, List<ProposedAction>> groupedActions = new LinkedHashMap<>();
        for (TriggeredRule triggered : triggeredRules) {
            if (triggered == null || triggered.rule() == null || triggered.actions() == null) {
                continue;
            }
            Rule rule = triggered.rule();
            for (Action action : triggered.actions()) {
                if (action == null) {
                    continue;
                }
                DeviceStateKey targetKey = action.getTargetStateKey();
                groupedActions.computeIfAbsent(targetKey, k -> new ArrayList<>())
                        .add(new ProposedAction(rule, action));
            }
        }

        List<Action> toExecute = new ArrayList<>();
        List<OverrideRecord> overrides = new ArrayList<>();
        Instant now = Instant.now();

        for (Map.Entry<DeviceStateKey, List<ProposedAction>> entry : groupedActions.entrySet()) {
            DeviceStateKey targetKey = entry.getKey();
            List<ProposedAction> candidates = entry.getValue();

            if (candidates.size() == 1) {
                // No conflict: single action targeting this state key
                toExecute.add(candidates.get(0).action());
            } else {
                // Check if all actions agree on the same target value
                String firstValue = candidates.get(0).action().targetValue();
                boolean allAgree = candidates.stream()
                        .allMatch(p -> valuesEqual(p.action().targetValue(), firstValue));

                if (allAgree) {
                    // Harmonious agreement: pass through one action without creating override records
                    toExecute.add(candidates.get(0).action());
                } else {
                    // Contradiction detected: sort candidates by total order (highest priority first)
                    candidates.sort((p1, p2) -> comparator.compare(p1.rule(), p2.rule()));

                    ProposedAction winner = candidates.get(0);
                    toExecute.add(winner.action());

                    String winnerName = ruleDisplayName(winner.rule());

                    for (int i = 1; i < candidates.size(); i++) {
                        ProposedAction loser = candidates.get(i);
                        // Only create override records if the loser proposed a different target value
                        if (!valuesEqual(loser.action().targetValue(), winner.action().targetValue())) {
                            String loserName = ruleDisplayName(loser.rule());
                            OverrideRecord override = createOverrideRecord(winner, winnerName, loser, loserName, targetKey, now);
                            overrides.add(override);

                            // Emit structured event to RULE_AUDIT logger
                            auditLogger.warn(
                                    "OVERRIDE: [winnerId={}, tier={}] overrides [loserId={}, tier={}] on key={} " +
                                    "(applied='{}', dropped='{}'). Reason: {}",
                                    override.winningRuleId(),
                                    override.winningTier(),
                                    override.overriddenRuleId(),
                                    override.overriddenTier(),
                                    override.targetKey(),
                                    override.appliedTargetValue(),
                                    override.overriddenTargetValue(),
                                    override.reason()
                            );
                        }
                    }
                }
            }
        }

        logger.debug("Conflict resolution completed: {} actions to execute, {} overrides recorded",
                toExecute.size(), overrides.size());
        return new ResolutionResult(toExecute, overrides);
    }

    private OverrideRecord createOverrideRecord(
            ProposedAction winner,
            String winnerName,
            ProposedAction loser,
            String loserName,
            DeviceStateKey targetKey,
            Instant timestamp
    ) {
        String reason;
        if (winner.rule().getTier() != loser.rule().getTier()) {
            reason = String.format(
                    "Priority tier override: Rule '%s' (%s) prevailed over Rule '%s' (%s) on %s (applied: '%s', dropped: '%s')",
                    winnerName,
                    winner.rule().getTier(),
                    loserName,
                    loser.rule().getTier(),
                    targetKey,
                    winner.action().targetValue(),
                    loser.action().targetValue()
            );
        } else {
            reason = String.format(
                    "Same-tier tie-break override: Rule '%s' prevailed over Rule '%s' on %s (applied: '%s', dropped: '%s')",
                    winnerName,
                    loserName,
                    targetKey,
                    winner.action().targetValue(),
                    loser.action().targetValue()
            );
        }

        return new OverrideRecord(
                UUID.randomUUID().toString(),
                winner.rule().getId(),
                loser.rule().getId(),
                winner.rule().getTier(),
                loser.rule().getTier(),
                targetKey,
                loser.action().targetValue(),
                winner.action().targetValue(),
                timestamp,
                reason
        );
    }

    private String ruleDisplayName(Rule rule) {
        if (rule == null) {
            return "Unknown Rule";
        }
        if (rule.getName() != null && !rule.getName().isBlank()) {
            return rule.getName();
        }
        return rule.getId();
    }

    private boolean valuesEqual(String val1, String val2) {
        if (val1 == null && val2 == null) {
            return true;
        }
        if (val1 == null || val2 == null) {
            return false;
        }
        return val1.trim().equalsIgnoreCase(val2.trim());
    }

    private record ProposedAction(Rule rule, Action action) {}
}