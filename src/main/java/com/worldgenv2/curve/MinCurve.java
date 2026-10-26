package com.worldgenv2.curve;

import java.util.List;
import java.util.Objects;

/**
 * Returns the minimum value across all child curves.
 */
public final class MinCurve implements Curve {

    private final List<Curve> curves;

    public MinCurve(List<Curve> curves) {
        Objects.requireNonNull(curves, "curves");
        if (curves.isEmpty()) {
            throw new IllegalArgumentException("MinCurve requires at least one curve");
        }
        this.curves = List.copyOf(curves);
    }

    @Override
    public double evaluate(double input) {
        double min = Double.POSITIVE_INFINITY;
        for (Curve c : curves) {
            double v = c.evaluate(input);
            if (v < min) {
                min = v;
            }
        }
        return min;
    }

    public List<Curve> getCurves() {
        return curves;
    }
}
