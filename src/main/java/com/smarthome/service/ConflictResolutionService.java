package com.smarthome.service;

import com.smarthome.engine.priority.ResolutionResult;
import com.smarthome.engine.priority.TriggeredRule;

import java.util.List;

/**
 * Service orchestrating priority-based conflict resolution and persistence of override audit logs.
 */
public interface ConflictResolutionService {

    /**
     * Resolves contradictions among actions proposed by triggered rules and persists override records.
     */
    ResolutionResult resolveAndPersist(List<TriggeredRule> triggeredRules);
}

