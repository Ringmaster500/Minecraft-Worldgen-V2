package com.worldgenv2.density;

import java.util.Arrays;
import java.util.List;

public final class MaxDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public MaxDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public MaxDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.length == 0) return 0.0f;
        float max = inputs[0].compute(x, y, z);
        for (int i = 1; i < inputs.length; i++) {
            max = Math.max(max, inputs[i].compute(x, y, z));
        }
        return max;
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (allFlat(inputs) ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (allFlatXZ(inputs) ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        int height = maxY - minY;
        if (height <= 0) return;
        if (inputs.length == 0) { Arrays.fill(out, 0, height, 0f); return; }

        // inputs that do not depend on Y are evaluated once per column and folded in as one number
        float flat = Float.NEGATIVE_INFINITY;
        boolean haveFlat = false, filled = false;
        float[] scratch = null;
        for (DensityFunction in : inputs) {
            if (in.yIndependent()) {
                float v = in.compute(x, minY, z);
                flat = Math.max(flat, v);
                haveFlat = true;
            } else if (!filled) {
                in.computeColumn(x, z, minY, maxY, out);
                filled = true;
            } else {
                if (scratch == null) scratch = ColumnPool.take(height);
                in.computeColumn(x, z, minY, maxY, scratch);
                for (int i = 0; i < height; i++) {
                    out[i] = Math.max(out[i], scratch[i]);
                }
            }
        }
        if (scratch != null) ColumnPool.give(scratch);
        if (haveFlat) {
            for (int i = 0; i < height; i++) {
                out[i] = Math.max(out[i], flat);
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
    private static boolean allFlat(DensityFunction[] in) { for (DensityFunction f : in) if (!f.yIndependent()) return false; return true; }
    private static boolean allFlatXZ(DensityFunction[] in) { for (DensityFunction f : in) if (!f.xzIndependent()) return false; return true; }
}
