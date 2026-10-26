package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Returns fixed static coordinate points that fall within the query bounding box.
 */
public final class ListPositionsProvider implements PositionsProvider {

    private final List<Vec3d> positions;

    public ListPositionsProvider(List<Vec3d> positions) {
        Objects.requireNonNull(positions, "positions");
        this.positions = List.copyOf(positions);
    }

    public ListPositionsProvider(Vec3d... positions) {
        this(List.of(positions));
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> result = new ArrayList<>();
        for (Vec3d p : positions) {
            if (p.x() >= minX && p.x() <= maxX
                && p.y() >= minY && p.y() <= maxY
                && p.z() >= minZ && p.z() <= maxZ) {
                result.add(p);
            }
        }
        return result;
    }

    public List<Vec3d> getPositionsList() {
        return positions;
    }
}
