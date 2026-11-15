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
