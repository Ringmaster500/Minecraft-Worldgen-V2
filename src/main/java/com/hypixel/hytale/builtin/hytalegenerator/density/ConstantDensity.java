package com.hypixel.hytale.builtin.hytalegenerator.density;

import java.util.List;

public final class ConstantDensity implements DensityFunction {
    private final float value;

    public ConstantDensity(float value) {
        this.value = value;
    }

    @Override
    public float compute(float x, float y, float z) {
        return value;
    }

    public float getValue() {
        return value;
    }
}
