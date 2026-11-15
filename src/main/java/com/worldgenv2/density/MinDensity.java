package com.worldgenv2.density;

import java.util.Arrays;
import java.util.List;

public final class MinDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public MinDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public MinDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.length == 0) return 0.0f;
        float min = inputs[0].compute(x, y, z);
        for (int i = 1; i < inputs.length; i++) {
            min = Math.min(min, inputs[i].compute(x, y, z));
        }
        return min;
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (allFlat(inputs) ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (allFlatXZ(inputs) ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        if (inputs.length == 0) { java.util.Arrays.fill(lo, 0, nb, 0f); java.util.Arrays.fill(hi, 0, nb, 0f); return; }
        java.util.Arrays.fill(lo, 0, nb, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(hi, 0, nb, Float.POSITIVE_INFINITY);
        float[] l = ColumnPool.take(nb), h = ColumnPool.take(nb);
        for (DensityFunction in : inputs) { in.boundsSlabs(x, z, minY, maxY, bs, l, h); for (int k = 0; k < nb; k++) { lo[k] = Math.min(lo[k], l[k]); hi[k] = Math.min(hi[k], h[k]); } }
        ColumnPool.give(h); ColumnPool.give(l);    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        float lo = Float.POSITIVE_INFINITY, hi = Float.POSITIVE_INFINITY;
        if (inputs.length == 0) { Bounds.set(out, 0f, 0f); return; }
        for (DensityFunction in : inputs) { in.bounds(x, z, y0, y1, out); lo = Math.min(lo, out[0]); hi = Math.min(hi, out[1]); }
        Bounds.set(out, lo, hi);    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        int height = maxY - minY;
        if (height <= 0) return;
        if (inputs.length == 0) { Arrays.fill(out, 0, height, 0f); return; }

        // inputs that do not depend on Y are evaluated once per column and folded in as one number
        float flat = Float.POSITIVE_INFINITY;
        boolean haveFlat = false, filled = false;
        float[] scratch = null;
        for (DensityFunction in : inputs) {
            if (in.yIndependent()) {
                float v = in.compute(x, minY, z);
                flat = Math.min(flat, v);
                haveFlat = true;
            } else if (!filled) {
                in.computeColumn(x, z, minY, maxY, out);
                filled = true;
            } else {
                if (scratch == null) scratch = ColumnPool.take(height);
                in.computeColumn(x, z, minY, maxY, scratch);
                for (int i = 0; i < height; i++) {
                    out[i] = Math.min(out[i], scratch[i]);
                }
            }
        }
        if (scratch != null) ColumnPool.give(scratch);
        if (haveFlat) {
            for (int i = 0; i < height; i++) {
                out[i] = Math.min(out[i], flat);
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
    private static boolean allFlat(DensityFunction[] in) { for (DensityFunction f : in) if (!f.yIndependent()) return false; return true; }
    private static boolean allFlatXZ(DensityFunction[] in) { for (DensityFunction f : in) if (!f.xzIndependent()) return false; return true; }
}
