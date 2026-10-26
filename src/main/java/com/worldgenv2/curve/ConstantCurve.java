package com.worldgenv2.curve;

/**
 * Curve returning an invariant constant value regardless of input.
 */
public record ConstantCurve(double value) implements Curve {

    @Override
    public double evaluate(double input) {
        return value;
    }
}
