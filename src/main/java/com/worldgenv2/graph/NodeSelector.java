package com.worldgenv2.graph;

/**
 * Filter contract for selecting subsets of nodes in a {@link ProceduralGraph}.
 */
@FunctionalInterface
public interface NodeSelector {
    boolean select(GraphNode node, ProceduralGraph graph);
}
