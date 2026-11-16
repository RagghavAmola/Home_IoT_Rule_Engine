package com.smarthome.service;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.exception.EntityNotFoundException;
import com.smarthome.exception.LogicalConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleValidationServiceTest {

    private RuleDao ruleDao;
    private RuleValidationService validationService;

    @BeforeEach
    void setUp() {
        ruleDao = mock(RuleDao.class);
        validationService = new RuleValidationServiceImpl(ruleDao);
    }

    private Rule createRule(String id, String readDev, String readKey, String writeDev, String writeKey) {
        return createRule(id, readDev, readKey, writeDev, writeKey, true);
    }

    private Rule createRule(String id, String readDev, String readKey, String writeDev, String writeKey, boolean enabled) {
        LeafCondition cond = new LeafCondition(readDev, readKey, Operator.EQ, "1");
        Action act = new Action(writeDev, writeKey, "2");
        return new Rule(id, "Rule " + id, enabled, PriorityTier.COMFORT, 0, cond, List.of(act));
    }

    @Test
    @DisplayName("Valid candidate rule: persists to DAO successfully")
    void testValidateAndSaveSuccess() throws LogicalConflictException {
        Rule r1 = createRule("R1", "dev0", "state0", "dev1", "state1");
        Rule r2 = createRule("R2", "dev1", "state1", "dev2", "state2");

        when(ruleDao.findAllEnabledRules()).thenReturn(List.of(r1));
        when(ruleDao.save(r2)).thenReturn(r2);

        Rule saved = validationService.validateAndSave(r2);
        assertNotNull(saved);
        assertEquals("R2", saved.getId());
        verify(ruleDao, times(1)).save(r2);
    }

    @Test
    @DisplayName("Candidate introducing cycle: throws LogicalConflictException and NEVER calls DAO save")
    void testValidateAndSaveDetectsCycleAndNeverCallsDao() {
        Rule r1 = createRule("R1", "dev2", "state2", "dev1", "state1");
        // Candidate R2: reads dev1.state1 (from R1) and writes dev2.state2 (which triggers R1) -> 2-cycle!
        Rule r2 = createRule("R2", "dev1", "state1", "dev2", "state2");

        when(ruleDao.findAllEnabledRules()).thenReturn(List.of(r1));

        LogicalConflictException ex = assertThrows(
                LogicalConflictException.class,
                () -> validationService.validateAndSave(r2),
                "Should throw LogicalConflictException on detected cycle"
        );

        assertFalse(ex.getCyclePath().isEmpty());
        assertTrue(ex.getMessage().contains("Circular dependency detected"));
        assertEquals(3, ex.getCyclePath().size());

        // CRITICAL REQUIREMENT: verify DAO insert was NEVER called
        verify(ruleDao, never()).save(any());
    }

    @Test
    @DisplayName("Candidate rule with UUID: validated in graph and saved")
    void testCandidateWithUuidValidatedAndSaved() throws LogicalConflictException {
        LeafCondition cond = new LeafCondition("sensor-1", "lux", Operator.LT, "50");
        Action act = new Action("light-1", "power", "ON");
        String candidateId = java.util.UUID.randomUUID().toString();
        Rule candidate = new Rule(candidateId, "Auto Light", true, PriorityTier.COMFORT, 0, cond, List.of(act));

        when(ruleDao.findAllEnabledRules()).thenReturn(List.of());
        when(ruleDao.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Rule saved = validationService.validateAndSave(candidate);
        assertNotNull(saved.getId());
        assertEquals(candidateId, saved.getId());
        verify(ruleDao, times(1)).save(candidate);
    }

    @Test
    @DisplayName("Disabled rule with cycle potential: allowed to save without conflict")
    void testDisabledRuleAllowed() throws LogicalConflictException {
        Rule r1 = createRule("R1", "dev2", "state2", "dev1", "state1");
        // Candidate R2 has cyclic potential but is disabled
        Rule r2Disabled = createRule("R2", "dev1", "state1", "dev2", "state2", false);

        when(ruleDao.save(r2Disabled)).thenReturn(r2Disabled);

        Rule saved = validationService.validateAndSave(r2Disabled);
        assertNotNull(saved);
        assertFalse(saved.isEnabled());
        verify(ruleDao, times(1)).save(r2Disabled);
    }

    @Test
    @DisplayName("Re-enabling a disabled rule validates against active DAG and throws on cycle")
    void testEnableRuleValidation() {
        Rule r1 = createRule("R1", "dev2", "state2", "dev1", "state1", true);
        Rule r2Disabled = createRule("R2", "dev1", "state1", "dev2", "state2", false);

        when(ruleDao.findById("R2")).thenReturn(Optional.of(r2Disabled));
        when(ruleDao.findAllEnabledRules()).thenReturn(List.of(r1));

        LogicalConflictException ex = assertThrows(
                LogicalConflictException.class,
                () -> validationService.enableRule("R2")
        );

        assertFalse(ex.getCyclePath().isEmpty());
        verify(ruleDao, never()).save(any());
    }

    @Test
    @DisplayName("Re-enabling non-existent rule throws EntityNotFoundException")
    void testEnableNonExistentRuleThrows() {
        when(ruleDao.findById("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> validationService.enableRule("NON_EXISTENT"));
    }

    @Test
    @DisplayName("Editing an existing rule replaces previous version in validation graph")
    void testEditingRuleReplacesPreviousVersion() throws LogicalConflictException {
        // R1 previously wrote dev1.key1
        Rule r1Old = createRule("R1", "dev0", "key0", "dev1", "key1");
        // R2 reads dev1.key1, writes dev2.key2
        Rule r2 = createRule("R2", "dev1", "key1", "dev2", "key2");

        // Now edit R1 so it reads dev2.key2 and writes dev3.key3 (no cycle!)
        Rule r1New = createRule("R1", "dev2", "key2", "dev3", "key3");

        when(ruleDao.findAllEnabledRules()).thenReturn(List.of(r1Old, r2));
        when(ruleDao.save(r1New)).thenReturn(r1New);

        Rule saved = validationService.validateAndSave(r1New);
        assertEquals("R1", saved.getId());
        verify(ruleDao, times(1)).save(r1New);
    }
}

