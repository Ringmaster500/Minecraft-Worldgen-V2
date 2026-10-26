package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;

import java.util.List;
import java.util.Objects;

/**
 * Emits a single fixed anchor point position.
 */
public final class AnchorPositionsProvider implements PositionsProvider {

    private final Vec3d anchor;

    public AnchorPositionsProvider(Vec3d anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public AnchorPositionsProvider(double x, double y, double z) {
        this(new Vec3d(x, y, z));
    }

    public Vec3d anchor() {
        return anchor;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (anchor.x() >= minX && anchor.x() <= maxX
            && anchor.y() >= minY && anchor.y() <= maxY
            && anchor.z() >= minZ && anchor.z() <= maxZ) {
            return List.of(anchor);
        }
        return List.of();
    }
}
