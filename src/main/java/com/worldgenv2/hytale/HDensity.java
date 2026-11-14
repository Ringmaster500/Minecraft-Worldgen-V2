package com.worldgenv2.hytale;

import com.worldgenv2.curve.Curve;
import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.function.DoubleSupplier;

/** Hytale's Density nodes that the engine lacked or implemented differently, with the semantics of docs.hytale.com/hytale-generator/density. */
public final class HDensity {
    private HDensity() {}

    /** The product of all inputs; stops evaluating as soon as one is 0, so put the cheapest mask first. */
    public static DensityFunction multiplier(List<DensityFunction> in) {
        return (x, y, z) -> {
            float r = 1f;
            for (DensityFunction d : in) { float v = d.compute(x, y, z); if (v == 0f) return 0f; r *= v; }
            return r;
        };
    }

    private static float smoothMax(float a, float b, float k) {
        if (k <= 0f) return Math.max(a, b);
        float h = Math.max(k - Math.abs(a - b), 0f) / k;
        return Math.max(a, b) + h * h * k * 0.25f;
    }

    private static float smoothMin(float a, float b, float k) {
        if (k <= 0f) return Math.min(a, b);
        float h = Math.max(k - Math.abs(a - b), 0f) / k;
        return Math.min(a, b) - h * h * k * 0.25f;
    }

    public static DensityFunction smoothMax(DensityFunction a, DensityFunction b, float range) { return (x, y, z) -> smoothMax(a.compute(x, y, z), b.compute(x, y, z), range); }

    public static DensityFunction smoothMin(DensityFunction a, DensityFunction b, float range) { return (x, y, z) -> smoothMin(a.compute(x, y, z), b.compute(x, y, z), range); }

    /** Like Clamp, but the limits are approached smoothly over {@code range}. */
    public static DensityFunction smoothClamp(DensityFunction in, float wallA, float wallB, float range) {
        float lo = Math.min(wallA, wallB), hi = Math.max(wallA, wallB);
        return (x, y, z) -> smoothMin(smoothMax(in.compute(x, y, z), lo, range), hi, range);
    }

    public static DensityFunction inverter(DensityFunction in) { return (x, y, z) -> -in.compute(x, y, z); }

    /** Stretches (above 1), contracts (below 1) or flips (below 0) the input field per axis. */
    public static DensityFunction scale(DensityFunction in, float sx, float sy, float sz) {
        float ax = sx == 0 ? 1 : sx, ay = sy == 0 ? 1 : sy, az = sz == 0 ? 1 : sz;
        return (x, y, z) -> in.compute(x / ax, y / ay, z / az);
    }

    /** Linearly remaps the input range [fromMin, fromMax] to [toMin, toMax]. */
    public static DensityFunction normalizer(DensityFunction in, float fromMin, float fromMax, float toMin, float toMax) {
        float span = fromMax - fromMin;
        return (x, y, z) -> span == 0f ? toMin : toMin + (in.compute(x, y, z) - fromMin) * (toMax - toMin) / span;
    }

    /** Slides the input field along a vector. */
    public static DensityFunction slider(DensityFunction in, float sx, float sy, float sz) { return (x, y, z) -> in.compute(x - sx, y - sy, z - sz); }

    /** Outputs Then where the condition is non-zero, otherwise Else; only the chosen branch is evaluated. */
    public static DensityFunction selector(List<DensityFunction> in) {
        if (in.size() != 3) return (x, y, z) -> 0f;
        DensityFunction then = in.get(0), otherwise = in.get(1), cond = in.get(2);
        return (x, y, z) -> cond.compute(x, y, z) != 0f ? then.compute(x, y, z) : otherwise.compute(x, y, z);
    }

    /** A named decimal constant from the World Structure: its Y (Distance off) or the signed distance to it (Distance on). */
    public static DensityFunction baseHeight(DoubleSupplier height, boolean distance) {
        return (x, y, z) -> distance ? (float) (y - height.getAsDouble()) : (float) height.getAsDouble();
    }

    /** The curve of the distance from the field's origin. */
    public static DensityFunction distance(Curve curve) { return (x, y, z) -> (float) curve.evaluate(Math.sqrt((double) x * x + (double) y * y + (double) z * z)); }

    /** The curve of the distance from an axis line through the origin. */
    public static DensityFunction axis(double ax, double ay, double az, Curve curve) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        double ux = len == 0 ? 0 : ax / len, uy = len == 0 ? 1 : ay / len, uz = len == 0 ? 0 : az / len;
        return (x, y, z) -> {
            double dot = x * ux + y * uy + z * uz;
            double px = x - dot * ux, py = y - dot * uy, pz = z - dot * uz;
            return (float) curve.evaluate(Math.sqrt(px * px + py * py + pz * pz));
        };
    }

    /** Only meaningful inside a PositionsCellNoise; elsewhere it is the largest decimal number, as in Hytale. */
    public static DensityFunction cellWallDistance() { return (x, y, z) -> Float.MAX_VALUE; }
}
