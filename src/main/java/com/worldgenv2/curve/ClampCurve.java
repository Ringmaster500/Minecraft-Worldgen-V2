package com.worldgenv2.curve;

import java.util.Objects;

/**
 * Hard-clamps the child curve's output between wallA and wallB.
 */
public final class ClampCurve implements Curve {

    private final Curve curve;
    private final double min;
    private final double max;

    public ClampCurve(Curve curve, double wallA, double wallB) {
        this.curve = Objects.requireNonNull(curve, "curve");
        this.min = Math.min(wallA, wallB);
        this.max = Math.max(wallA, wallB);
    }

    @Override
    public double evaluate(double input) {
        return Math.clamp(curve.evaluate(input), min, max);
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
}
