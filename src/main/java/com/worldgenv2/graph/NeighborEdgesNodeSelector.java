package com.worldgenv2.graph;

import java.util.List;
import java.util.Objects;

/**
 * Selects a node when at least a threshold number of its connected incident edges are selected by an {@link EdgeSelector}.
 * Threshold of -1 requires all incident edges to be selected (and at least 1 edge must exist).
 */
public final class NeighborEdgesNodeSelector implements NodeSelector {

    private final EdgeSelector child;
    private final int threshold;

    public NeighborEdgesNodeSelector(EdgeSelector child, int threshold) {
        this.child = Objects.requireNonNull(child, "child");
        this.threshold = threshold;
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        List<GraphEdge> incident = graph.getConnectedEdges(node.getId());
        if (incident.isEmpty()) {
            return false;
        }

        int count = 0;
        for (GraphEdge edge : incident) {
            if (child.select(edge, graph)) {
                count++;
            }
        }

        if (threshold == -1) {
            return count == incident.size();
        }
        return count >= threshold;
    }

    public EdgeSelector getChild() {
        return child;
    }

    public int getThreshold() {
        return threshold;
    }
}
