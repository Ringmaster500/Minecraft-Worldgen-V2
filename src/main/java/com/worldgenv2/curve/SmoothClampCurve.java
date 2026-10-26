package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Smoothly clamps the child curve's output between wallA and wallB within the given smoothing range.
 */
public final class SmoothClampCurve implements Curve {

    private final Curve curve;
    private final double min;
    private final double max;
    private final double range;

    public SmoothClampCurve(Curve curve, double wallA, double wallB, double range) {
        this.curve = Objects.requireNonNull(curve, "curve");
        this.min = Math.min(wallA, wallB);
        this.max = Math.max(wallA, wallB);
        this.range = Math.max(0.0, range);
    }

    @Override
    public double evaluate(double input) {
        double v = curve.evaluate(input);
        if (range <= 1e-9) {
            return Math.clamp(v, min, max);
        }
        // Apply smooth floor first, then smooth ceiling
        double hFloor = Math.clamp(0.5 + 0.5 * (v - min) / range, 0.0, 1.0);
        double floored = min * (1.0 - hFloor) + v * hFloor + range * hFloor * (1.0 - hFloor);

        double hCeil = Math.clamp(0.5 + 0.5 * (max - floored) / range, 0.0, 1.0);
        return max * (1.0 - hCeil) + floored * hCeil - range * hCeil * (1.0 - hCeil);
    }

    public Curve getCurve() {
        return curve;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getRange() {
        return range;
    }
}
