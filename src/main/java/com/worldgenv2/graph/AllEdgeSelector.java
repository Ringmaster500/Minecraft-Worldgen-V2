package com.worldgenv2.graph;

/**
 * Selects all edges unconditionally.
 */
public final class AllEdgeSelector implements EdgeSelector {

    public static final AllEdgeSelector INSTANCE = new AllEdgeSelector();

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        return true;
    }
}
