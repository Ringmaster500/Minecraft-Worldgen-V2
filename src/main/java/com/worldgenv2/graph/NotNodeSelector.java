package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Inverts the selection of a child {@link NodeSelector}.
 */
public final class NotNodeSelector implements NodeSelector {

    private final NodeSelector child;

    public NotNodeSelector(NodeSelector child) {
        this.child = Objects.requireNonNull(child, "child");
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        return !child.select(node, graph);
    }

    public NodeSelector getChild() {
        return child;
    }
}
