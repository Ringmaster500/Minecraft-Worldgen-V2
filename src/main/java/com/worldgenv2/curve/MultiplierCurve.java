package com.worldgenv2.curve;

import java.util.List;
import java.util.Objects;

/**
 * Multiplies the outputs of all child curves together.
 */
public final class MultiplierCurve implements Curve {

    private final List<Curve> curves;

    public MultiplierCurve(List<Curve> curves) {
        Objects.requireNonNull(curves, "curves");
        this.curves = List.copyOf(curves);
    }

    public MultiplierCurve(Curve... curves) {
        this(List.of(curves));
    }

    @Override
    public double evaluate(double input) {
        double product = 1.0;
        for (Curve c : curves) {
            product *= c.evaluate(input);
        }
        return product;
    }

    public List<Curve> getCurves() {
        return curves;
    }
}
