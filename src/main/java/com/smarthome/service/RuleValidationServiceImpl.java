package com.smarthome.service;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Rule;
import com.smarthome.engine.graph.RuleDependencyGraph;
import com.smarthome.exception.EntityNotFoundException;
import com.smarthome.exception.LogicalConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link RuleValidationService} providing DAG cycle checking before rule persistence.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless service relying on thread-safe DAO and
 * synchronized graph evaluation. All state is isolated per method invocation.
 * </p>
 */
public class RuleValidationServiceImpl implements RuleValidationService {

    private static final Logger logger = LoggerFactory.getLogger(RuleValidationServiceImpl.class);

    private final RuleDao ruleDao;
    private final RuleDependencyGraph dependencyGraph;

    public RuleValidationServiceImpl(RuleDao ruleDao) {
        this(ruleDao, new RuleDependencyGraph());
    }

    public RuleValidationServiceImpl(RuleDao ruleDao, RuleDependencyGraph dependencyGraph) {
        this.ruleDao = Objects.requireNonNull(ruleDao, "ruleDao cannot be null");
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph cannot be null");
    }

    @Override
    public Rule validateAndSave(Rule candidate) throws LogicalConflictException {
        Objects.requireNonNull(candidate, "candidate rule cannot be null");

        // Guarantee candidate has a valid non-empty ID before graph evaluation
        Rule candidateWithId = candidate;
        if (candidate.getId() == null || candidate.getId().isBlank()) {
            candidateWithId = new Rule(
                    UUID.randomUUID().toString(),
                    candidate.getName(),
                    candidate.isEnabled(),
                    candidate.getTier(),
                    candidate.getTieBreakOrder(),
                    candidate.getRootCondition(),
                    candidate.getActions(),
                    candidate.getCreatedAt()
            );
        }

        // Active DAG cycle validation
        if (candidateWithId.isEnabled()) {
            List<Rule> existingEnabledRules = ruleDao.findAllEnabledRules();
            List<String> cyclePath = dependencyGraph.findCyclePath(candidateWithId, existingEnabledRules);

            if (!cyclePath.isEmpty()) {
                String errorMsg = String.format(
                        "Cannot save rule '%s' (id=%s): Circular dependency detected: %s",
                        candidateWithId.getName(),
                        candidateWithId.getId(),
                        String.join(" -> ", cyclePath)
                );
                logger.warn("Rule validation failed for id={}: cycle path = {}", candidateWithId.getId(), cyclePath);
                throw new LogicalConflictException(errorMsg, cyclePath);
            }
        }

        logger.info("Rule validation succeeded for id={}. Persisting to DAO.", candidateWithId.getId());
        return ruleDao.save(candidateWithId);
    }

    @Override
    public Rule enableRule(String ruleId) throws LogicalConflictException {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        Rule existing = ruleDao.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Cannot enable rule; rule not found with id: " + ruleId));

        if (existing.isEnabled()) {
            return existing;
        }

        Rule toEnable = existing.withEnabled(true);
        return validateAndSave(toEnable);
    }
}

