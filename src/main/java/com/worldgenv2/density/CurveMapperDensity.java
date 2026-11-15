package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;
import java.util.Objects;

/**
 * Density function adapter that routes the output of a source density function through a {@link Curve}.
 * Enables arbitrary nonlinear transfer functions, plateaus, s-curves, and smooth clamping.
 */
public final class CurveMapperDensity implements DensityFunction {

    private final DensityFunction source;
    private final Curve curve;

    public CurveMapperDensity(DensityFunction source, Curve curve) {
        this.source = Objects.requireNonNull(source, "source");
        this.curve = Objects.requireNonNull(curve, "curve");
    }

    @Override
    public float compute(float x, float y, float z) {
        float val = source.compute(x, y, z);
        return (float) curve.evaluate(val);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (source.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (source.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        source.computeColumn(x, z, minY, maxY, out);
        for (int i = 0, n = maxY - minY; i < n; i++) {
            out[i] = (float) curve.evaluate(out[i]);
        }
    }

    public DensityFunction getSource() {
        return source;
    }

    public Curve getCurve() {
        return curve;
    }
}
