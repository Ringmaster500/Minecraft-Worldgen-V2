package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Multi-way routing switch density node.
 * Evaluates the indexSelector density function, rounds it to the nearest integer index,
 * and executes only the selected branch.
 */
public final class SwitchDensity implements DensityFunction {

    private final DensityFunction indexSelector;
    private final List<DensityFunction> branches;

    public SwitchDensity(DensityFunction indexSelector, List<DensityFunction> branches) {
        this.indexSelector = Objects.requireNonNull(indexSelector, "indexSelector");
        Objects.requireNonNull(branches, "branches");
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("SwitchDensity requires at least one branch");
        }
        this.branches = List.copyOf(branches);
    }

    @Override
    public float compute(float x, float y, float z) {
        int idx = Math.round(indexSelector.compute(x, y, z));
        int clamped = Math.clamp(idx, 0, branches.size() - 1);
        return branches.get(clamped).compute(x, y, z);
    }

    public DensityFunction getIndexSelector() {
        return indexSelector;
    }

    public List<DensityFunction> getBranches() {
        return branches;
    }
}
