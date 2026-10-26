package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates horizontal point intervals spaced along the X or Z axis at a fixed Y plane.
 */
public final class SimpleHorizontalPositionsProvider implements PositionsProvider {

    private final double spacing;
    private final double yLevel;
    private final boolean alongX;

    public SimpleHorizontalPositionsProvider(double spacing, double yLevel, boolean alongX) {
        this.spacing = Math.max(0.5, spacing);
        this.yLevel = yLevel;
        this.alongX = alongX;
    }

    public double spacing() {
        return spacing;
    }

    public double yLevel() {
        return yLevel;
    }

    public boolean alongX() {
        return alongX;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (yLevel < minY || yLevel > maxY) {
            return List.of();
        }

        List<Vec3d> points = new ArrayList<>();
        if (alongX) {
            double startX = Math.floor(minX / spacing) * spacing;
            double midZ = (minZ + maxZ) * 0.5;
            for (double x = startX; x <= maxX; x += spacing) {
                if (x >= minX) {
                    points.add(new Vec3d(x, yLevel, midZ));
                }
            }
        } else {
            double startZ = Math.floor(minZ / spacing) * spacing;
            double midX = (minX + maxX) * 0.5;
            for (double z = startZ; z <= maxZ; z += spacing) {
                if (z >= minZ) {
                    points.add(new Vec3d(midX, yLevel, z));
                }
            }
        }
        return points;
    }
}
