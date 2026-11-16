package com.smarthome.service;

import com.smarthome.engine.priority.ResolutionResult;
import com.smarthome.engine.priority.TriggeredRule;

import java.util.List;

/**
 * Service orchestrating priority-based conflict resolution and persistence of override audit logs.
 */
public interface ConflictResolutionService {

    /**
     * Resolves contradictions among actions proposed by triggered rules and persists generated
     * override audit records to the database.
     *
     * @param triggeredRules collection of rules triggered in the current evaluation cycle
     * @return the {@link ResolutionResult} containing approved actions and recorded overrides
     */
    ResolutionResult resolveAndPersist(List<TriggeredRule> triggeredRules);
}

