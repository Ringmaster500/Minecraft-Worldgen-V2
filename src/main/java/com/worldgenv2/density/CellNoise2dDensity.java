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

    /** The last column evaluated on this thread: a column of voxels asks for the same (x, z) hundreds of times, the value never changes. */
    private static final class ColumnMemo {
        final float[] xs = new float[16], zs = new float[16], vs = new float[16];
        ColumnMemo() { java.util.Arrays.fill(xs, Float.NaN); }
    }
    private final ThreadLocal<ColumnMemo> memo = ThreadLocal.withInitial(ColumnMemo::new);
    @Override
    public float compute(float x, float y, float z) {
        ColumnMemo m = memo.get();
        int slot = (Float.floatToRawIntBits(x) * 31 + Float.floatToRawIntBits(z)) * 0x9E3779B1 >>> 28;
        if (m.xs[slot] == x && m.zs[slot] == z) return m.vs[slot];
        float v = noise.getNoise(x, z) * amplitude;
        m.xs[slot] = x; m.zs[slot] = z; m.vs[slot] = v;
        return v;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;
        float val = noise.getNoise(x, z) * amplitude;
        for (int i = 0; i < height; i++) {
            out[i] = val;
        }
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
    @Override public boolean yIndependent() { return true; }
}
