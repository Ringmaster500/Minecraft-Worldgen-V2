package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Inverts the sign of the child curve's output (f(x) -> -f(x)).
 */
public record InverterCurve(Curve curve) implements Curve {

    public InverterCurve {
        Objects.requireNonNull(curve, "curve");
    }

    @Override
    public double evaluate(double input) {
        return -curve.evaluate(input);
    }
}
