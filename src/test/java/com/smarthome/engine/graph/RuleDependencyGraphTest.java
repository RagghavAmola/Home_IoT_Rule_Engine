package com.smarthome.engine.graph;

import com.smarthome.domain.Action;
import com.smarthome.domain.Condition;
import com.smarthome.domain.ConditionGroup;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDependencyGraphTest {

    private RuleDependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = new RuleDependencyGraph();
    }

    private Rule createRule(String id, String readDev, String readKey, String writeDev, String writeKey) {
        LeafCondition cond = new LeafCondition(readDev, readKey, Operator.EQ, "1");
        Action act = new Action(writeDev, writeKey, "2");
        return new Rule(id, "Rule " + id, true, PriorityTier.COMFORT, 0, cond, List.of(act));
    }

    @Test
    @DisplayName("Chain dependency: R1 (writes K1) -> R2 (reads K1, writes K2) -> R3 (reads K2)")
    void testChainDependency() {
        Rule r1 = createRule("R1", "dev0", "state0", "dev1", "state1");
        Rule r2 = createRule("R2", "dev1", "state1", "dev2", "state2");
        Rule r3 = createRule("R3", "dev2", "state2", "dev3", "state3");

        graph.addRule(r1);
        graph.addRule(r2);
        graph.addRule(r3);

        Map<String, Set<String>> adj = graph.getAdjacencyMap();
        assertEquals(Set.of("R2"), adj.get("R1"));
        assertEquals(Set.of("R3"), adj.get("R2"));
        assertTrue(adj.get("R3").isEmpty());

        List<String> topoOrder = graph.getTopologicalOrder();
        assertEquals(List.of("R1", "R2", "R3"), topoOrder);
    }

    @Test
    @DisplayName("Self-loop: Rule action writes to the same key its condition reads")
    void testSelfLoopDetection() {
        Rule selfLoopRule = createRule("R_SELF", "thermostat-1", "temperature", "thermostat-1", "temperature");
        graph.addRule(selfLoopRule);

        Map<String, Set<String>> adj = graph.getAdjacencyMap();
        assertTrue(adj.get("R_SELF").contains("R_SELF"), "Self-loop edge must be in adjacency set");

        List<String> cyclePath = graph.findCyclePath(selfLoopRule, List.of());
        assertEquals(List.of("R_SELF", "R_SELF"), cyclePath);

        assertThrows(IllegalStateException.class, () -> graph.getTopologicalOrder());
    }

    @Test
    @DisplayName("2-cycle: R1 writes K1 -> R2 reads K1, writes K2 -> R1 reads K2")
    void testTwoCycleDetection() {
        Rule r1 = createRule("R1", "dev2", "key2", "dev1", "key1");
        Rule r2 = createRule("R2", "dev1", "key1", "dev2", "key2");

        List<String> cycle = graph.findCyclePath(r2, List.of(r1));
        assertFalse(cycle.isEmpty(), "Cycle must be detected between R1 and R2");
        assertEquals(3, cycle.size());
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
    }

    @Test
    @DisplayName("3-cycle: R1 -> R2 -> R3 -> R1")
    void testThreeCycleDetection() {
        Rule r1 = createRule("R1", "dev3", "key3", "dev1", "key1");
        Rule r2 = createRule("R2", "dev1", "key1", "dev2", "key2");
        Rule r3 = createRule("R3", "dev2", "key2", "dev3", "key3");

        List<String> cycle = graph.findCyclePath(r3, List.of(r1, r2));
        assertFalse(cycle.isEmpty(), "3-cycle must be detected");
        assertEquals(4, cycle.size());
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
    }

    @Test
    @DisplayName("Diamond DAG: valid DAG produces correct topological sort without cycle false positive")
    void testDiamondDagTopologicalSort() {
        // R1 writes dev1.key1
        // R2 reads dev1.key1, writes dev2.key2
        // R3 reads dev1.key1, writes dev3.key3
        // R4 reads dev2.key2 AND dev3.key3, writes dev4.key4
        Rule r1 = createRule("R1", "root", "start", "dev1", "key1");
        Rule r2 = createRule("R2", "dev1", "key1", "dev2", "key2");
        Rule r3 = createRule("R3", "dev1", "key1", "dev3", "key3");

        Condition c4 = new ConditionGroup(LogicalOperator.AND, List.of(
                new LeafCondition("dev2", "key2", Operator.EQ, "1"),
                new LeafCondition("dev3", "key3", Operator.EQ, "1")
        ));
        Rule r4 = new Rule("R4", "Rule R4", true, PriorityTier.ENERGY, 0, c4,
                List.of(new Action("dev4", "key4", "done")));

        graph.rebuild(List.of(r1, r2, r3, r4));

        List<String> order = graph.getTopologicalOrder();
        assertEquals(4, order.size());
        assertEquals("R1", order.get(0), "R1 must be first");
        assertEquals("R4", order.get(3), "R4 must be last");
        assertTrue(order.indexOf("R1") < order.indexOf("R2"));
        assertTrue(order.indexOf("R1") < order.indexOf("R3"));
        assertTrue(order.indexOf("R2") < order.indexOf("R4"));
        assertTrue(order.indexOf("R3") < order.indexOf("R4"));
    }

    @Test
    @DisplayName("Dynamic rule removal cleans up edges correctly")
    void testRemoveRule() {
        Rule r1 = createRule("R1", "dev0", "state0", "dev1", "state1");
        Rule r2 = createRule("R2", "dev1", "state1", "dev2", "state2");
        Rule r3 = createRule("R3", "dev2", "state2", "dev3", "state3");

        graph.rebuild(List.of(r1, r2, r3));
        assertEquals(3, graph.getTopologicalOrder().size());

        // Remove intermediate node R2
        graph.removeRule("R2");
        Map<String, Set<String>> adj = graph.getAdjacencyMap();
        assertFalse(adj.containsKey("R2"));
        assertFalse(adj.get("R1").contains("R2"));

        List<String> remainingOrder = graph.getTopologicalOrder();
        assertEquals(2, remainingOrder.size());
        assertTrue(remainingOrder.contains("R1"));
        assertTrue(remainingOrder.contains("R3"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Large graph (10,000 rules) performance sanity: DAG building and cycle detection")
    void testLargeGraphPerformanceSanity() {
        int ruleCount = 10000;
        List<Rule> rules = new ArrayList<>(ruleCount);

        for (int i = 0; i < ruleCount; i++) {
            String readDev = "dev_" + i;
            String readKey = "val";
            String writeDev = "dev_" + (i + 1);
            String writeKey = "val";
            rules.add(createRule("RULE_" + i, readDev, readKey, writeDev, writeKey));
        }

        long start = System.currentTimeMillis();
        graph.rebuild(rules);
        List<String> order = graph.getTopologicalOrder();
        long duration = System.currentTimeMillis() - start;

        assertEquals(ruleCount, order.size());
        assertEquals("RULE_0", order.get(0));
        assertEquals("RULE_" + (ruleCount - 1), order.get(ruleCount - 1));
        assertTrue(duration < 3000, "10k rules graph build & topological sort should complete in under 3s, took: " + duration + "ms");

        // Now verify cycle detection on candidate creating loop: RULE_9999 writes back to dev_0.val (RULE_0)
        // Now verify cycle detection on candidate creating loop with a dedicated loop rule ID
        Rule loopCandidate = createRule("RULE_LOOP", "dev_" + (ruleCount - 1), "val", "dev_0", "val");
        List<String> cycle = graph.findCyclePath(loopCandidate, rules);
        assertFalse(cycle.isEmpty(), "Loop across 10,000 rules must be detected");;
    }
}

