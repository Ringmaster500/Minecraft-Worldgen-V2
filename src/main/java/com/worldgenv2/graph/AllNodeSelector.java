package com.worldgenv2.graph;

/**
 * Selects all nodes unconditionally.
 */
public final class AllNodeSelector implements NodeSelector {

    public static final AllNodeSelector INSTANCE = new AllNodeSelector();

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        return true;
    }
}
