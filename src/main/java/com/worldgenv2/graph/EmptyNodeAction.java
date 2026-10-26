package com.worldgenv2.graph;

/**
 * No-op node action.
 */
public final class EmptyNodeAction implements NodeAction {

    public static final EmptyNodeAction INSTANCE = new EmptyNodeAction();

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        // No-op
    }
}
