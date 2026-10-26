package com.worldgenv2.vector;

/**
 * Strategy interface defining a 3D vector field: (x, y, z) -> Vec3d.
 * Used for domain warping, directional jitter, surface normal calculations, and projection.
 */
@FunctionalInterface
public interface VectorProvider {

    /**
     * Resolves the 3D vector at continuous world coordinates (x, y, z).
     *
     * @param x continuous world X
     * @param y continuous world Y
     * @param z continuous world Z
     * @return 3D vector
     */
    Vec3d getVector(double x, double y, double z);

    default VectorProvider add(VectorProvider other) {
        return new AdderVectorProvider(this, other);
    }

    default VectorProvider subtract(VectorProvider other) {
        return new SubtracterVectorProvider(this, other);
    }

    default VectorProvider cross(VectorProvider other) {
        return new CrossVectorProvider(this, other);
    }
}
