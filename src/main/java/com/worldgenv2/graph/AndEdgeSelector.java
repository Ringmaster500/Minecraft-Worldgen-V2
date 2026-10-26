package com.worldgenv2.graph;

import java.util.List;

/**
 * Selects an edge if and only if all child {@link EdgeSelector} instances select it.
 */
public final class AndEdgeSelector implements EdgeSelector {

    private final List<EdgeSelector> children;

    public AndEdgeSelector(List<EdgeSelector> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        for (EdgeSelector child : children) {
            if (!child.select(edge, graph)) {
                return false;
            }
        }
        return true;
    }

    public List<EdgeSelector> getChildren() {
        return children;
    }
}
