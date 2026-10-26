package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Returns the polynomial smooth minimum between two curves within the specified range.
 */
public record SmoothMinCurve(Curve curveA, Curve curveB, double range) implements Curve {

    public SmoothMinCurve {
        Objects.requireNonNull(curveA, "curveA");
        Objects.requireNonNull(curveB, "curveB");
        if (range < 0.0) range = 0.0;
    }

    @Override
    public double evaluate(double input) {
        double a = curveA.evaluate(input);
        double b = curveB.evaluate(input);
        if (range <= 1e-9) {
            return Math.min(a, b);
        }
        double h = Math.clamp(0.5 + 0.5 * (b - a) / range, 0.0, 1.0);
        return b * (1.0 - h) + a * h - range * h * (1.0 - h);
    }
}
