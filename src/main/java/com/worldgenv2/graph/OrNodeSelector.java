package com.worldgenv2.graph;

import java.util.List;

/**
 * Selects a node if any child {@link NodeSelector} selects it.
 */
public final class OrNodeSelector implements NodeSelector {

    private final List<NodeSelector> children;

    public OrNodeSelector(List<NodeSelector> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        for (NodeSelector child : children) {
            if (child.select(node, graph)) {
                return true;
            }
        }
        return false;
    }

    public List<NodeSelector> getChildren() {
        return children;
    }
}
