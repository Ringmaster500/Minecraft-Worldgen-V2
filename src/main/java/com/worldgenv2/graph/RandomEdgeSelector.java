package com.worldgenv2.graph;

/**
 * Probabilistically selects edges independently with a configured probability chance in [0, 1].
 */
public final class RandomEdgeSelector implements EdgeSelector {

    private final double chance;
    private final long seed;

    public RandomEdgeSelector(double chance, long seed) {
        this.chance = Math.clamp(chance, 0.0, 1.0);
        this.seed = seed;
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        if (chance <= 0.0) return false;
        if (chance >= 1.0) return true;

        long h = seed ^ (edge.key() * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        double roll = (h & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;
        return roll < chance;
    }

    public double getChance() {
        return chance;
    }

    public long getSeed() {
        return seed;
    }
}
