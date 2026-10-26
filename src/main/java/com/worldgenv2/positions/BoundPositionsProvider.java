package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Restricts child positions to a strict 3D AABB bounding box.
 */
public final class BoundPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final double bMinX, bMinY, bMinZ;
    private final double bMaxX, bMaxY, bMaxZ;

    public BoundPositionsProvider(
        PositionsProvider source,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.bMinX = minX;
        this.bMinY = minY;
        this.bMinZ = minZ;
        this.bMaxX = maxX;
        this.bMaxY = maxY;
        this.bMaxZ = maxZ;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double qMinX = Math.max(minX, bMinX);
        double qMinY = Math.max(minY, bMinY);
        double qMinZ = Math.max(minZ, bMinZ);
        double qMaxX = Math.min(maxX, bMaxX);
        double qMaxY = Math.min(maxY, bMaxY);
        double qMaxZ = Math.min(maxZ, bMaxZ);

        if (qMinX > qMaxX || qMinY > qMaxY || qMinZ > qMaxZ) {
            return List.of();
        }

        List<Vec3d> raw = source.getPositions(qMinX, qMinY, qMinZ, qMaxX, qMaxY, qMaxZ);
        List<Vec3d> result = new ArrayList<>(raw.size());
        for (Vec3d p : raw) {
            if (p.x() >= bMinX && p.x() <= bMaxX
                && p.y() >= bMinY && p.y() <= bMaxY
                && p.z() >= bMinZ && p.z() <= bMaxZ) {
                result.add(p);
            }
        }
        return result;
    }

    public PositionsProvider getSource() {
        return source;
    }
}
