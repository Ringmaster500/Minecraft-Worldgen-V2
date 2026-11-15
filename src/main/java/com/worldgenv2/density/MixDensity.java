package com.worldgenv2.density;

import java.util.Objects;

/**
 * Linearly interpolates between two densities based on a factor field:
 * {@code f(x, y, z) = (1 - factor) * a + factor * b}.
 */
public final class MixDensity implements DensityFunction {

    private final DensityFunction a;
    private final DensityFunction b;
    private final DensityFunction factor;

    public MixDensity(DensityFunction a, DensityFunction b, DensityFunction factor) {
        this.a = Objects.requireNonNull(a, "a");
        this.b = Objects.requireNonNull(b, "b");
        this.factor = Objects.requireNonNull(factor, "factor");
    }

    public DensityFunction a() {
        return a;
    }

    public DensityFunction b() {
        return b;
    }

    public DensityFunction factor() {
        return factor;
    }

    @Override
    public float compute(float x, float y, float z) {
        float t = Math.clamp(factor.compute(x, y, z), 0.0f, 1.0f);
        if (t <= 0.0f) return a.compute(x, y, z);
        if (t >= 1.0f) return b.compute(x, y, z);
        float va = a.compute(x, y, z);
        float vb = b.compute(x, y, z);
        return va + t * (vb - va);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (a.yIndependent() && b.yIndependent() && factor.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (a.xzIndependent() && b.xzIndependent() && factor.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        float[] fl = ColumnPool.take(nb), fh = ColumnPool.take(nb), bl = ColumnPool.take(nb), bh = ColumnPool.take(nb);
        factor.boundsSlabs(x, z, minY, maxY, bs, fl, fh);
        a.boundsSlabs(x, z, minY, maxY, bs, lo, hi);
        b.boundsSlabs(x, z, minY, maxY, bs, bl, bh);
        for (int k = 0; k < nb; k++) {
            float tl = Math.clamp(fl[k], 0f, 1f), th = Math.clamp(fh[k], 0f, 1f);
            if (Float.isNaN(fl[k]) || Float.isNaN(fh[k])) { tl = 0f; th = 1f; }
            if (th <= 0f) continue;                                         // a alone
            if (tl >= 1f) { lo[k] = bl[k]; hi[k] = bh[k]; continue; }       // b alone
            float l = Math.min(Bounds.lerp(lo[k], bl[k], tl), Bounds.lerp(lo[k], bl[k], th));
            float h = Math.max(Bounds.lerp(hi[k], bh[k], tl), Bounds.lerp(hi[k], bh[k], th));
            lo[k] = Float.isNaN(l) ? Float.NEGATIVE_INFINITY : l;
            hi[k] = Float.isNaN(h) ? Float.POSITIVE_INFINITY : h;
        }
        ColumnPool.give(bh); ColumnPool.give(bl); ColumnPool.give(fh); ColumnPool.give(fl);    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        factor.bounds(x, z, y0, y1, out);
        float tl = Math.clamp(out[0], 0f, 1f), th = Math.clamp(out[1], 0f, 1f);
        if (Float.isNaN(out[0]) || Float.isNaN(out[1])) { tl = 0f; th = 1f; }
        float al, ah, bl = 0f, bh = 0f;
        if (th <= 0f) { a.bounds(x, z, y0, y1, out); return; }
        if (tl >= 1f) { b.bounds(x, z, y0, y1, out); return; }
        a.bounds(x, z, y0, y1, out); al = out[0]; ah = out[1];
        b.bounds(x, z, y0, y1, out); bl = out[0]; bh = out[1];
        // for fixed a and b the result is linear in t, so its extremes over [tl, th] are at the ends
        float lo = Math.min(Bounds.lerp(al, bl, tl), Bounds.lerp(al, bl, th));
        float hi = Math.max(Bounds.lerp(ah, bh, tl), Bounds.lerp(ah, bh, th));
        Bounds.set(out, Float.isNaN(lo) ? Float.NEGATIVE_INFINITY : lo, Float.isNaN(hi) ? Float.POSITIVE_INFINITY : hi);    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        int height = maxY - minY;
        if (factor.yIndependent()) {
            // the blend is the same for the whole column: often it picks one input outright, and the other is never evaluated
            float t = Math.clamp(factor.compute(x, minY, z), 0.0f, 1.0f);
            if (t <= 0.0f) { a.computeColumn(x, z, minY, maxY, out); return; }
            if (t >= 1.0f) { b.computeColumn(x, z, minY, maxY, out); return; }
            float[] bufB = ColumnPool.take(height);
            a.computeColumn(x, z, minY, maxY, out);
            b.computeColumn(x, z, minY, maxY, bufB);
            for (int i = 0; i < height; i++) out[i] += t * (bufB[i] - out[i]);
            ColumnPool.give(bufB);
            return;
        }
        // The blend first: each input is then evaluated only over the heights where it has any weight (a where t < 1, b where t > 0).
        float[] bufF = ColumnPool.take(height);
        factor.computeColumn(x, z, minY, maxY, bufF);
        int aLo = height, aHi = -1, bLo = height, bHi = -1;
        for (int i = 0; i < height; i++) {
            float t = Math.clamp(bufF[i], 0.0f, 1.0f);
            bufF[i] = t;
            if (t < 1.0f) { if (i < aLo) aLo = i; aHi = i; }
            if (t > 0.0f) { if (i < bLo) bLo = i; bHi = i; }
        }
        float[] bufB = null;
        if (aHi >= 0) {
            if (aLo == 0 && aHi == height - 1) a.computeColumn(x, z, minY, maxY, out);
            else {
                float[] tmp = ColumnPool.take(height);
                a.computeColumn(x, z, minY + aLo, minY + aHi + 1, tmp);
                System.arraycopy(tmp, 0, out, aLo, aHi - aLo + 1);
                ColumnPool.give(tmp);
            }
        }
        if (bHi >= 0) {
            bufB = ColumnPool.take(height);
            b.computeColumn(x, z, minY + bLo, minY + bHi + 1, bufB);
        }
        for (int i = 0; i < height; i++) {
            float t = bufF[i];
            if (t <= 0.0f) continue;                                    // out[i] already holds a
            float vb = bufB[i - bLo];
            out[i] = t >= 1.0f ? vb : out[i] + t * (vb - out[i]);
        }
        if (bufB != null) ColumnPool.give(bufB);
        ColumnPool.give(bufF);
    }
}
