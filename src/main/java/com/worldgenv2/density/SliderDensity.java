package com.worldgenv2.density;

import java.util.Objects;

/**
 * Displaces coordinate evaluation space along a 3D translation vector:
 * {@code f(x, y, z) = input(x - dx, y - dy, z - dz)}.
 */
public final class SliderDensity implements DensityFunction {

    private final DensityFunction input;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;

    public SliderDensity(DensityFunction input, float offsetX, float offsetY, float offsetZ) {
        this.input = Objects.requireNonNull(input, "input");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public DensityFunction input() {
        return input;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float offsetZ() {
        return offsetZ;
    }

    @Override
    public float compute(float x, float y, float z) {
        return input.compute(x - offsetX, y - offsetY, z - offsetZ);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        input.computeColumn(x - offsetX, z - offsetZ, (int) (minY - offsetY), (int) (maxY - offsetY), out);
    }
}
