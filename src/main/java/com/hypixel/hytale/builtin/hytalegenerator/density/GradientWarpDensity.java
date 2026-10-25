package com.hypixel.hytale.builtin.hytalegenerator.density;

/**
 * Gradient Warp density node.
 * Perturbs the evaluation coordinates along the gradient vector of a warp noise field,
 * producing organic, flowing terrain contours and eliminating grid artifacts.
 */
public final class GradientWarpDensity implements DensityFunction {

    private final DensityFunction source;
    private final DensityFunction warpSource;
    private final float warpStrength;

    public GradientWarpDensity(DensityFunction source, DensityFunction warpSource, float warpStrength) {
        this.source = source;
        this.warpSource = warpSource;
        this.warpStrength = warpStrength;
    }

    @Override
    public float compute(float x, float y, float z) {
        // Sample gradient offsets along X and Z
        float delta = 1.0f;
        float warpX = (warpSource.compute(x + delta, y, z) - warpSource.compute(x - delta, y, z)) * 0.5f * warpStrength;
        float warpZ = (warpSource.compute(x, y, z + delta) - warpSource.compute(x, y, z - delta)) * 0.5f * warpStrength;

        return source.compute(x + warpX, y, z + warpZ);
    }

    public DensityFunction getSource() {
        return source;
    }

    public DensityFunction getWarpSource() {
        return warpSource;
    }

    public float getWarpStrength() {
        return warpStrength;
    }
}
