package com.worldgenv2.density;

import java.util.Objects;

/**
 * Conditional branching density node (if-then-else).
 * If condition != 0.0 (or &gt; 0), evaluates and returns thenDensity;
 * otherwise evaluates and returns elseDensity.
 * Lazily short-circuits evaluation to skip expensive sub-trees.
 */
public final class SelectorDensity implements DensityFunction {

    private final DensityFunction condition;
    private final DensityFunction thenDensity;
    private final DensityFunction elseDensity;

    public SelectorDensity(DensityFunction condition, DensityFunction thenDensity, DensityFunction elseDensity) {
        this.condition = Objects.requireNonNull(condition, "condition");
        this.thenDensity = Objects.requireNonNull(thenDensity, "thenDensity");
        this.elseDensity = Objects.requireNonNull(elseDensity, "elseDensity");
    }

    @Override
    public float compute(float x, float y, float z) {
        float cond = condition.compute(x, y, z);
        return cond != 0.0f ? thenDensity.compute(x, y, z) : elseDensity.compute(x, y, z);
    }

    @Override public boolean yIndependent() { return condition.yIndependent() && thenDensity.yIndependent() && elseDensity.yIndependent(); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            float y = minY + i;
            float cond = condition.compute(x, y, z);
            out[i] = cond != 0.0f ? thenDensity.compute(x, y, z) : elseDensity.compute(x, y, z);
        }
    }

    public DensityFunction getCondition() {
        return condition;
    }

    public DensityFunction getThenDensity() {
        return thenDensity;
    }

    public DensityFunction getElseDensity() {
        return elseDensity;
    }
}
