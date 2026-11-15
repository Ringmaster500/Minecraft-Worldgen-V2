package com.worldgenv2.density;

import java.util.Objects;

/**
 * Single-instance memoization wrapper for exported density sub-graphs.
 * Ensures all import references share the same underlying instance and cache state.
 */
public final class SingleInstanceDensity implements DensityFunction {

    private final DensityFunction delegate;

    public SingleInstanceDensity(DensityFunction delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public float compute(float x, float y, float z) {
        return delegate.compute(x, y, z);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (delegate.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (delegate.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        delegate.boundsSlabs(x, z, minY, maxY, bs, lo, hi);    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        delegate.bounds(x, z, y0, y1, out);    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        delegate.computeColumn(x, z, minY, maxY, out);
    }

    public DensityFunction getDelegate() {
        return delegate;
    }
}
