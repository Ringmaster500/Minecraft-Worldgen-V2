package com.worldgenv2.graph;

/**
 * Strategy contract for supplying {@link GraphContent} to a {@link GraphNode}.
 */
@FunctionalInterface
public interface ContentSupplier {
    GraphContent supply(GraphNode node, long seed);
}
