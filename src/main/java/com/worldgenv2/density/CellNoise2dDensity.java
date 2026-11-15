package com.worldgenv2.density;

import java.util.Objects;

/**
 * 2D Voronoi Cellular noise density function varying on the XZ plane.
 * Column-constant: values do not vary with Y, enabling single-sample column optimizations.
 */
public final class CellNoise2dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;

    public CellNoise2dDensity(FastNoiseLite noise) {
        this(noise, 1.0f);
    }

    public CellNoise2dDensity(FastNoiseLite noise, float amplitude) {
        this.noise = Objects.requireNonNull(noise, "noise");
        this.amplitude = amplitude;
    }

    /** Which exact noise this is (settings and amplitude), for the shared per-thread memo; worked out on first use. */
    private volatile int memoId;

    private int memoId() {
        int id = memoId;
        if (id == 0) memoId = id = NoiseMemo.idFor(java.util.List.of("2d", noise.signature(), amplitude));
        return id;
    }

    private float sample(float x, float z) { return noise.getNoise(x, z) * amplitude; }

    @Override
    public float compute(float x, float y, float z) {
        return NoiseMemo.get(memoId(), x, z, this::sample);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;
        java.util.Arrays.fill(out, 0, height, NoiseMemo.get(memoId(), x, z, this::sample));
    }
    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
    @Override public boolean yIndependent() { return true; }
}
