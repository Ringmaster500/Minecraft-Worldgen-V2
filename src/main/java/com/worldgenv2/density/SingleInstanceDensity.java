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

    @Override public boolean yIndependent() { return delegate.yIndependent(); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        delegate.computeColumn(x, z, minY, maxY, out);
    }

    public DensityFunction getDelegate() {
        return delegate;
    }
}
