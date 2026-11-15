package com.worldgenv2.density;

import java.util.Objects;

/** {@code k * input + b}: Inverter, Normalizer, OffsetConstant and AmplitudeConstant, with a tight loop for whole columns. */
public final class LinearDensity implements DensityFunction {
    private final DensityFunction in;
    private final float k, b;
    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();

    public LinearDensity(DensityFunction in, float k, float b) {
        this.in = Objects.requireNonNull(in, "in");
        this.k = k;
        this.b = b;
    }

    @Override public float compute(float x, float y, float z) { return in.compute(x, y, z) * k + b; }

    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (in.yIndependent() ? 1 : 2); return f == 1; }

    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (in.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        in.computeColumn(x, z, minY, maxY, out);
        final float k = this.k, b = this.b;
        for (int i = 0, n = maxY - minY; i < n; i++) out[i] = out[i] * k + b;
    }
}