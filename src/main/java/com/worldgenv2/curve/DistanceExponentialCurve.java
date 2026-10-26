package com.worldgenv2.curve;

/**
 * Exponential falloff curve.
 * At input = 0.0, outputs 1.0.
 * As input approaches range, outputs 0.0.
 * Beyond range, outputs constant 0.0; below 0.0, outputs 1.0.
 */
public record DistanceExponentialCurve(double exponent, double range) implements Curve {

    public DistanceExponentialCurve {
        if (range <= 0.0) {
            range = 1.0;
        }
        if (exponent <= 0.0) {
            exponent = 1.0;
        }
    }

    @Override
    public double evaluate(double input) {
        if (input <= 0.0) {
            return 1.0;
        }
        if (input >= range) {
            return 0.0;
        }
        double t = input / range;
        return Math.pow(1.0 - t, exponent);
    }
}
