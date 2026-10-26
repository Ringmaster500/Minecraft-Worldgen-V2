package com.worldgenv2.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes an {@link EdgeAction} across edges in the graph within the generation bounds.
 */
public final class EdgeActionPass implements GraphPass {

    private final EdgeAction action;
    private final EdgeSelector selector;

    public EdgeActionPass(EdgeAction action) {
        this(action, AllEdgeSelector.INSTANCE);
    }

    public EdgeActionPass(EdgeAction action, EdgeSelector selector) {
        this.action = Objects.requireNonNull(action, "action");
        this.selector = selector != null ? selector : AllEdgeSelector.INSTANCE;
    }

    @Override
    public void execute(ProceduralGraph graph, long seed, GraphBounds bounds) {
        List<GraphEdge> snapshot = new ArrayList<>(graph.getEdges());
        for (GraphEdge edge : snapshot) {
            // Check that edge still exists
            if (graph.hasEdge(edge.nodeA(), edge.nodeB())) {
                GraphNode a = graph.getNode(edge.nodeA());
                GraphNode b = graph.getNode(edge.nodeB());
                if (a != null && b != null) {
                    if (bounds.contains(a.getPosition()) || bounds.contains(b.getPosition()) || bounds.contains(edge.midpoint(graph))) {
                        if (selector.select(edge, graph)) {
                            action.apply(edge, graph, seed);
                        }
                    }
                }
            }
        }
    }

    public EdgeAction getAction() {
        return action;
    }

    public EdgeSelector getSelector() {
        return selector;
    }
}
