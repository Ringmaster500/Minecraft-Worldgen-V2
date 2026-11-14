package com.worldgenv2.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Runs a child {@link EdgeAction} on a fraction (ratio) of the node's connected incident edges.
 */
public final class ConnectedEdgesNodeAction implements NodeAction {

    private final EdgeAction child;
    private final double ratio;
    private final long actionSeed;

    public ConnectedEdgesNodeAction(EdgeAction child, double ratio, long actionSeed) {
        this.child = Objects.requireNonNull(child, "child");
        this.ratio = Math.max(0.0, ratio);
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        List<GraphEdge> edges = new ArrayList<>(graph.getConnectedEdges(node.getId()));
        if (edges.isEmpty()) {
            return;
        }

        int targetCount = (int) Math.floor(edges.size() * Math.min(1.0, ratio));
        if (targetCount <= 0) {
            return;
        }

        if (targetCount < edges.size()) {
            Random rng = new Random(seed ^ actionSeed ^ (node.stableKey() * 37L));
            Collections.shuffle(edges, rng);
        }

        for (int i = 0; i < targetCount; i++) {
            child.apply(edges.get(i), graph, seed);
        }
    }

    public EdgeAction getChild() {
        return child;
    }

    public double getRatio() {
        return ratio;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
