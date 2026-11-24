package com.smarthome.service;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Rule;
import com.smarthome.engine.graph.RuleDependencyGraph;
import com.smarthome.exception.EntityNotFoundException;
import com.smarthome.exception.LogicalConflictException;
import com.smarthome.service.monitor.RuleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link RuleValidationService} providing DAG cycle checking before rule persistence
 * and orchestrating updates to both persistent storage and in-memory caches.
 * <p>
 * <b>Thread-safety strategy:</b> Stateless service relying on thread-safe DAO,
 * synchronized graph evaluation, and thread-safe {@link RuleCache}.
 * </p>
 */
public class RuleValidationServiceImpl implements RuleValidationService {

    private static final Logger logger = LoggerFactory.getLogger(RuleValidationServiceImpl.class);

    private final RuleDao ruleDao;
    private final RuleDependencyGraph dependencyGraph;
    private final RuleCache ruleCache;

    public RuleValidationServiceImpl(RuleDao ruleDao) {
        this(ruleDao, new RuleDependencyGraph(), null);
    }

    public RuleValidationServiceImpl(RuleDao ruleDao, RuleDependencyGraph dependencyGraph) {
        this(ruleDao, dependencyGraph, null);
    }

    public RuleValidationServiceImpl(RuleDao ruleDao, RuleDependencyGraph dependencyGraph, RuleCache ruleCache) {
        this.ruleDao = Objects.requireNonNull(ruleDao, "ruleDao cannot be null");
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph cannot be null");
        this.ruleCache = ruleCache;
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
        Rule saved = ruleDao.save(candidateWithId);
        if (ruleCache != null) {
            ruleCache.updateRule(saved);
        }
        return saved;
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

    @Override
    public Rule disableRule(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        Rule existing = ruleDao.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Cannot disable rule; rule not found with id: " + ruleId));

        if (!existing.isEnabled()) {
            return existing;
        }

        Rule disabled = existing.withEnabled(false);
        Rule saved = ruleDao.save(disabled);
        if (ruleCache != null) {
            ruleCache.updateRule(saved);
        }
        logger.info("Rule disabled: id='{}'", ruleId);
        return saved;
    }

    @Override
    public boolean deleteRule(String ruleId) {
        if (ruleId == null) return false;
        boolean deleted = ruleDao.deleteById(ruleId);
        if (deleted && ruleCache != null) {
            ruleCache.removeRule(ruleId);
        }
        logger.info("Rule deleted: id='{}', success={}", ruleId, deleted);
        return deleted;
    }

    @Override
    public List<Rule> findAllRules() {
        return ruleDao.findAll();
    }

    @Override
    public Optional<Rule> findRuleById(String ruleId) {
        if (ruleId == null) return Optional.empty();
        return ruleDao.findById(ruleId);
    }
}
