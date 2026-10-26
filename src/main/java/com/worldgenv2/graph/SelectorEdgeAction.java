package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Conditionally executes an {@link EdgeAction} only if the edge is selected by an {@link EdgeSelector}.
 */
public final class SelectorEdgeAction implements EdgeAction {

    private final EdgeSelector selector;
    private final EdgeAction action;

    public SelectorEdgeAction(EdgeSelector selector, EdgeAction action) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        if (selector.select(edge, graph)) {
            action.apply(edge, graph, seed);
        }
    }

    public EdgeSelector getSelector() {
        return selector;
    }

    public EdgeAction getAction() {
        return action;
    }
}
