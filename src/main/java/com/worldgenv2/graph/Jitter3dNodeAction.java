package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

/**
 * Perturbs the node in 3D space by a random vector of length up to magnitude.
 */
public final class Jitter3dNodeAction implements NodeAction {

    private final double magnitude;
    private final long actionSeed;

    public Jitter3dNodeAction(double magnitude, long actionSeed) {
        this.magnitude = Math.max(0.0, magnitude);
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        if (magnitude <= 0.0) return;

        long h = seed ^ actionSeed ^ (node.getId() * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        double u = ((h & 0xFFFFL) / (double) 0xFFFFL) * 2.0 - 1.0;
        double theta = (((h >>> 16) & 0xFFFFL) / (double) 0xFFFFL) * (2.0 * Math.PI);
        double dist = (((h >>> 32) & 0xFFFFL) / (double) 0xFFFFL) * magnitude;

        double r = Math.sqrt(Math.max(0.0, 1.0 - u * u));
        double dx = r * Math.cos(theta) * dist;
        double dy = u * dist;
        double dz = r * Math.sin(theta) * dist;

        node.setPosition(node.getPosition().add(dx, dy, dz));
    }

    public double getMagnitude() {
        return magnitude;
    }

    public long getActionSeed() {
        return actionSeed;
    }
}
