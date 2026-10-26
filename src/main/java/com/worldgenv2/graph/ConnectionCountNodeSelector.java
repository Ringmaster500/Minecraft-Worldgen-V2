package com.worldgenv2.graph;

import java.util.Collection;
import java.util.Set;

/**
 * Selects nodes whose connection count (degree) is present in the specified counts set.
 */
public final class ConnectionCountNodeSelector implements NodeSelector {

    private final Set<Integer> counts;

    public ConnectionCountNodeSelector(Collection<Integer> counts) {
        this.counts = counts != null ? Set.copyOf(counts) : Set.of();
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        return counts.contains(node.getConnectionCount());
    }

    public Set<Integer> getCounts() {
        return counts;
    }
}
