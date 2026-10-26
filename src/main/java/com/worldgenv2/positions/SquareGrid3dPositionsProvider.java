package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an infinite 3D cubic lattice of positions across XYZ space.
 */
public final class SquareGrid3dPositionsProvider implements PositionsProvider {

    private final double spacing;

    public SquareGrid3dPositionsProvider() {
        this(1.0);
    }

    public SquareGrid3dPositionsProvider(double spacing) {
        this.spacing = spacing > 0.0 ? spacing : 1.0;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> result = new ArrayList<>();
        long startX = (long) Math.floor(minX / spacing);
        long endX = (long) Math.ceil(maxX / spacing);
        long startY = (long) Math.floor(minY / spacing);
        long endY = (long) Math.ceil(maxY / spacing);
        long startZ = (long) Math.floor(minZ / spacing);
        long endZ = (long) Math.ceil(maxZ / spacing);

        for (long x = startX; x <= endX; x++) {
            double px = x * spacing;
            if (px < minX || px > maxX) continue;
            for (long y = startY; y <= endY; y++) {
                double py = y * spacing;
                if (py < minY || py > maxY) continue;
                for (long z = startZ; z <= endZ; z++) {
                    double pz = z * spacing;
                    if (pz >= minZ && pz <= maxZ) {
                        result.add(new Vec3d(px, py, pz));
                    }
                }
            }
        }
        return result;
    }

    public double getSpacing() {
        return spacing;
    }
}
