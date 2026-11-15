package com.worldgenv2.density;

import java.util.Objects;

/**
 * Produces the absolute value of an input density field: f(x, y, z) = |g(x, y, z)|.
 * Used for symmetrical pinching, ridged creases, and fracture planes.
 */
public final class AbsDensity implements DensityFunction {

    private final DensityFunction input;

    public AbsDensity(DensityFunction input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public DensityFunction input() {
        return input;
    }

    @Override
    public float compute(float x, float y, float z) {
        return Math.abs(input.compute(x, y, z));
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (input.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (input.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        input.boundsSlabs(x, z, minY, maxY, bs, lo, hi);
        for (int k = 0; k < nb; k++) {
            float l = lo[k], h = hi[k];
            if (l >= 0f) continue;
            if (h <= 0f) { lo[k] = -h; hi[k] = -l; }
            else { lo[k] = 0f; hi[k] = Math.max(-l, h); }
        }    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        input.bounds(x, z, y0, y1, out);
        float l = out[0], h = out[1];
        if (l >= 0f) Bounds.set(out, l, h);
        else if (h <= 0f) Bounds.set(out, -h, -l);
        else Bounds.set(out, 0f, Math.max(-l, h));    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = Math.abs(out[i]);
        }
    }
}
