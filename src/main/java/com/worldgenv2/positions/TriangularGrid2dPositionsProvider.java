package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an infinite 2D triangular (hexagonal) mesh of positions on the XZ plane with Y = 0.
 * The distance between adjacent points is equal to spacing (default 1.0 block).
 */
public final class TriangularGrid2dPositionsProvider implements PositionsProvider {

    private static final double SQRT_3_OVER_2 = Math.sqrt(3.0) * 0.5;

    private final double spacing;

    public TriangularGrid2dPositionsProvider() {
        this(1.0);
    }

    public TriangularGrid2dPositionsProvider(double spacing) {
        this.spacing = spacing > 0.0 ? spacing : 1.0;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> result = new ArrayList<>();
        if (minY > 0.0 || maxY < 0.0) {
            return result;
        }

        double rowHeight = spacing * SQRT_3_OVER_2;
        long startRow = (long) Math.floor(minZ / rowHeight);
        long endRow = (long) Math.ceil(maxZ / rowHeight);

        long startCol = (long) Math.floor(minX / spacing) - 1;
        long endCol = (long) Math.ceil(maxX / spacing) + 1;

        for (long r = startRow; r <= endRow; r++) {
            double pz = r * rowHeight;
            if (pz < minZ || pz > maxZ) continue;
            double xOffset = (Math.floorMod(r, 2) != 0) ? (0.5 * spacing) : 0.0;

            for (long c = startCol; c <= endCol; c++) {
                double px = c * spacing + xOffset;
                if (px >= minX && px <= maxX) {
                    result.add(new Vec3d(px, 0.0, pz));
                }
            }
        }
        return result;
    }

    public double getSpacing() {
        return spacing;
    }
}
