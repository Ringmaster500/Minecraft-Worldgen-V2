package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Offsets all generated positions by a static 3D vector.
 */
public final class OffsetPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final Vec3d offset;

    public OffsetPositionsProvider(PositionsProvider source, Vec3d offset) {
        this.source = Objects.requireNonNull(source, "source");
        this.offset = Objects.requireNonNull(offset, "offset");
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> raw = source.getPositions(
            minX - offset.x(), minY - offset.y(), minZ - offset.z(),
            maxX - offset.x(), maxY - offset.y(), maxZ - offset.z()
        );
        List<Vec3d> result = new ArrayList<>(raw.size());
        for (Vec3d p : raw) {
            result.add(p.add(offset));
        }
        return result;
    }

    public PositionsProvider getSource() {
        return source;
    }

    public Vec3d getOffset() {
        return offset;
    }
}
