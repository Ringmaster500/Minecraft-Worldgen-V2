package com.worldgenv2.density;

import java.util.Arrays;

/**
 * 2D horizontal noise density node.
 * Evaluates FastNoiseLite on the (X, Z) plane and projects vertically across the entire column.
 * Column evaluation computes the 2D noise only once per (X, Z) coordinate.
 */
public final class Noise2dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;

    public Noise2dDensity(FastNoiseLite noise, float amplitude) {
        this.noise = noise;
        this.amplitude = amplitude;
    }

    public Noise2dDensity(int seed, FastNoiseLite.NoiseType noiseType, float frequency, int octaves, float amplitude) {
        this.noise = new FastNoiseLite(seed);
        this.noise.setNoiseType(noiseType);
        this.noise.setFrequency(frequency);
        if (octaves > 1) {
            this.noise.setFractalType(FastNoiseLite.FractalType.FBm);
            this.noise.setFractalOctaves(octaves);
        } else {
            this.noise.setFractalType(FastNoiseLite.FractalType.None);
        }
        this.amplitude = amplitude;
    }

    /**
     * Recent columns evaluated on this thread: a column of voxels asks for the same (x, z) hundreds of times, and gradient sampling
     * keeps coming back to the same few neighbours. The value never changes, so a small direct-mapped cache is enough.
     */
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
        Arrays.fill(out, 0, height, val);
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
    @Override public boolean yIndependent() { return true; }
}
