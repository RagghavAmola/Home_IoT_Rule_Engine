package com.smarthome.engine.graph;

import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.Rule;

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
 * Directed dependency graph tracking rule actuation-trigger relationships.
 * An edge A -> B exists when A's writeSet overlaps with B's readSet.
 */
public class RuleDependencyGraph {

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
     * Adds a rule node to the dependency graph and updates edges.
     */
    public synchronized void addRule(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        removeRule(rule.getId());

        rulesById.put(rule.getId(), rule);
        adjacencyMap.put(rule.getId(), new LinkedHashSet<>());

        for (DeviceStateKey readKey : rule.getReadSet()) {
            readersByKey.computeIfAbsent(readKey, k -> new HashSet<>()).add(rule.getId());
        }

        for (DeviceStateKey writeKey : rule.getWriteSet()) {
            Set<String> readers = readersByKey.get(writeKey);
            if (readers != null) {
                for (String readerId : readers) {
                    adjacencyMap.get(rule.getId()).add(readerId);
                }
            }
        }

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
     * Removes a rule node and its associated edges from the graph.
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

        for (Set<String> neighbors : adjacencyMap.values()) {
            neighbors.remove(ruleId);
        }
    }

    /**
     * Rebuilds the entire dependency graph from a provided collection of rules.
     */
    public synchronized void rebuild(Collection<Rule> rules) {
        Objects.requireNonNull(rules, "rules cannot be null");
        rulesById.clear();
        adjacencyMap.clear();
        readersByKey.clear();

        for (Rule rule : rules) {
            rulesById.put(rule.getId(), rule);
            adjacencyMap.put(rule.getId(), new LinkedHashSet<>());
            for (DeviceStateKey readKey : rule.getReadSet()) {
                readersByKey.computeIfAbsent(readKey, k -> new HashSet<>()).add(rule.getId());
            }
        }

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
     * Evaluates whether adding or modifying a candidate rule introduces any dependency cycle.
     */
    public List<String> findCyclePath(Rule candidate, Collection<Rule> existing) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(existing, "existing rules cannot be null");

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
     */
    public synchronized List<String> getTopologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adjacencyMap.keySet()) {
            inDegree.put(node, 0);
        }
        for (Set<String> targets : adjacencyMap.values()) {
            for (String target : targets) {
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

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

