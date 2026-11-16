package com.smarthome.service;

import com.smarthome.domain.Rule;
import com.smarthome.exception.LogicalConflictException;

/**
 * Service interface responsible for validating automation rules and enforcing graph invariants (DAG constraints)
 * prior to persistence.
 */
public interface RuleValidationService {

    /**
     * Validates that adding or updating the candidate rule does not introduce a circular dependency
     * among active rules, and persists the rule if valid.
     * <p>
     * If the candidate rule does not have an ID, one is generated prior to graph validation.
     * If a cycle is detected, {@link LogicalConflictException} is thrown and the DAO is not called.
     * </p>
     *
     * @param candidate candidate rule to validate and persist
     * @return the persisted rule entity
     * @throws LogicalConflictException if the candidate rule introduces a dependency cycle
     */
    Rule validateAndSave(Rule candidate) throws LogicalConflictException;

    /**
     * Re-enables a disabled rule, validating that activating it maintains DAG acyclicity.
     *
     * @param ruleId unique identifier of the rule to enable
     * @return the updated and saved enabled rule
     * @throws LogicalConflictException if re-enabling introduces a dependency cycle
     */
    Rule enableRule(String ruleId) throws LogicalConflictException;
}

