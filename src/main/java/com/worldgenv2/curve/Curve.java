package com.worldgenv2.curve;

/**
 * Fundamental 1D transfer function interface f(x) = y.
 * Maps decimal scalar inputs to decimal scalar outputs.
 * Used for shaping density distributions, erosion falloffs, plateau capping, and smooth transitions.
 */
@FunctionalInterface
public interface Curve {

    /**
     * Evaluates the curve function at input x.
     *
     * @param input scalar input value (continuous or normalized)
     * @return transformed scalar output value
     */
    double evaluate(double input);

    default Curve invert() {
        return new InverterCurve(this);
    }

    default Curve not() {
        return new NotCurve(this);
    }

    default Curve clamp(double wallA, double wallB) {
        return new ClampCurve(this, wallA, wallB);
    }

    default Curve smoothClamp(double wallA, double wallB, double range) {
        return new SmoothClampCurve(this, wallA, wallB, range);
    }

    /** The smallest and largest output for inputs in [lo, hi], or null when the curve cannot tell. */
    default double[] range(double lo, double hi) { return null; }
}
