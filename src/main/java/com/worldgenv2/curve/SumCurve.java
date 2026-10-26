package com.worldgenv2.curve;

import java.util.List;
import java.util.Objects;

/**
 * Sums the outputs of all child curves together.
 */
public final class SumCurve implements Curve {

    private final List<Curve> curves;

    public SumCurve(List<Curve> curves) {
        Objects.requireNonNull(curves, "curves");
        this.curves = List.copyOf(curves);
    }

    public SumCurve(Curve... curves) {
        this(List.of(curves));
    }

    @Override
    public double evaluate(double input) {
        double sum = 0.0;
        for (Curve c : curves) {
            sum += c.evaluate(input);
        }
        return sum;
    }

    public List<Curve> getCurves() {
        return curves;
    }
}
