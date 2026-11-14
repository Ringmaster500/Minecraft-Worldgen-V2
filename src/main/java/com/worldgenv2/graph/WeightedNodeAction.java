package com.worldgenv2.graph;

import java.util.List;
import java.util.Objects;

/**
 * Probabilistically selects and executes a child {@link NodeAction} from a weighted distribution.
 */
public final class WeightedNodeAction implements NodeAction {

    public record Entry(double weight, NodeAction action) {
        public Entry {
            Objects.requireNonNull(action, "action");
            if (weight < 0.0) throw new IllegalArgumentException("Weight cannot be negative: " + weight);
        }
    }

    private final List<Entry> entries;
    private final double totalWeight;
    private final long actionSeed;

    public WeightedNodeAction(List<Entry> entries, long actionSeed) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("WeightedNodeAction requires at least one entry");
        }
        this.entries = List.copyOf(entries);
        double sum = 0.0;
        for (Entry e : this.entries) {
            sum += e.weight();
        }
        this.totalWeight = sum;
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        if (totalWeight <= 0.0) return;

        long h = seed ^ actionSeed ^ (node.stableKey() * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        double roll = ((h & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE) * totalWeight;
        double accum = 0.0;
        for (Entry e : entries) {
            accum += e.weight();
            if (roll <= accum) {
                e.action().apply(node, graph, seed);
                return;
            }
        }
        entries.getLast().action().apply(node, graph, seed);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
