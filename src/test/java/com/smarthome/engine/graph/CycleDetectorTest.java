package com.smarthome.engine.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class CycleDetectorTest {

    private CycleDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CycleDetector();
    }
    @Test
    @DisplayName("Null adjacency map throws NullPointerException")
    void testNullGraphThrowsException() {
        assertThrows(NullPointerException.class, () -> detector.findCycle(null));
    }

    @Test
    @DisplayName("No-cycle linear chain: R1 -> R2 -> R3")
    void testNoCycleChain() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("R1", Set.of("R2"));
        graph.put("R2", Set.of("R3"));
        graph.put("R3", Collections.emptySet());

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertFalse(cycle.isPresent(), "Linear chain must not report a cycle");
    }

    @Test
    @DisplayName("Simple 2-cycle: R1 -> R2 -> R1")
    void testSimpleTwoCycle() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("R1", Set.of("R2"));
        graph.put("R2", Set.of("R1"));

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertTrue(cycle.isPresent(), "2-cycle must be detected");
        List<String> path = cycle.get();
        assertEquals(3, path.size());
        assertEquals(path.get(0), path.get(path.size() - 1), "Cycle must start and end at the same node");
    }

    @Test
    @DisplayName("3-cycle: R1 -> R2 -> R3 -> R1")
    void testThreeCycle() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("R1", Set.of("R2"));
        graph.put("R2", Set.of("R3"));
        graph.put("R3", Set.of("R1"));

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertTrue(cycle.isPresent(), "3-cycle must be detected");
        List<String> path = cycle.get();
        assertEquals(4, path.size());
        assertEquals(path.get(0), path.get(path.size() - 1));
    }

    @Test
    @DisplayName("Self-loop: R1 -> R1")
    void testSelfLoop() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("R1", Set.of("R1"));

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertTrue(cycle.isPresent(), "Self-loop must be detected");
        assertEquals(List.of("R1", "R1"), cycle.get());
    }

    @Test
    @DisplayName("Diamond DAG (valid DAG, no false positive): R1 -> (R2, R3) -> R4")
    void testDiamondDagNoFalsePositive() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        graph.put("R1", Set.of("R2", "R3"));
        graph.put("R2", Set.of("R4"));
        graph.put("R3", Set.of("R4"));
        graph.put("R4", Collections.emptySet());

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertFalse(cycle.isPresent(), "Diamond graph is an acyclic DAG and must not report a cycle");
    }

    @Test
    @DisplayName("Disconnected components: separate DAGs have no cycle")
    void testDisconnectedAcyclicComponents() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        // Component 1
        graph.put("A1", Set.of("A2"));
        graph.put("A2", Collections.emptySet());
        // Component 2
        graph.put("B1", Set.of("B2"));
        graph.put("B2", Collections.emptySet());
        // Isolated node
        graph.put("C1", Collections.emptySet());

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertFalse(cycle.isPresent());
    }

    @Test
    @DisplayName("Disconnected components with a cycle in one component")
    void testDisconnectedComponentWithCycle() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        // Acyclic component
        graph.put("A1", Set.of("A2"));
        graph.put("A2", Collections.emptySet());
        // Cyclic component
        graph.put("B1", Set.of("B2"));
        graph.put("B2", Set.of("B3"));
        graph.put("B3", Set.of("B1"));

        Optional<List<String>> cycle = detector.findCycle(graph);
        assertTrue(cycle.isPresent(), "Cycle in disconnected component must be detected");
        List<String> path = cycle.get();
        assertEquals(4, path.size());
        assertEquals(path.get(0), path.get(path.size() - 1));
        assertTrue(path.contains("B1"));
        assertTrue(path.contains("B2"));
        assertTrue(path.contains("B3"));
    }
}

