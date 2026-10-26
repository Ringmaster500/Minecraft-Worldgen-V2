package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Puts an upper bound (ceiling) on the child curve, smoothly curving into the ceiling
 * within the specified smoothing range.
 */
public record SmoothCeilingCurve(Curve curve, double ceiling, double range) implements Curve {

    public SmoothCeilingCurve {
        Objects.requireNonNull(curve, "curve");
        if (range < 0.0) range = 0.0;
    }

    @Override
    public double evaluate(double input) {
        double v = curve.evaluate(input);
        if (range <= 1e-9) {
            return Math.min(ceiling, v);
        }
        double h = Math.clamp(0.5 + 0.5 * (ceiling - v) / range, 0.0, 1.0);
        return ceiling * (1.0 - h) + v * h - range * h * (1.0 - h);
    }
}
