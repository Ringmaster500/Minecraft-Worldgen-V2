package com.worldgenv2.density;

import java.util.Objects;

/** {@code sign(v) * |v|^e} (Hytale's Pow), with the common whole-number exponents done by multiplication. */
public final class SignedPowDensity implements DensityFunction {
    private final DensityFunction in;
    private final float e;
    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();

    public SignedPowDensity(DensityFunction in, float e) {
        this.in = Objects.requireNonNull(in, "in");
        this.e = e;
    }

    private float apply(float v) {
        if (e == 1f) return v;
        if (e == 2f) return v * Math.abs(v);
        if (e == 3f) return v * v * v;
        if (e == 0.5f) return v < 0 ? -(float) Math.sqrt(-v) : (float) Math.sqrt(v);
        return (float) (Math.signum(v) * Math.pow(Math.abs(v), e));
    }

    @Override public float compute(float x, float y, float z) { return apply(in.compute(x, y, z)); }

    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (in.yIndependent() ? 1 : 2); return f == 1; }

    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (in.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        in.boundsSlabs(x, z, minY, maxY, bs, lo, hi);
        for (int k = 0; k < nb; k++) {
            float l = apply(lo[k]), h = apply(hi[k]);
            if (Float.isNaN(l) || Float.isNaN(h)) { lo[k] = Float.NEGATIVE_INFINITY; hi[k] = Float.POSITIVE_INFINITY; }
            else { lo[k] = l; hi[k] = h; }
        }    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        in.bounds(x, z, y0, y1, out);
        Bounds.set(out, apply(out[0]), apply(out[1]));    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        in.computeColumn(x, z, minY, maxY, out);
        int n = maxY - minY;
        if (e == 3f) { for (int i = 0; i < n; i++) { float v = out[i]; out[i] = v * v * v; } }
        else if (e == 2f) { for (int i = 0; i < n; i++) { float v = out[i]; out[i] = v * Math.abs(v); } }
        else { for (int i = 0; i < n; i++) out[i] = apply(out[i]); }
    }
}