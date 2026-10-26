package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Inverts the selection of a child {@link EdgeSelector}.
 */
public final class NotEdgeSelector implements EdgeSelector {

    private final EdgeSelector child;

    public NotEdgeSelector(EdgeSelector child) {
        this.child = Objects.requireNonNull(child, "child");
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        return !child.select(edge, graph);
    }

    public EdgeSelector getChild() {
        return child;
    }
}
