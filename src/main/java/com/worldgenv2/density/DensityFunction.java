package com.worldgenv2.density;

/**
 * Fundamental building block of Hytale WorldGen V2.
 * Represents a continuous 3D scalar density field: D(x, y, z) in [-1.0, 1.0].
 * 
 * Convention:
 * - D > 0.0: Solid matter
 * - D < 0.0: Negative space / air
 * - D = 0.0: Exact terrain isosurface
 */
@FunctionalInterface
public interface DensityFunction {

    /**
     * Evaluates the 3D density at continuous world coordinates.
     */
    float compute(float x, float y, float z);

    /**
     * Convenience double precision evaluation alias.
     */
    default double evaluate(double x, double y, double z) {
        return compute((float) x, (float) y, (float) z);
    }

    /**
     * Evaluates an entire vertical column [minY, maxY) into the provided output buffer.
     * Index `i` in `out` corresponds to world Y coordinate `minY + i`.
     * Nodes such as {@link YSampledDensity}, {@link Noise2dDensity}, {@link ConstantDensity},
     * and combinator nodes override this to propagate column batching down the DAG.
     */
    default void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = compute(x, minY + i, z);
        }
    }

    /** True when the value does not depend on Y, so a whole column needs one evaluation. Only ever answer true when certain. */
    default boolean yIndependent() { return false; }

    /** True when the value depends on Y only (never on X or Z), so it can be tabulated once for the whole world. */
    default boolean xzIndependent() { return false; }

    /**
     * Conservative bounds of the values at (x, y, z) for every integer Y in [y0, y1): out[0] at most the smallest, out[1] at least
     * the largest. Used to skip parts of a column whose sign is already certain. The default knows a node that does not depend on Y
     * (one value) and otherwise nothing (the whole number line); nodes that can do better say so.
     */
    default void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) {
            float v = compute(x, y0, z);
            if (Float.isNaN(v)) { out[0] = Float.NEGATIVE_INFINITY; out[1] = Float.POSITIVE_INFINITY; return; }
            out[0] = v;
            out[1] = v;
            return;
        }
        out[0] = Float.NEGATIVE_INFINITY;
        out[1] = Float.POSITIVE_INFINITY;
    }

    /**
     * {@link #bounds} for every slab of {@code bs} heights of [minY, maxY) at once: slab k covers [minY + k*bs, min(maxY, minY + (k+1)*bs)).
     * One walk of the tree serves a whole column. The default asks {@link #bounds} slab by slab.
     */
    default void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent()) {
            float v = compute(x, minY, z);
            float l = Float.isNaN(v) ? Float.NEGATIVE_INFINITY : v, h = Float.isNaN(v) ? Float.POSITIVE_INFINITY : v;
            java.util.Arrays.fill(lo, 0, nb, l);
            java.util.Arrays.fill(hi, 0, nb, h);
            return;
        }
        float[] b = new float[2];
        for (int k = 0; k < nb; k++) {
            bounds(x, z, minY + k * bs, Math.min(maxY, minY + (k + 1) * bs), b);
            lo[k] = b[0];
            hi[k] = b[1];
        }
    }

    default DensityFunction add(DensityFunction other) {
        return new SumDensity(this, other);
    }

    default DensityFunction multiply(DensityFunction other) {
        return new ProductDensity(this, other);
    }

    default DensityFunction clamp(float min, float max) {
        return new ClampDensity(this, min, max);
    }

    default DensityFunction invert() {
        return new InvertDensity(this);
    }

    default DensityFunction abs() {
        return new AbsDensity(this);
    }

    default DensityFunction normalize(float fromMin, float fromMax) {
        return new NormalizerDensity(this, fromMin, fromMax);
    }

    default DensityFunction offset(float dx, float dy, float dz) {
        return new OffsetDensity(this, dx, dy, dz);
    }

    default DensityFunction scale(float sx, float sy, float sz) {
        return new ScaleDensity(this, sx, sy, sz);
    }
}
