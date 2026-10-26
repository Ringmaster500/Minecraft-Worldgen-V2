package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Logical NOT transfer function (f(x) -> 1.0 - f(x)).
 * Maps 1 to 0, 0 to 1, and intermediate values linearly.
 */
public record NotCurve(Curve curve) implements Curve {

    public NotCurve {
        Objects.requireNonNull(curve, "curve");
    }

    @Override
    public double evaluate(double input) {
        return 1.0 - curve.evaluate(input);
    }
}
