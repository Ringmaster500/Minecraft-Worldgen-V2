package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Conditionally executes a {@link NodeAction} only if the node is selected by a {@link NodeSelector}.
 */
public final class SelectorNodeAction implements NodeAction {

    private final NodeSelector selector;
    private final NodeAction action;

    public SelectorNodeAction(NodeSelector selector, NodeAction action) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        if (selector.select(node, graph)) {
            action.apply(node, graph, seed);
        }
    }

    public NodeSelector getSelector() {
        return selector;
    }

    public NodeAction getAction() {
        return action;
    }
}
