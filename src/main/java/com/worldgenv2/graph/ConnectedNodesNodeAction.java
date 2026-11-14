package com.worldgenv2.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Runs a child {@link NodeAction} on a fraction (ratio) of the node's connected neighbors.
 */
public final class ConnectedNodesNodeAction implements NodeAction {

    private final NodeAction child;
    private final double ratio;
    private final long actionSeed;

    public ConnectedNodesNodeAction(NodeAction child, double ratio, long actionSeed) {
        this.child = Objects.requireNonNull(child, "child");
        this.ratio = Math.max(0.0, ratio);
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        List<GraphNode> neighbors = new ArrayList<>(graph.getConnectedNodes(node.getId()));
        if (neighbors.isEmpty()) {
            return;
        }

        int targetCount = (int) Math.floor(neighbors.size() * Math.min(1.0, ratio));
        if (targetCount <= 0) {
            return;
        }

        if (targetCount < neighbors.size()) {
            Random rng = new Random(seed ^ actionSeed ^ (node.stableKey() * 31L));
            Collections.shuffle(neighbors, rng);
        }

        for (int i = 0; i < targetCount; i++) {
            child.apply(neighbors.get(i), graph, seed);
        }
    }

    public NodeAction getChild() {
        return child;
    }

    public double getRatio() {
        return ratio;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
