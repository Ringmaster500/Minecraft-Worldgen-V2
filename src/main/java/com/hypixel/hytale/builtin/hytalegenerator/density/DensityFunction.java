package com.hypixel.hytale.builtin.hytalegenerator.density;

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

    default DensityFunction add(DensityFunction other) {
        return (x, y, z) -> this.compute(x, y, z) + other.compute(x, y, z);
    }

    default DensityFunction multiply(DensityFunction other) {
        return (x, y, z) -> this.compute(x, y, z) * other.compute(x, y, z);
    }

    default DensityFunction clamp(float min, float max) {
        return (x, y, z) -> Math.clamp(this.compute(x, y, z), min, max);
    }

    default DensityFunction invert() {
        return (x, y, z) -> -this.compute(x, y, z);
    }
}
