package com.worldgenv2.density;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Dynamically selects among named density branches based on a contextual state tag.
 */
public final class SwitchStateDensity implements DensityFunction {

    private final Supplier<String> stateSupplier;
    private final Map<String, DensityFunction> branches;
    private final DensityFunction defaultBranch;

    public SwitchStateDensity(Supplier<String> stateSupplier, Map<String, DensityFunction> branches, DensityFunction defaultBranch) {
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier");
        this.branches = Map.copyOf(branches);
        this.defaultBranch = Objects.requireNonNull(defaultBranch, "defaultBranch");
    }

    public Map<String, DensityFunction> branches() {
        return branches;
    }

    public DensityFunction defaultBranch() {
        return defaultBranch;
    }

    private DensityFunction resolve() {
        String state = stateSupplier.get();
        if (state != null) {
            DensityFunction match = branches.get(state);
            if (match != null) return match;
        }
        return defaultBranch;
    }

    @Override
    public float compute(float x, float y, float z) {
        return resolve().compute(x, y, z);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        resolve().computeColumn(x, z, minY, maxY, out);
    }
}
