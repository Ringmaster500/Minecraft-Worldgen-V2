package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Puts a lower bound (floor) on the child curve, smoothly curving into the floor
 * within the specified smoothing range.
 */
public record SmoothFloorCurve(Curve curve, double floor, double range) implements Curve {

    public SmoothFloorCurve {
        Objects.requireNonNull(curve, "curve");
        if (range < 0.0) range = 0.0;
    }

    @Override
    public double evaluate(double input) {
        double v = curve.evaluate(input);
        if (range <= 1e-9) {
            return Math.max(floor, v);
        }
        double h = Math.clamp(0.5 + 0.5 * (v - floor) / range, 0.0, 1.0);
        return floor * (1.0 - h) + v * h + range * h * (1.0 - h);
    }
}
