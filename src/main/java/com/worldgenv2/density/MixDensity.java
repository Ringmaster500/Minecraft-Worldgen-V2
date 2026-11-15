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

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        float[] bufA = new float[height];
        float[] bufB = new float[height];
        float[] bufF = new float[height];

        a.computeColumn(x, z, minY, maxY, bufA);
        b.computeColumn(x, z, minY, maxY, bufB);
        factor.computeColumn(x, z, minY, maxY, bufF);

        for (int i = 0; i < height; i++) {
            float t = Math.clamp(bufF[i], 0.0f, 1.0f);
            out[i] = bufA[i] + t * (bufB[i] - bufA[i]);
        }
    }
}
