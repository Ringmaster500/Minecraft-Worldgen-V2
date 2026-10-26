package com.worldgenv2.graph;

/**
 * Action operating on an individual {@link GraphEdge} within a {@link ProceduralGraph}.
 */
@FunctionalInterface
public interface EdgeAction {
    void apply(GraphEdge edge, ProceduralGraph graph, long seed);
}
