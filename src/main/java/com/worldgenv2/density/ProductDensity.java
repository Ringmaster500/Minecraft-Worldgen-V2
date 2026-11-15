package com.worldgenv2.density;

import java.util.Arrays;
import java.util.List;

public final class ProductDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public ProductDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public ProductDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        float product = 1.0f;
        for (DensityFunction input : inputs) {
            product *= input.compute(x, y, z);
        }
        return product;
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (allFlat(inputs) ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (allFlatXZ(inputs) ? 1 : 2); return f == 1; }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        float lo = 1f, hi = 1f;
        for (DensityFunction in : inputs) { in.bounds(x, z, y0, y1, out); float l = Bounds.mulLo(lo, hi, out[0], out[1]), h = Bounds.mulHi(lo, hi, out[0], out[1]); lo = l; hi = h; }
        Bounds.set(out, lo, hi);    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        int height = maxY - minY;
        if (height <= 0) return;
        if (inputs.length == 0) { Arrays.fill(out, 0, height, 1f); return; }

        // inputs that do not depend on Y are evaluated once per column and folded in as one number
        float flat = 1f;
        boolean haveFlat = false, filled = false;
        float[] scratch = null;
        for (DensityFunction in : inputs) {
            if (in.yIndependent()) {
                float v = in.compute(x, minY, z);
                flat *= v;
                haveFlat = true;
            } else if (!filled) {
                in.computeColumn(x, z, minY, maxY, out);
                filled = true;
            } else {
                if (scratch == null) scratch = ColumnPool.take(height);
                in.computeColumn(x, z, minY, maxY, scratch);
                for (int i = 0; i < height; i++) {
                    out[i] *= scratch[i];
                }
            }
        }
        if (scratch != null) ColumnPool.give(scratch);
        if (haveFlat) {
            for (int i = 0; i < height; i++) {
                out[i] *= flat;
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
    private static boolean allFlat(DensityFunction[] in) { for (DensityFunction f : in) if (!f.yIndependent()) return false; return true; }
    private static boolean allFlatXZ(DensityFunction[] in) { for (DensityFunction f : in) if (!f.xzIndependent()) return false; return true; }
}
