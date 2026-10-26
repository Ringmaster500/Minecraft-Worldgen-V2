package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Selects an edge if its endpoint nodes satisfy a {@link NodeSelector} according to a logical operator (AND / OR).
 */
public final class NodesEdgeSelector implements EdgeSelector {

    public enum Operator {
        AND,
        OR
    }

    private final NodeSelector nodeSelector;
    private final Operator operator;

    public NodesEdgeSelector(NodeSelector nodeSelector, Operator operator) {
        this.nodeSelector = Objects.requireNonNull(nodeSelector, "nodeSelector");
        this.operator = operator != null ? operator : Operator.AND;
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        GraphNode a = graph.getNode(edge.nodeA());
        GraphNode b = graph.getNode(edge.nodeB());
        if (a == null || b == null) {
            return false;
        }

        boolean selA = nodeSelector.select(a, graph);
        boolean selB = nodeSelector.select(b, graph);

        return operator == Operator.AND ? (selA && selB) : (selA || selB);
    }

    public NodeSelector getNodeSelector() {
        return nodeSelector;
    }

    public Operator getOperator() {
        return operator;
    }
}
