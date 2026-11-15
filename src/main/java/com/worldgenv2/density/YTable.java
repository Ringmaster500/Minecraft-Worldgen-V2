package com.worldgenv2.density;

/**
 * The values of a density that depends only on Y (a gradient, a curve of Y), tabulated once for a range of heights that grows to
 * cover every request. Every column of the world then reads the same table instead of evaluating the node for each block. The table
 * also keeps the smallest and largest value of every 8 heights, for {@link DensityFunction#bounds}.
 */
final class YTable {
    private record Table(int minY, int maxY, float[] values, float[] blockMin, float[] blockMax) {}

    private volatile Table table;

    private Table cover(DensityFunction f, int y0, int y1) {
        Table t = table;
        if (t != null && t.minY <= y0 && t.maxY >= y1) return t;
        int lo = t == null ? y0 : Math.min(y0, t.minY), hi = t == null ? y1 : Math.max(y1, t.maxY);
        lo = Math.floorDiv(lo, 8) * 8;
        hi = Math.floorDiv(hi + 7, 8) * 8;
        float[] v = new float[hi - lo];
        for (int i = 0; i < v.length; i++) v[i] = f.compute(0f, lo + i, 0f);
        int blocks = v.length / 8;
        float[] bmin = new float[blocks], bmax = new float[blocks];
        for (int b = 0; b < blocks; b++) {
            float mn = Float.POSITIVE_INFINITY, mx = Float.NEGATIVE_INFINITY;
            for (int i = b * 8; i < b * 8 + 8; i++) {
                float x = v[i];
                if (Float.isNaN(x)) { mn = Float.NEGATIVE_INFINITY; mx = Float.POSITIVE_INFINITY; break; }
                if (x < mn) mn = x;
                if (x > mx) mx = x;
            }
            bmin[b] = mn;
            bmax[b] = mx;
        }
        Table nt = new Table(lo, hi, v, bmin, bmax);
        table = nt;
        return nt;
    }

    void fill(DensityFunction f, int minY, int maxY, float[] out) {
        Table t = cover(f, minY, maxY);
        System.arraycopy(t.values, minY - t.minY, out, 0, maxY - minY);
    }

    /** Smallest and largest value for Y in [y0, y1) into out[0], out[1]. */
    void bounds(DensityFunction f, int y0, int y1, float[] out) {
        Table t = cover(f, y0, y1);
        float mn = Float.POSITIVE_INFINITY, mx = Float.NEGATIVE_INFINITY;
        int i = y0 - t.minY, end = y1 - t.minY;
        while (i < end) {
            if ((i & 7) == 0 && i + 8 <= end) {
                int b = i >> 3;
                if (t.blockMin[b] < mn) mn = t.blockMin[b];
                if (t.blockMax[b] > mx) mx = t.blockMax[b];
                i += 8;
            } else {
                float x = t.values[i++];
                if (Float.isNaN(x)) { mn = Float.NEGATIVE_INFINITY; mx = Float.POSITIVE_INFINITY; break; }
                if (x < mn) mn = x;
                if (x > mx) mx = x;
            }
        }
        out[0] = mn;
        out[1] = mx;
    }
}