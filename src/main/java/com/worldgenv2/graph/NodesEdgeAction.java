package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Runs a {@link NodeAction} on both endpoint nodes of an edge.
 */
public final class NodesEdgeAction implements EdgeAction {

    private final NodeAction nodeAction;

    public NodesEdgeAction(NodeAction nodeAction) {
        this.nodeAction = Objects.requireNonNull(nodeAction, "nodeAction");
    }

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        GraphNode a = graph.getNode(edge.nodeA());
        if (a != null) {
            nodeAction.apply(a, graph, seed);
        }
        GraphNode b = graph.getNode(edge.nodeB());
        if (b != null) {
            nodeAction.apply(b, graph, seed);
        }
    }

    public NodeAction getNodeAction() {
        return nodeAction;
    }
}
