package com.worldgenv2.curve;

import java.util.List;
import java.util.Objects;

/**
 * Returns the maximum value across all child curves.
 */
public final class MaxCurve implements Curve {

    private final List<Curve> curves;

    public MaxCurve(List<Curve> curves) {
        Objects.requireNonNull(curves, "curves");
        if (curves.isEmpty()) {
            throw new IllegalArgumentException("MaxCurve requires at least one curve");
        }
        this.curves = List.copyOf(curves);
    }

    @Override
    public double evaluate(double input) {
        double max = Double.NEGATIVE_INFINITY;
        for (Curve c : curves) {
            double v = c.evaluate(input);
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    public List<Curve> getCurves() {
        return curves;
    }
}
