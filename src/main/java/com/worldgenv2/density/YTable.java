package com.worldgenv2.density;

/**
 * The values of a density that depends only on Y (a gradient, a curve of Y), tabulated once for a height range. Every column of
 * the world then reads the same table instead of evaluating the node for each block.
 */
final class YTable {
    private record Table(int minY, int maxY, float[] values) {}

    private volatile Table table;

    void fill(DensityFunction f, int minY, int maxY, float[] out) {
        Table t = table;
        if (t == null || t.minY != minY || t.maxY != maxY) {
            float[] v = new float[maxY - minY];
            for (int i = 0; i < v.length; i++) v[i] = f.compute(0f, minY + i, 0f);
            table = t = new Table(minY, maxY, v);
        }
        System.arraycopy(t.values, 0, out, 0, maxY - minY);
    }
}