package com.smarthome.dao;

import com.smarthome.domain.Rule;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for managing automation rules, including their hierarchical conditions and actions.
 */
public interface RuleDao {

    /**
     * Persists a rule with its complete hierarchical condition tree and actions transactionally.
     * If the rule already exists, updates it and replaces its condition hierarchy and actions.
     *
     * @param rule the rule to save
     * @return the saved rule
     */
    Rule save(Rule rule);

    /**
     * Retrieves a rule by its unique identifier, reconstructing its full condition tree and actions.
     *
     * @param id unique identifier of the rule
     * @return Optional containing the reconstructed Rule if found, or empty Optional
     */
    Optional<Rule> findById(String id);

    /**
     * Retrieves all rules configured in the system.
     *
     * @return list of all rules
     */
    List<Rule> findAll();

    /**
     * Retrieves all enabled rules ordered by priority tier rank ascending (highest priority first),
     * then tie-break order ascending, then creation time ascending.
     *
     * @return list of enabled rules in evaluation order
     */
    List<Rule> findAllEnabledRules();

    /**
     * Retrieves all rules that reference a specific device ID either in their condition read-set
     * or action write-set.
     *
     * @param deviceId identifier of the device
     * @return list of rules referencing the device
     */
    List<Rule> findByDeviceId(String deviceId);

    /**
     * Deletes a rule by its identifier. Associated condition groups, leaves, and actions are removed.
     *
     * @param id identifier of the rule to delete
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Checks if a rule with the given identifier exists.
     *
     * @param id rule identifier
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);
}

