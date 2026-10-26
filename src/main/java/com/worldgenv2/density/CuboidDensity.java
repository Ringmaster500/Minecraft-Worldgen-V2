package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;

/**
 * Volumetric Cuboid / Box density node.
 * Evaluates the Chebyshev / AABB distance from the origin scaled by half-extents (hx, hy, hz).
 */
public final class CuboidDensity implements DensityFunction {

    private final float halfX;
    private final float halfY;
    private final float halfZ;
    private final Curve curve;

    public CuboidDensity(float halfX, float halfY, float halfZ) {
        this(halfX, halfY, halfZ, null);
    }

    public CuboidDensity(float halfX, float halfY, float halfZ, Curve curve) {
        this.halfX = halfX > 0.0f ? halfX : 1.0f;
        this.halfY = halfY > 0.0f ? halfY : 1.0f;
        this.halfZ = halfZ > 0.0f ? halfZ : 1.0f;
        this.curve = curve;
    }

    @Override
    public float compute(float x, float y, float z) {
        float dx = Math.abs(x) / halfX;
        float dy = Math.abs(y) / halfY;
        float dz = Math.abs(z) / halfZ;
        double maxDist = Math.max(dx, Math.max(dy, dz));

        if (curve != null) {
            return (float) curve.evaluate(maxDist);
        }
        return (float) (1.0 - maxDist);
    }

    public float getHalfX() {
        return halfX;
    }

    public float getHalfY() {
        return halfY;
    }

    public float getHalfZ() {
        return halfZ;
    }

    public Curve getCurve() {
        return curve;
    }
}
