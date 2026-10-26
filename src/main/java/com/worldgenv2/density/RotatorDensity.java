package com.worldgenv2.density;

import java.util.Objects;

/**
 * Rotates the 3D coordinate space around an axis before evaluating child density.
 */
public final class RotatorDensity implements DensityFunction {

    public enum Axis {
        X, Y, Z
    }

    private final DensityFunction input;
    private final Axis axis;
    private final float angleDegrees;
    private final float sinAngle;
    private final float cosAngle;

    public RotatorDensity(DensityFunction input, Axis axis, float angleDegrees) {
        this.input = Objects.requireNonNull(input, "input");
        this.axis = axis != null ? axis : Axis.Y;
        this.angleDegrees = angleDegrees;
        double rad = Math.toRadians(angleDegrees);
        this.sinAngle = (float) Math.sin(rad);
        this.cosAngle = (float) Math.cos(rad);
    }

    public DensityFunction input() {
        return input;
    }

    public Axis axis() {
        return axis;
    }

    public float angleDegrees() {
        return angleDegrees;
    }

    @Override
    public float compute(float x, float y, float z) {
        float rx = x;
        float ry = y;
        float rz = z;

        switch (axis) {
            case X -> {
                ry = y * cosAngle - z * sinAngle;
                rz = y * sinAngle + z * cosAngle;
            }
            case Y -> {
                rx = x * cosAngle + z * sinAngle;
                rz = -x * sinAngle + z * cosAngle;
            }
            case Z -> {
                rx = x * cosAngle - y * sinAngle;
                ry = x * sinAngle + y * cosAngle;
            }
        }
        return input.compute(rx, ry, rz);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = compute(x, minY + i, z);
        }
    }
}
