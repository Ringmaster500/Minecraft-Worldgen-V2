package com.worldgenv2.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes a {@link NodeAction} across nodes in the graph within the generation bounds.
 */
public final class NodeActionPass implements GraphPass {

    private final NodeAction action;
    private final NodeSelector selector;

    public NodeActionPass(NodeAction action) {
        this(action, AllNodeSelector.INSTANCE);
    }

    public NodeActionPass(NodeAction action, NodeSelector selector) {
        this.action = Objects.requireNonNull(action, "action");
        this.selector = selector != null ? selector : AllNodeSelector.INSTANCE;
    }

    @Override
    public void execute(ProceduralGraph graph, long seed, GraphBounds bounds) {
        List<GraphNode> nodesInBounds = graph.getNodesInBounds(bounds);
        for (GraphNode node : nodesInBounds) {
            // Check that node still exists in graph (it may have been deleted by earlier action)
            if (graph.hasNode(node.getId()) && selector.select(node, graph)) {
                action.apply(node, graph, seed);
            }
        }
    }

    public NodeAction getAction() {
        return action;
    }

    public NodeSelector getSelector() {
        return selector;
    }
}
