package com.worldgenv2.graph;

import java.util.List;

/**
 * Selects an edge if any child {@link EdgeSelector} selects it.
 */
public final class OrEdgeSelector implements EdgeSelector {

    private final List<EdgeSelector> children;

    public OrEdgeSelector(List<EdgeSelector> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        for (EdgeSelector child : children) {
            if (child.select(edge, graph)) {
                return true;
            }
        }
        return false;
    }

    public List<EdgeSelector> getChildren() {
        return children;
    }
}
