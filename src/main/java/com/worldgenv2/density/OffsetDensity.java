package com.worldgenv2.density;

import java.util.Objects;

/**
 * Translates evaluation coordinates before sampling the child density function:
 * f(x, y, z) = g(x + offsetX, y + offsetY, z + offsetZ).
 */
public final class OffsetDensity implements DensityFunction {

    private final DensityFunction input;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final boolean integerYOffset;
    private final int intOffsetY;

    public OffsetDensity(DensityFunction input, float offsetX, float offsetY, float offsetZ) {
        this.input = Objects.requireNonNull(input, "input");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.intOffsetY = Math.round(offsetY);
        this.integerYOffset = Math.abs(offsetY - this.intOffsetY) < 1e-6f;
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
        return input.compute(x + offsetX, y + offsetY, z + offsetZ);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (input.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (input.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        if (integerYOffset) {
            input.computeColumn(x + offsetX, z + offsetZ, minY + intOffsetY, maxY + intOffsetY, out);
        } else {
            int height = maxY - minY;
            float sx = x + offsetX;
            float sz = z + offsetZ;
            for (int i = 0; i < height; i++) {
                out[i] = input.compute(sx, (minY + i) + offsetY, sz);
            }
        }
    }
}
