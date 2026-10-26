package com.worldgenv2.graph;

/**
 * Action operating on an individual {@link GraphNode} within a {@link ProceduralGraph}.
 */
@FunctionalInterface
public interface NodeAction {
    void apply(GraphNode node, ProceduralGraph graph, long seed);
}
