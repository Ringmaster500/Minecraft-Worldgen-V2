package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Offsets each position using the vector returned by a {@link VectorProvider} at that position.
 */
public final class VectorOffsetPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final VectorProvider vectorProvider;
    private final double maxOffsetDistance;

    public VectorOffsetPositionsProvider(PositionsProvider source, VectorProvider vectorProvider) {
        this(source, vectorProvider, 32.0);
    }

    public VectorOffsetPositionsProvider(PositionsProvider source, VectorProvider vectorProvider, double maxOffsetDistance) {
        this.source = Objects.requireNonNull(source, "source");
        this.vectorProvider = Objects.requireNonNull(vectorProvider, "vectorProvider");
        this.maxOffsetDistance = Math.max(0.0, maxOffsetDistance);
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> candidates = source.getPositions(
            minX - maxOffsetDistance, minY - maxOffsetDistance, minZ - maxOffsetDistance,
            maxX + maxOffsetDistance, maxY + maxOffsetDistance, maxZ + maxOffsetDistance
        );

        List<Vec3d> result = new ArrayList<>(candidates.size());
        for (Vec3d p : candidates) {
            Vec3d v = vectorProvider.getVector(p.x(), p.y(), p.z());
            Vec3d moved = p.add(v);
            if (moved.x() >= minX && moved.x() <= maxX
                && moved.y() >= minY && moved.y() <= maxY
                && moved.z() >= minZ && moved.z() <= maxZ) {
                result.add(moved);
            }
        }
        return result;
    }

    public PositionsProvider getSource() {
        return source;
    }

    public VectorProvider getVectorProvider() {
        return vectorProvider;
    }
}
