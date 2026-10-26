package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Connects the current node to neighboring nodes within a maximum range that satisfy a {@link NodeSelector}.
 * An optional cap limits the number of connections established.
 */
public final class ProximityConnectorNodeAction implements NodeAction {

    private final double range;
    private final NodeSelector neighborSelector;
    private final int cap;
    private final long actionSeed;

    public ProximityConnectorNodeAction(double range, NodeSelector neighborSelector, int cap, long actionSeed) {
        this.range = Math.max(0.0, range);
        this.neighborSelector = neighborSelector != null ? neighborSelector : AllNodeSelector.INSTANCE;
        this.cap = cap;
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        if (range <= 0.0) return;

        Vec3d pos = node.getPosition();
        List<GraphNode> candidates = new ArrayList<>();
        for (GraphNode other : graph.getNodesInRadius(pos, range)) {
            if (other.getId() != node.getId() && !node.isConnectedTo(other.getId())) {
                if (neighborSelector.select(other, graph)) {
                    candidates.add(other);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        // Sort candidates by distance from node
        candidates.sort(Comparator.comparingDouble(o -> o.getPosition().distanceSquared(pos)));

        int limit = (cap >= 0) ? Math.min(cap, candidates.size()) : candidates.size();
        for (int i = 0; i < limit; i++) {
            graph.addEdge(node.getId(), candidates.get(i).getId());
        }
    }

    public double getRange() {
        return range;
    }

    public NodeSelector getNeighborSelector() {
        return neighborSelector;
    }

    public int getCap() {
        return cap;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
