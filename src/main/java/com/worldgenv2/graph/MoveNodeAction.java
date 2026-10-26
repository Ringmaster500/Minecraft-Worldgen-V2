package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.Objects;

/**
 * Displaces the node by a vector evaluated from a {@link VectorProvider}.
 * If vector length reaches or exceeds maxDistance, displacement is cancelled.
 */
public final class MoveNodeAction implements NodeAction {

    private final VectorProvider vector;
    private final double maxDistance;

    public MoveNodeAction(VectorProvider vector, double maxDistance) {
        this.vector = Objects.requireNonNull(vector, "vector");
        this.maxDistance = Math.max(0.0, maxDistance);
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        Vec3d pos = node.getPosition();
        Vec3d v = vector.getVector(pos.x(), pos.y(), pos.z());
        double len = v.length();
        if (len < maxDistance && len > 1e-9) {
            node.setPosition(pos.add(v));
        }
    }

    public VectorProvider getVector() {
        return vector;
    }

    public double getMaxDistance() {
        return maxDistance;
    }
}
