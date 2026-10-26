package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

/**
 * 3D axis-aligned bounding box defining an area of interest for graph generation and spatial queries.
 */
public record GraphBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public GraphBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            double rMinX = Math.min(minX, maxX);
            double rMaxX = Math.max(minX, maxX);
            double rMinY = Math.min(minY, maxY);
            double rMaxY = Math.max(minY, maxY);
            double rMinZ = Math.min(minZ, maxZ);
            double rMaxZ = Math.max(minZ, maxZ);
            minX = rMinX; maxX = rMaxX;
            minY = rMinY; maxY = rMaxY;
            minZ = rMinZ; maxZ = rMaxZ;
        }
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Vec3d pos) {
        return contains(pos.x(), pos.y(), pos.z());
    }

    public boolean intersects(GraphBounds other) {
        return this.minX <= other.maxX && this.maxX >= other.minX
            && this.minY <= other.maxY && this.maxY >= other.minY
            && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public GraphBounds expand(double amount) {
        return new GraphBounds(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    public GraphBounds expand(double dx, double dy, double dz) {
        return new GraphBounds(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public double sizeX() {
        return maxX - minX;
    }

    public double sizeY() {
        return maxY - minY;
    }

    public double sizeZ() {
        return maxZ - minZ;
    }

    public Vec3d center() {
        return new Vec3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }
}
