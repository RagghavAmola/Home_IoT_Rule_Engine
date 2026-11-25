package com.smarthome.service;

import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.engine.priority.ConflictResolver;
import com.smarthome.engine.priority.ResolutionResult;
import com.smarthome.engine.priority.TriggeredRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of ConflictResolutionService delegating resolution to ConflictResolver and persisting overrides.
 */
public class ConflictResolutionServiceImpl implements ConflictResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(ConflictResolutionServiceImpl.class);

    private final ConflictResolver conflictResolver;
    private final ExecutionLogDao executionLogDao;

    public ConflictResolutionServiceImpl(ConflictResolver conflictResolver, ExecutionLogDao executionLogDao) {
        this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver cannot be null");
        this.executionLogDao = Objects.requireNonNull(executionLogDao, "executionLogDao cannot be null");
    }

    public ConflictResolutionServiceImpl(ExecutionLogDao executionLogDao) {
        this(new ConflictResolver(), executionLogDao);
    }

    @Override
    public ResolutionResult resolveAndPersist(List<TriggeredRule> triggeredRules) {
        Objects.requireNonNull(triggeredRules, "triggeredRules cannot be null");
        ResolutionResult result = conflictResolver.resolve(triggeredRules);
        for (OverrideRecord override : result.overrides()) {
            try {
                executionLogDao.saveOverrideRecord(override);
            } catch (Exception e) {
                logger.error("Failed to persist override record id={}: {}", override.id(), e.getMessage(), e);
            }
        }

        return result;
    }
}

