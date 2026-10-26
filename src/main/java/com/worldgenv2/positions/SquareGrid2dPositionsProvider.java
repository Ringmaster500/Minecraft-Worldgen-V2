package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an infinite 2D square lattice of positions on the XZ plane with Y = 0.
 */
public final class SquareGrid2dPositionsProvider implements PositionsProvider {

    private final double spacing;

    public SquareGrid2dPositionsProvider() {
        this(1.0);
    }

    public SquareGrid2dPositionsProvider(double spacing) {
        this.spacing = spacing > 0.0 ? spacing : 1.0;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> result = new ArrayList<>();
        // If Y=0 is not in query range, return empty
        if (minY > 0.0 || maxY < 0.0) {
            return result;
        }

        long startX = (long) Math.floor(minX / spacing);
        long endX = (long) Math.ceil(maxX / spacing);
        long startZ = (long) Math.floor(minZ / spacing);
        long endZ = (long) Math.ceil(maxZ / spacing);

        for (long x = startX; x <= endX; x++) {
            double posX = x * spacing;
            if (posX < minX || posX > maxX) continue;
            for (long z = startZ; z <= endZ; z++) {
                double posZ = z * spacing;
                if (posZ >= minZ && posZ <= maxZ) {
                    result.add(new Vec3d(posX, 0.0, posZ));
                }
            }
        }
        return result;
    }

    public double getSpacing() {
        return spacing;
    }
}
