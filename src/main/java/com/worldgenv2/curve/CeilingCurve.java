package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Puts an upper bound (ceiling) on the output of the child curve.
 */
public record CeilingCurve(Curve curve, double ceiling) implements Curve {

    public CeilingCurve {
        Objects.requireNonNull(curve, "curve");
    }

    @Override
    public double evaluate(double input) {
        return Math.min(ceiling, curve.evaluate(input));
    }
}
