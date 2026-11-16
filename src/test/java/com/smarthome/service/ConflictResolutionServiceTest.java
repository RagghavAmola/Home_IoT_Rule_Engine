package com.smarthome.service;

import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.engine.priority.ResolutionResult;
import com.smarthome.engine.priority.TriggeredRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

class ConflictResolutionServiceTest {

    private ExecutionLogDao executionLogDao;
    private ConflictResolutionService service;

    @BeforeEach
    void setUp() {
        executionLogDao = mock(ExecutionLogDao.class);
        service = new ConflictResolutionServiceImpl(executionLogDao);
    }

    private Rule createRule(String id, PriorityTier tier, Action action) {
        LeafCondition cond = new LeafCondition("sensor", "state", Operator.EQ, "1");
        return new Rule(id, "Rule " + id, true, tier, 0, cond, List.of(action), Instant.now());
    }

    @Test
    @DisplayName("Service resolves conflict and persists override record via ExecutionLogDao")
    void testResolveAndPersistSavesOverride() {
        Action safetyAction = new Action("heater-1", "power", "OFF");
        Action comfortAction = new Action("heater-1", "power", "ON");

        Rule safetyRule = createRule("R_SAFE", PriorityTier.SAFETY, safetyAction);
        Rule comfortRule = createRule("R_COMFORT", PriorityTier.COMFORT, comfortAction);

        List<TriggeredRule> triggered = List.of(
                new TriggeredRule(comfortRule),
                new TriggeredRule(safetyRule)
        );

        ResolutionResult result = service.resolveAndPersist(triggered);

        assertEquals(1, result.toExecute().size());
        assertEquals("OFF", result.toExecute().get(0).targetValue());
        assertEquals(1, result.overrides().size());

        // Verify that ExecutionLogDao persisted the override record
        verify(executionLogDao, times(1)).saveOverrideRecord(any(OverrideRecord.class));
    }

    @Test
    @DisplayName("Service with no conflicts does not invoke ExecutionLogDao")
    void testNoConflictDoesNotCallDao() {
        Action act = new Action("light-1", "power", "ON");
        Rule r = createRule("R1", PriorityTier.COMFORT, act);

        ResolutionResult result = service.resolveAndPersist(List.of(new TriggeredRule(r)));

        assertEquals(1, result.toExecute().size());
        assertEquals(0, result.overrides().size());

        verify(executionLogDao, never()).saveOverrideRecord(any());
    }
    @Test
    @DisplayName("Service throws NullPointerException when triggeredRules is null")
    void testResolveAndPersistNullThrowsException() {
        assertThrows(NullPointerException.class, () -> service.resolveAndPersist(null));
        verify(executionLogDao, never()).saveOverrideRecord(any());
    }

    @Test
    @DisplayName("Service handles DAO persistence exception gracefully without crashing")
    void testDaoExceptionHandledGracefully() {
        Action safetyAction = new Action("heater-1", "power", "OFF");
        Action comfortAction = new Action("heater-1", "power", "ON");

        Rule safetyRule = createRule("R_SAFE", PriorityTier.SAFETY, safetyAction);
        Rule comfortRule = createRule("R_COMFORT", PriorityTier.COMFORT, comfortAction);

        List<TriggeredRule> triggered = List.of(
                new TriggeredRule(comfortRule),
                new TriggeredRule(safetyRule)
        );

        doThrow(new RuntimeException("Simulated DB error")).when(executionLogDao).saveOverrideRecord(any());

        ResolutionResult result = service.resolveAndPersist(triggered);

        // Resolution completes successfully even if logging DAO throws
        assertEquals(1, result.toExecute().size());
        assertEquals(1, result.overrides().size());
        verify(executionLogDao, times(1)).saveOverrideRecord(any(OverrideRecord.class));
    }
}

