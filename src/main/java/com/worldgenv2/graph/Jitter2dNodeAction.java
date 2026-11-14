package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

/**
 * Perturbs the node in the horizontal (X-Z) plane by a random vector of length up to magnitude.
 */
public final class Jitter2dNodeAction implements NodeAction {

    private final double magnitude;
    private final long actionSeed;

    public Jitter2dNodeAction(double magnitude, long actionSeed) {
        this.magnitude = Math.max(0.0, magnitude);
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        if (magnitude <= 0.0) return;

        long h = seed ^ actionSeed ^ (node.stableKey() * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        double angle = ((h & 0xFFFFL) / (double) 0xFFFFL) * (2.0 * Math.PI);
        double dist = (((h >>> 16) & 0xFFFFL) / (double) 0xFFFFL) * magnitude;

        double dx = Math.cos(angle) * dist;
        double dz = Math.sin(angle) * dist;

        node.setPosition(node.getPosition().add(dx, 0.0, dz));
    }

    public double getMagnitude() {
        return magnitude;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
