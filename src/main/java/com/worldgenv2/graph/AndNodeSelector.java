package com.worldgenv2.graph;

import java.util.List;

/**
 * Selects a node if and only if all child {@link NodeSelector} instances select it.
 */
public final class AndNodeSelector implements NodeSelector {

    private final List<NodeSelector> children;

    public AndNodeSelector(List<NodeSelector> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        for (NodeSelector child : children) {
            if (!child.select(node, graph)) {
                return false;
            }
        }
        return true;
    }

    public List<NodeSelector> getChildren() {
        return children;
    }
}
