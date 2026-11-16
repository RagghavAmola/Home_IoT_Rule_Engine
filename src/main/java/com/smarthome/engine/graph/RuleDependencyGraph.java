package com.smarthome.engine.graph;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Directed dependency graph representing triggering relationships among smart home automation rules.
 * <p>
 * <b>Dependency Semantics:</b>
 * A directed edge {@code A -> B} exists when an actuation produced by rule {@code A}
 * can trigger the condition evaluated by rule {@code B}. Specifically, an edge {@code A -> B}
 * is created whenever {@code A.getWriteSet()} intersects with {@code B.getReadSet()}
 * on at least one {@link DeviceStateKey}.
 * </p>
 * <p>
 * <b>Conservative Overlap Strategy:</b>
 * In accordance with smart home safety requirements, any overlap between {@code A.writeSet}
 * and {@code B.readSet} is conservatively treated as a dependency edge, irrespective of target
 * value matching or current state. This conservative approach guarantees that no potential
 * cyclic actuation feedback loop is missed.
 * </p>
 * <p>
 * <b>Thread-safety strategy:</b>
 * Internal graph state is guarded by explicit object synchronization ({@code synchronized} methods).
 * Query methods return defensive unmodifiable snapshots.
 * </p>
 */
public class RuleDependencyGraph {

    private static final Logger logger = LoggerFactory.getLogger(RuleDependencyGraph.class);

    private final Map<String, Rule> rulesById = new LinkedHashMap<>();
    private final Map<String, Set<String>> adjacencyMap = new LinkedHashMap<>();
    private final Map<DeviceStateKey, Set<String>> readersByKey = new HashMap<>();
    private final CycleDetector cycleDetector;

    public RuleDependencyGraph() {
        this(new CycleDetector());
    }

    public RuleDependencyGraph(CycleDetector cycleDetector) {
        this.cycleDetector = Objects.requireNonNull(cycleDetector, "cycleDetector cannot be null");
    }

    /**
     * Adds a rule node to the dependency graph and updates all incoming, outgoing, and self-loop edges.
     *
     * @param rule the rule to insert
     */
    public synchronized void addRule(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        removeRule(rule.getId());

        rulesById.put(rule.getId(), rule);
        adjacencyMap.put(rule.getId(), new LinkedHashSet<>());

        // Register in inverted reader index
        for (DeviceStateKey readKey : rule.getReadSet()) {
            readersByKey.computeIfAbsent(readKey, k -> new HashSet<>()).add(rule.getId());
        }

        // Recompute edges involving this rule
        // 1. Outgoing edges: for each writeKey of this rule, find existing rules reading it
        for (DeviceStateKey writeKey : rule.getWriteSet()) {
            Set<String> readers = readersByKey.get(writeKey);
            if (readers != null) {
                for (String readerId : readers) {
                    adjacencyMap.get(rule.getId()).add(readerId);
                }
            }
        }

        // 2. Incoming edges: for each existing rule whose writeSet contains any readKey of this rule
        for (Rule existing : rulesById.values()) {
            if (!existing.getId().equals(rule.getId())) {
                for (DeviceStateKey readKey : rule.getReadSet()) {
                    if (existing.getWriteSet().contains(readKey)) {
                        adjacencyMap.get(existing.getId()).add(rule.getId());
                    }
                }
            }
        }
    }

    /**
     * Removes a rule node and all its associated incoming and outgoing edges from the graph.
     *
     * @param ruleId unique identifier of the rule to remove
     */
    public synchronized void removeRule(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Rule removedRule = rulesById.remove(ruleId);
        adjacencyMap.remove(ruleId);

        if (removedRule != null) {
            for (DeviceStateKey readKey : removedRule.getReadSet()) {
                Set<String> readers = readersByKey.get(readKey);
                if (readers != null) {
                    readers.remove(ruleId);
                    if (readers.isEmpty()) {
                        readersByKey.remove(readKey);
                    }
                }
            }
        }

        // Remove incoming edges from remaining nodes
        for (Set<String> neighbors : adjacencyMap.values()) {
            neighbors.remove(ruleId);
        }
    }

    /**
     * Rebuilds the entire dependency graph from a provided collection of rules.
     *
     * @param rules collection of rules to populate into the graph
     */
    public synchronized void rebuild(Collection<Rule> rules) {
        Objects.requireNonNull(rules, "rules cannot be null");
        rulesById.clear();
        adjacencyMap.clear();
        readersByKey.clear();

        // 1. Register all nodes and populate inverted reader index
        for (Rule rule : rules) {
            rulesById.put(rule.getId(), rule);
            adjacencyMap.put(rule.getId(), new LinkedHashSet<>());
            for (DeviceStateKey readKey : rule.getReadSet()) {
                readersByKey.computeIfAbsent(readKey, k -> new HashSet<>()).add(rule.getId());
            }
        }

        // 2. Establish directed edges: A -> B when A.writeSet intersects B.readSet
        for (Rule rule : rules) {
            Set<String> outNeighbors = adjacencyMap.get(rule.getId());
            for (DeviceStateKey writeKey : rule.getWriteSet()) {
                Set<String> readers = readersByKey.get(writeKey);
                if (readers != null) {
                    outNeighbors.addAll(readers);
                }
            }
        }
    }

    /**
     * Evaluates whether adding or modifying a candidate rule within the existing rule set introduces
     * any dependency cycle.
     *
     * @param candidate the proposed candidate rule
     * @param existing  collection of existing active rules
     * @return list representing the cycle path (e.g. {@code ["R1", "R2", "R1"]}),
     *         or an empty list if no cycle is introduced
     */
    public List<String> findCyclePath(Rule candidate, Collection<Rule> existing) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(existing, "existing rules cannot be null");

        // Build temporary graph model containing existing rules plus candidate
        Map<String, Rule> combinedRules = new LinkedHashMap<>();
        for (Rule r : existing) {
            if (r.isEnabled()) {
                combinedRules.put(r.getId(), r);
            }
        }
        if (candidate.isEnabled()) {
            combinedRules.put(candidate.getId(), candidate);
        } else {
            combinedRules.remove(candidate.getId());
        }

        RuleDependencyGraph tempGraph = new RuleDependencyGraph(this.cycleDetector);
        tempGraph.rebuild(combinedRules.values());

        Optional<List<String>> cycle = tempGraph.cycleDetector.findCycle(tempGraph.getAdjacencyMap());
        return cycle.orElse(Collections.emptyList());
    }

    /**
     * Computes a valid topological ordering of the rules using Kahn's algorithm.
     *
     * @return list of rule identifiers ordered such that for every directed edge {@code u -> v},
     *         {@code u} precedes {@code v}
     * @throws IllegalStateException if the graph contains a directed cycle
     */
    public synchronized List<String> getTopologicalOrder() {
        // Calculate in-degrees
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adjacencyMap.keySet()) {
            inDegree.put(node, 0);
        }
        for (Set<String> targets : adjacencyMap.values()) {
            for (String target : targets) {
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

        // Initialize queue with zero in-degree nodes
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            order.add(u);

            Set<String> neighbors = adjacencyMap.getOrDefault(u, Collections.emptySet());
            for (String v : neighbors) {
                int updatedInDegree = inDegree.get(v) - 1;
                inDegree.put(v, updatedInDegree);
                if (updatedInDegree == 0) {
                    queue.add(v);
                }
            }
        }

        if (order.size() != adjacencyMap.size()) {
            Optional<List<String>> cycle = cycleDetector.findCycle(adjacencyMap);
            String cycleDesc = cycle.map(Object::toString).orElse("unknown cycle");
            throw new IllegalStateException("Graph contains a cycle: " + cycleDesc);
        }

        return Collections.unmodifiableList(order);
    }

    /**
     * Returns a snapshot of the current directed adjacency map.
     *
     * @return unmodifiable map of rule IDs to their downstream neighbors
     */
    public synchronized Map<String, Set<String>> getAdjacencyMap() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : adjacencyMap.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Returns an unmodifiable snapshot of the rules currently registered in the graph.
     *
     * @return map of rule ID to Rule entity
     */
    public synchronized Map<String, Rule> getRules() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(rulesById));
    }
}

