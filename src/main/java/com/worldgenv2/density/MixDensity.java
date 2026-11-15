package com.worldgenv2.density;

import java.util.Objects;

/**
 * Linearly interpolates between two densities based on a factor field:
 * {@code f(x, y, z) = (1 - factor) * a + factor * b}.
 */
public final class MixDensity implements DensityFunction {

    private final DensityFunction a;
    private final DensityFunction b;
    private final DensityFunction factor;

    public MixDensity(DensityFunction a, DensityFunction b, DensityFunction factor) {
        this.a = Objects.requireNonNull(a, "a");
        this.b = Objects.requireNonNull(b, "b");
        this.factor = Objects.requireNonNull(factor, "factor");
    }

    public DensityFunction a() {
        return a;
    }

    public DensityFunction b() {
        return b;
    }

    public DensityFunction factor() {
        return factor;
    }

    @Override
    public float compute(float x, float y, float z) {
        float t = Math.clamp(factor.compute(x, y, z), 0.0f, 1.0f);
        if (t <= 0.0f) return a.compute(x, y, z);
        if (t >= 1.0f) return b.compute(x, y, z);
        float va = a.compute(x, y, z);
        float vb = b.compute(x, y, z);
        return va + t * (vb - va);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (a.yIndependent() && b.yIndependent() && factor.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (a.xzIndependent() && b.xzIndependent() && factor.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        int height = maxY - minY;
        if (factor.yIndependent()) {
            // the blend is the same for the whole column: often it picks one input outright, and the other is never evaluated
            float t = Math.clamp(factor.compute(x, minY, z), 0.0f, 1.0f);
            if (t <= 0.0f) { a.computeColumn(x, z, minY, maxY, out); return; }
            if (t >= 1.0f) { b.computeColumn(x, z, minY, maxY, out); return; }
            float[] bufB = ColumnPool.take(height);
            a.computeColumn(x, z, minY, maxY, out);
            b.computeColumn(x, z, minY, maxY, bufB);
            for (int i = 0; i < height; i++) out[i] += t * (bufB[i] - out[i]);
            ColumnPool.give(bufB);
            return;
        }
        float[] bufB = ColumnPool.take(height);
        float[] bufF = ColumnPool.take(height);
        a.computeColumn(x, z, minY, maxY, out);
        b.computeColumn(x, z, minY, maxY, bufB);
        factor.computeColumn(x, z, minY, maxY, bufF);
        for (int i = 0; i < height; i++) {
            float t = Math.clamp(bufF[i], 0.0f, 1.0f);
            out[i] += t * (bufB[i] - out[i]);
        }
        ColumnPool.give(bufF);
        ColumnPool.give(bufB);
    }
}
