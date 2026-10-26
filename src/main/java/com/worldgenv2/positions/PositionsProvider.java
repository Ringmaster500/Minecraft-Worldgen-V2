package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.List;

/**
 * Strategy interface generating deterministic 3D coordinate point sets within a spatial bounding box.
 * Used for placing trees, flora, structures, boulders, and cellular Voronoi seeds.
 */
@FunctionalInterface
public interface PositionsProvider {

    /**
     * Generates all points provided by this field that fall within the query bounding box:
     * [minX, maxX] x [minY, maxY] x [minZ, maxZ].
     *
     * @param minX minimum world X
     * @param minY minimum world Y
     * @param minZ minimum world Z
     * @param maxX maximum world X
     * @param maxY maximum world Y
     * @param maxZ maximum world Z
     * @return list of 3D point positions
     */
    List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    default PositionsProvider union(PositionsProvider other) {
        return new UnionPositionsProvider(this, other);
    }

    default PositionsProvider offset(double dx, double dy, double dz) {
        return new OffsetPositionsProvider(this, new Vec3d(dx, dy, dz));
    }
}
