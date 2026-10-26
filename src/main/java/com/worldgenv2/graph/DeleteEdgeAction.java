package com.worldgenv2.graph;

/**
 * Deletes the edge from the procedural graph.
 */
public final class DeleteEdgeAction implements EdgeAction {

    public static final DeleteEdgeAction INSTANCE = new DeleteEdgeAction();

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        graph.removeEdge(edge.nodeA(), edge.nodeB());
    }
}
