package com.worldgenv2.density;

import java.util.Locale;
import java.util.Objects;

/**
 * Calculates trigonometric functions (sin, cos, tan) on an input density field:
 * {@code f(x, y, z) = amplitude * func(frequency * input + phase)}.
 */
public final class TrigDensity implements DensityFunction {

    public enum Function {
        SIN, COS, TAN
    }

    private final DensityFunction input;
    private final Function function;
    private final float frequency;
    private final float amplitude;
    private final float phase;

    public TrigDensity(DensityFunction input, Function function, float frequency, float amplitude, float phase) {
        this.input = Objects.requireNonNull(input, "input");
        this.function = function != null ? function : Function.SIN;
        this.frequency = frequency;
        this.amplitude = amplitude;
        this.phase = phase;
    }

    public DensityFunction input() {
        return input;
    }

    public Function function() {
        return function;
    }

    public float frequency() {
        return frequency;
    }

    public float amplitude() {
        return amplitude;
    }

    public float phase() {
        return phase;
    }

    @Override
    public float compute(float x, float y, float z) {
        float v = input.compute(x, y, z);
        double angle = frequency * v + phase;
        double res = switch (function) {
            case SIN -> Math.sin(angle);
            case COS -> Math.cos(angle);
            case TAN -> Math.tan(angle);
        };
        return (float) (amplitude * res);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (input.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (input.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            double angle = frequency * out[i] + phase;
            double res = switch (function) {
                case SIN -> Math.sin(angle);
                case COS -> Math.cos(angle);
                case TAN -> Math.tan(angle);
            };
            out[i] = (float) (amplitude * res);
        }
    }
}
