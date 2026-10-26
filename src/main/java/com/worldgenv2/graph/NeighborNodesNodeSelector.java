package com.worldgenv2.graph;

import java.util.List;
import java.util.Objects;

/**
 * Selects a node when at least a threshold number of its connected neighbor nodes are selected by a child selector.
 * Threshold of -1 requires all neighbors to be selected (and at least 1 neighbor must exist).
 */
public final class NeighborNodesNodeSelector implements NodeSelector {

    private final NodeSelector child;
    private final int threshold;

    public NeighborNodesNodeSelector(NodeSelector child, int threshold) {
        this.child = Objects.requireNonNull(child, "child");
        this.threshold = threshold;
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        List<GraphNode> neighbors = graph.getConnectedNodes(node.getId());
        if (neighbors.isEmpty()) {
            return false;
        }

        int count = 0;
        for (GraphNode neighbor : neighbors) {
            if (child.select(neighbor, graph)) {
                count++;
            }
        }

        if (threshold == -1) {
            return count == neighbors.size();
        }
        return count >= threshold;
    }

    public NodeSelector getChild() {
        return child;
    }

    public int getThreshold() {
        return threshold;
    }
}
