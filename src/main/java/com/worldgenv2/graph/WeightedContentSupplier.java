package com.worldgenv2.graph;

import java.util.List;
import java.util.Objects;

/**
 * Probabilistically selects a child {@link ContentSupplier} from a weighted distribution.
 */
public final class WeightedContentSupplier implements ContentSupplier {

    public record Entry(double weight, ContentSupplier supplier) {
        public Entry {
            Objects.requireNonNull(supplier, "supplier");
            if (weight < 0.0) throw new IllegalArgumentException("Weight cannot be negative: " + weight);
        }
    }

    private final List<Entry> entries;
    private final double totalWeight;

    public WeightedContentSupplier(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("WeightedContentSupplier requires at least one entry");
        }
        this.entries = List.copyOf(entries);
        double sum = 0.0;
        for (Entry e : this.entries) {
            sum += e.weight();
        }
        this.totalWeight = sum;
    }

    @Override
    public GraphContent supply(GraphNode node, long seed) {
        if (totalWeight <= 0.0) {
            return GraphContent.EMPTY;
        }

        // Coordinate + Node ID hash
        long h = seed ^ (node.stableKey() * 0x9E3779B97F4A7C15L);
        h ^= Double.doubleToLongBits(node.getPosition().x()) * 31;
        h ^= Double.doubleToLongBits(node.getPosition().y()) * 17;
        h ^= Double.doubleToLongBits(node.getPosition().z()) * 13;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        double roll = ((h & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE) * totalWeight;
        double accum = 0.0;
        for (Entry e : entries) {
            accum += e.weight();
            if (roll <= accum) {
                return e.supplier().supply(node, seed);
            }
        }
        return entries.getLast().supplier().supply(node, seed);
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
