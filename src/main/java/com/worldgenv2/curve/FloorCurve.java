package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Puts a lower bound (floor) on the output of the child curve.
 */
public record FloorCurve(Curve curve, double floor) implements Curve {

    public FloorCurve {
        Objects.requireNonNull(curve, "curve");
    }

    @Override
    public double evaluate(double input) {
        return Math.max(floor, curve.evaluate(input));
    }
}
