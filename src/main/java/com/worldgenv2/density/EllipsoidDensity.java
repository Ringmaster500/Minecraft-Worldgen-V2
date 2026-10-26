package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;

/**
 * Volumetric Ellipsoid density node.
 * Evaluates the continuous normalized distance to the center (0, 0, 0)
 * scaled by radii (rx, ry, rz). When a {@link Curve} is provided, maps the
 * distance to a continuous density value; otherwise outputs positive solid density inside
 * and negative air outside: 1.0 - distance.
 */
public final class EllipsoidDensity implements DensityFunction {

    private final float radiusX;
    private final float radiusY;
    private final float radiusZ;
    private final Curve curve;

    public EllipsoidDensity(float radiusX, float radiusY, float radiusZ) {
        this(radiusX, radiusY, radiusZ, null);
    }

    public EllipsoidDensity(float radiusX, float radiusY, float radiusZ, Curve curve) {
        this.radiusX = radiusX > 0.0f ? radiusX : 1.0f;
        this.radiusY = radiusY > 0.0f ? radiusY : 1.0f;
        this.radiusZ = radiusZ > 0.0f ? radiusZ : 1.0f;
        this.curve = curve;
    }

    @Override
    public float compute(float x, float y, float z) {
        float nx = x / radiusX;
        float ny = y / radiusY;
        float nz = z / radiusZ;
        double dist = Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (curve != null) {
            return (float) curve.evaluate(dist);
        }
        return (float) (1.0 - dist);
    }

    public float getRadiusX() {
        return radiusX;
    }

    public float getRadiusY() {
        return radiusY;
    }

    public float getRadiusZ() {
        return radiusZ;
    }

    public Curve getCurve() {
        return curve;
    }
}
