package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Blends across a list of input densities according to an index/selector field.
 */
public final class MultiMixDensity implements DensityFunction {

    private final List<DensityFunction> inputs;
    private final DensityFunction selector;

    public MultiMixDensity(List<DensityFunction> inputs, DensityFunction selector) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        this.inputs = List.copyOf(inputs);
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    public List<DensityFunction> inputs() {
        return inputs;
    }

    public DensityFunction selector() {
        return selector;
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.size() == 1) {
            return inputs.get(0).compute(x, y, z);
        }
        float t = selector.compute(x, y, z);
        // Map t into [0, inputs.size() - 1]
        float scaled = t * (inputs.size() - 1);
        int idx = (int) Math.floor(scaled);
        float frac = scaled - idx;

        if (idx < 0) return inputs.get(0).compute(x, y, z);
        if (idx >= inputs.size() - 1) return inputs.get(inputs.size() - 1).compute(x, y, z);

        float v0 = inputs.get(idx).compute(x, y, z);
        float v1 = inputs.get(idx + 1).compute(x, y, z);
        return v0 + frac * (v1 - v0);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = compute(x, minY + i, z);
        }
    }
}
