package com.worldgenv2.props;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Density-driven clustering gate for natural flora and prop scatter.
 * Evaluates a DensityFunction at the anchor coordinate and either:
 * 1. Gates deterministically when density >= threshold (if probabilistic == false), or
 * 2. Modulates placement probability P(x, y, z) = clamp(density * biomeMultiplier - threshold, 0.0, 1.0)
 *    against a deterministic coordinate hash (if probabilistic == true).
 */
public final class DensitySelectorProp<T> implements Prop<T> {

    private final DensityFunction densityField;
    private final float threshold;
    private final float biomeMultiplier;
    private final boolean probabilistic;
    private final Prop<T> childProp;

    public DensitySelectorProp(DensityFunction densityField, float threshold, Prop<T> childProp) {
        this(densityField, threshold, 1.0f, false, childProp);
    }

    public DensitySelectorProp(
        DensityFunction densityField,
        float threshold,
        float biomeMultiplier,
        boolean probabilistic,
        Prop<T> childProp
    ) {
        this.densityField = Objects.requireNonNull(densityField, "densityField");
        this.threshold = threshold;
        this.biomeMultiplier = biomeMultiplier;
        this.probabilistic = probabilistic;
        this.childProp = Objects.requireNonNull(childProp, "childProp");
    }

    public DensityFunction densityField() {
        return densityField;
    }

    public float threshold() {
        return threshold;
    }

    public Prop<T> childProp() {
        return childProp;
    }

    @Override
    public boolean generate(Context<T> context) {
        float sample = densityField.compute(context.worldX(), context.worldY(), context.worldZ());
        if (!probabilistic) {
            if (sample < threshold) {
                return false;
            }
            return childProp.generate(context);
        }

        float probability = Math.clamp(sample * biomeMultiplier - threshold, 0.0f, 1.0f);
        if (probability <= 0.0f) {
            return false;
        }
        float roll = hashToUnitFloat(context.seed(), context.worldX(), context.worldY(), context.worldZ());
        if (roll >= probability) {
            return false;
        }
        return childProp.generate(context);
    }

    private static float hashToUnitFloat(long seed, int x, int y, int z) {
        long h = seed ^ ((long) x * 0x9E3779B97F4A7C15L)
            ^ ((long) y * 0xBF58476D1CE4E5B9L)
            ^ ((long) z * 0x94D049BB133111EBL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return ((h >>> 40) & 0xFFFFFF) / (float) (1 << 24);
    }

    @Override
    public int horizontalReach() {
        return childProp.horizontalReach();
    }
}
