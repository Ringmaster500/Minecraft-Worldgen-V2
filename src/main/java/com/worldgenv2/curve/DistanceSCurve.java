package com.worldgenv2.curve;

/**
 * S-shaped falloff curve combining two exponential segments.
 * Outputs 1.0 at input = 0.0 and smoothly descends to 0.0 at input = range.
 * Beyond range, outputs constant 0.0; below 0.0, outputs constant 1.0.
 */
public record DistanceSCurve(
    double exponentA,
    double exponentB,
    double range,
    double transition,
    double transitionSmooth
) implements Curve {

    public DistanceSCurve(double exponentA, double exponentB, double range) {
        this(exponentA, exponentB, range, 1.0, 1.0);
    }

    public DistanceSCurve {
        if (range <= 0.0) range = 1.0;
        if (exponentA <= 0.0) exponentA = 1.0;
        if (exponentB <= 0.0) exponentB = 1.0;
        transition = Math.clamp(transition, 0.0, 1.0);
        transitionSmooth = Math.clamp(transitionSmooth, 0.0, 1.0);
    }

    @Override
    public double evaluate(double input) {
        if (input <= 0.0) return 1.0;
        if (input >= range) return 0.0;

        double t = input / range;

        // Two exponential branches joined at t = 0.5
        double valA = 1.0 - 0.5 * Math.pow(Math.min(1.0, 2.0 * t), exponentA);
        double valB = 0.5 * Math.pow(Math.max(0.0, 2.0 * (1.0 - t)), exponentB);

        if (t <= 0.5) {
            double blend = 2.0 * t;
            // Transition parameter controls sharpness around midpoint
            double w = Math.pow(blend, 1.0 / Math.max(0.01, transitionSmooth));
            return valA * (1.0 - 0.5 * w * (1.0 - transition)) + valB * (0.5 * w * (1.0 - transition));
        } else {
            double blend = 2.0 * (1.0 - t);
            double w = Math.pow(blend, 1.0 / Math.max(0.01, transitionSmooth));
            return valB * (1.0 - 0.5 * w * (1.0 - transition)) + valA * (0.5 * w * (1.0 - transition));
        }
    }
}
