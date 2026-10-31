package com.smarthome.dao;

import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.RuleExecutionRecord;

import java.time.Instant;
import java.util.List;

/**
 * Data Access Object interface for persisting and querying rule execution and override audit logs.
 */
public interface ExecutionLogDao {

    /**
     * Persists an audit record of a rule evaluation and action execution.
     *
     * @param record execution log record
     */
    void saveExecutionRecord(RuleExecutionRecord record);

    /**
     * Retrieves recent execution audit records for a specific rule.
     *
     * @param ruleId identifier of the rule
     * @param limit  maximum number of records to return
     * @return list of execution records ordered by timestamp descending
     */
    List<RuleExecutionRecord> findExecutionRecordsByRuleId(String ruleId, int limit);

    /**
     * Retrieves the most recent execution audit records across all rules.
     *
     * @param limit maximum number of records to return
     * @return list of execution records ordered by timestamp descending
     */
    List<RuleExecutionRecord> findRecentExecutionRecords(int limit);

    /**
     * Persists an audit record of a priority tier conflict override.
     *
     * @param record override log record
     */
    void saveOverrideRecord(OverrideRecord record);

    /**
     * Retrieves the most recent override audit records across all rules.
     *
     * @param limit maximum number of records to return
     * @return list of override records ordered by timestamp descending
     */
    List<OverrideRecord> findRecentOverrideRecords(int limit);

    /**
     * Clears audit records older than the specified timestamp threshold.
     *
     * @param threshold cutoff instant
     * @return total count of records deleted
     */
    int clearLogsOlderThan(Instant threshold);
}

