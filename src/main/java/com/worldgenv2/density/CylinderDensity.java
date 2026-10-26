package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;

/**
 * Volumetric Cylinder density node.
 * Evaluates the radial distance from the cylinder's central axis line.
 */
public final class CylinderDensity implements DensityFunction {

    public enum Axis {
        X, Y, Z
    }

    private final float radius;
    private final Axis axis;
    private final Curve curve;

    public CylinderDensity(float radius) {
        this(radius, Axis.Y, null);
    }

    public CylinderDensity(float radius, Axis axis, Curve curve) {
        this.radius = radius > 0.0f ? radius : 1.0f;
        this.axis = axis != null ? axis : Axis.Y;
        this.curve = curve;
    }

    @Override
    public float compute(float x, float y, float z) {
        double distSq = switch (axis) {
            case X -> y * y + z * z;
            case Y -> x * x + z * z;
            case Z -> x * x + y * y;
        };
        double dist = Math.sqrt(distSq) / radius;

        if (curve != null) {
            return (float) curve.evaluate(dist);
        }
        return (float) (1.0 - dist);
    }

    public float getRadius() {
        return radius;
    }

    public Axis getAxis() {
        return axis;
    }

    public Curve getCurve() {
        return curve;
    }
}
