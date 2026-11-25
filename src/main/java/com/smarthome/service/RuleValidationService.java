package com.smarthome.service;

import com.smarthome.domain.Rule;
import com.smarthome.exception.LogicalConflictException;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for validating automation rules and enforcing DAG invariants prior to persistence.
 */
public interface RuleValidationService {

    /**
     * Validates candidate rule acyclicity and persists it if valid.
     */
    Rule validateAndSave(Rule candidate) throws LogicalConflictException;

    /**
     * Re-enables a disabled rule, validating that activating it maintains DAG acyclicity.
     */
    Rule enableRule(String ruleId) throws LogicalConflictException;

    /**
     * Disables an active rule without deleting its configuration.
     *
     * @param ruleId unique identifier of the rule to disable
     * @return the updated disabled rule
     */
    Rule disableRule(String ruleId);

    /**
     * Deletes a rule from the system by ID.
     *
     * @param ruleId unique identifier of the rule to delete
     * @return true if deleted, false if not found
     */
    boolean deleteRule(String ruleId);

    /**
     * Retrieves all rules configured in the system.
     *
     * @return list of all rules
     */
    List<Rule> findAllRules();

    /**
     * Looks up a rule by ID.
     *
     * @param ruleId rule identifier
     * @return Optional containing rule if found
     */
    Optional<Rule> findRuleById(String ruleId);
}
