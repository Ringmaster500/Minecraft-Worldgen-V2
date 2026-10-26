package com.worldgenv2.graph;

/**
 * No-op edge action.
 */
public final class EmptyEdgeAction implements EdgeAction {

    public static final EmptyEdgeAction INSTANCE = new EmptyEdgeAction();

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        // No-op
    }
}
