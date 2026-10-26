package com.worldgenv2.graph;

/**
 * Deletes the node and all of its incident edges from the procedural graph.
 */
public final class DeleteNodeAction implements NodeAction {

    public static final DeleteNodeAction INSTANCE = new DeleteNodeAction();

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        graph.removeNode(node.getId());
    }
}
