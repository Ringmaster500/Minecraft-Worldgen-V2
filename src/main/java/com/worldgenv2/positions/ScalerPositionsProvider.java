package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Scales the spatial distribution of child positions by a 3D vector.
 */
public final class ScalerPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final Vec3d scale;

    public ScalerPositionsProvider(PositionsProvider source, Vec3d scale) {
        this.source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scale, "scale");
        double sx = Math.abs(scale.x()) > 1e-9 ? scale.x() : 1.0;
        double sy = Math.abs(scale.y()) > 1e-9 ? scale.y() : 1.0;
        double sz = Math.abs(scale.z()) > 1e-9 ? scale.z() : 1.0;
        this.scale = new Vec3d(sx, sy, sz);
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double invX = 1.0 / scale.x();
        double invY = 1.0 / scale.y();
        double invZ = 1.0 / scale.z();

        double qMinX = Math.min(minX * invX, maxX * invX);
        double qMaxX = Math.max(minX * invX, maxX * invX);
        double qMinY = Math.min(minY * invY, maxY * invY);
        double qMaxY = Math.max(minY * invY, maxY * invY);
        double qMinZ = Math.min(minZ * invZ, maxZ * invZ);
        double qMaxZ = Math.max(minZ * invZ, maxZ * invZ);

        List<Vec3d> raw = source.getPositions(qMinX, qMinY, qMinZ, qMaxX, qMaxY, qMaxZ);
        List<Vec3d> scaled = new ArrayList<>(raw.size());
        for (Vec3d p : raw) {
            scaled.add(new Vec3d(p.x() * scale.x(), p.y() * scale.y(), p.z() * scale.z()));
        }
        return scaled;
    }

    public PositionsProvider getSource() {
        return source;
    }

    public Vec3d getScale() {
        return scale;
    }
}
