package com.worldgenv2.density;

/** Small helpers for {@link DensityFunction#bounds}: interval arithmetic that never answers narrower than the truth. */
public final class Bounds {
    private Bounds() {}

    public static final float NEG = Float.NEGATIVE_INFINITY, POS = Float.POSITIVE_INFINITY;

    public static void unknown(float[] out) { out[0] = NEG; out[1] = POS; }

    /** Writes [lo, hi], widening to the whole line when either is NaN. */
    public static void set(float[] out, float lo, float hi) {
        if (Float.isNaN(lo) || Float.isNaN(hi)) { unknown(out); return; }
        out[0] = lo;
        out[1] = hi;
    }

    public static float mulLo(float al, float ah, float bl, float bh) {
        float a = al * bl, b = al * bh, c = ah * bl, d = ah * bh;
        if (Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(c) || Float.isNaN(d)) return NEG;
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    public static float mulHi(float al, float ah, float bl, float bh) {
        float a = al * bl, b = al * bh, c = ah * bl, d = ah * bh;
        if (Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(c) || Float.isNaN(d)) return POS;
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    /** a + t (b - a) for one t in [0, 1], as the evaluation does it (t = 0 gives a, t = 1 gives b). */
    public static float lerp(float a, float b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return a + t * (b - a);
    }
}