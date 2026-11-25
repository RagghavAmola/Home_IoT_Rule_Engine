package com.smarthome.engine.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Three-color iterative DFS cycle detector finding back-edges and cycle paths in rule graphs.
 */
public class CycleDetector {

    private enum Color {
        WHITE,
        GRAY,
        BLACK
    }

    private static class DfsFrame {
        final String node;
        final Iterator<String> neighborIterator;

        DfsFrame(String node, Iterator<String> neighborIterator) {
            this.node = node;
            this.neighborIterator = neighborIterator;
        }
    }

    /**
     * Finds a directed cycle in the given directed graph adjacency map.
     *
     * @param adjacencyMap directed adjacency list where key is rule ID and value is set of downstream rule IDs
     * @return an {@link Optional} containing the ordered cycle path (starting and ending with the same node),
     *         or {@link Optional#empty()} if the graph is a Directed Acyclic Graph (DAG)
     */
    public Optional<List<String>> findCycle(Map<String, Set<String>> adjacencyMap) {
        Objects.requireNonNull(adjacencyMap, "adjacencyMap cannot be null");

        Map<String, Color> colors = new HashMap<>();
        for (String node : adjacencyMap.keySet()) {
            colors.put(node, Color.WHITE);
        }

        for (String startNode : adjacencyMap.keySet()) {
            if (colors.get(startNode) == Color.WHITE) {
                Optional<List<String>> cycle = dfsIterative(startNode, adjacencyMap, colors);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<List<String>> dfsIterative(
            String startNode,
            Map<String, Set<String>> adjacencyMap,
            Map<String, Color> colors
    ) {
        Deque<DfsFrame> stack = new ArrayDeque<>();
        List<String> currentPath = new ArrayList<>();

        colors.put(startNode, Color.GRAY);
        currentPath.add(startNode);
        stack.push(new DfsFrame(startNode, getNeighborIterator(startNode, adjacencyMap)));

        while (!stack.isEmpty()) {
            DfsFrame top = stack.peek();

            if (top.neighborIterator.hasNext()) {
                String neighbor = top.neighborIterator.next();
                Color neighborColor = colors.getOrDefault(neighbor, Color.WHITE);

                if (neighborColor == Color.GRAY) {
                    // Back-edge detected! Reconstruct the exact cycle path
                    List<String> cyclePath = new ArrayList<>();
                    if (neighbor.equals(top.node)) {
                        // Self-loop: [node, node]
                        cyclePath.add(top.node);
                        cyclePath.add(top.node);
                    } else {
                        int startIndex = currentPath.indexOf(neighbor);
                        for (int i = startIndex; i < currentPath.size(); i++) {
                            cyclePath.add(currentPath.get(i));
                        }
                        cyclePath.add(neighbor);
                    }
                    return Optional.of(Collections.unmodifiableList(cyclePath));
                } else if (neighborColor == Color.WHITE) {
                    colors.put(neighbor, Color.GRAY);
                    currentPath.add(neighbor);
                    stack.push(new DfsFrame(neighbor, getNeighborIterator(neighbor, adjacencyMap)));
                }
            } else {
                // All neighbors fully explored for this node
                stack.pop();
                colors.put(top.node, Color.BLACK);
                currentPath.remove(currentPath.size() - 1);
            }
        }

        return Optional.empty();
    }

    private Iterator<String> getNeighborIterator(String node, Map<String, Set<String>> adjacencyMap) {
        Set<String> neighbors = adjacencyMap.get(node);
        return neighbors != null ? neighbors.iterator() : Collections.emptyIterator();
    }
}