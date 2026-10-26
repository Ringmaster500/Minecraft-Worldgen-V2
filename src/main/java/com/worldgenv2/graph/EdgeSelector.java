package com.worldgenv2.graph;

/**
 * Filter contract for selecting subsets of edges in a {@link ProceduralGraph}.
 */
@FunctionalInterface
public interface EdgeSelector {
    boolean select(GraphEdge edge, ProceduralGraph graph);
}
