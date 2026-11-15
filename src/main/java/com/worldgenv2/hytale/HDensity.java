package com.worldgenv2.hytale;

import com.worldgenv2.curve.Curve;
import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.function.DoubleSupplier;

/** Hytale's Density nodes that the engine lacked or implemented differently, with the semantics of docs.hytale.com/hytale-generator/density. */
public final class HDensity {
    private HDensity() {}

    // ------------------------------------------------------------------ column-aware building blocks
    // A terrain column is evaluated 300 to 500 voxels at a time. These helpers hand whole columns to the children (which cache the
    // parts that do not depend on Y) instead of asking for one voxel at a time.

    public interface FloatOp1 { float apply(float a); }
    public interface FloatOp2 { float apply(float a, float b); }

    /** A pointwise function of one input. */
    public static DensityFunction pointwise(DensityFunction in, FloatOp1 op) {
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) { return op.apply(in.compute(x, y, z)); }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                in.computeColumn(x, z, minY, maxY, out);
                for (int i = 0, n = maxY - minY; i < n; i++) out[i] = op.apply(out[i]);
            }
        };
    }

    /** A pointwise function of two inputs. */
    public static DensityFunction pointwise(DensityFunction a, DensityFunction b, FloatOp2 op) {
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) { return op.apply(a.compute(x, y, z), b.compute(x, y, z)); }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                int n = maxY - minY;
                float[] tmp = new float[n];
                a.computeColumn(x, z, minY, maxY, out);
                b.computeColumn(x, z, minY, maxY, tmp);
                for (int i = 0; i < n; i++) out[i] = op.apply(out[i], tmp[i]);
            }
        };
    }
    /** The product of all inputs; stops evaluating as soon as one is 0, so put the cheapest mask first. */
    public static DensityFunction multiplier(List<DensityFunction> in) {
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) {
                float r = 1f;
                for (DensityFunction d : in) { float v = d.compute(x, y, z); if (v == 0f) return 0f; r *= v; }
                return r;
            }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                int n = maxY - minY;
                java.util.Arrays.fill(out, 0, n, 1f);
                float[] tmp = new float[n];
                for (DensityFunction d : in) { d.computeColumn(x, z, minY, maxY, tmp); for (int i = 0; i < n; i++) out[i] *= tmp[i]; }
            }
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

    public static DensityFunction smoothMax(DensityFunction a, DensityFunction b, float range) { return pointwise(a, b, (p, q) -> smoothMax(p, q, range)); }

    public static DensityFunction smoothMin(DensityFunction a, DensityFunction b, float range) { return pointwise(a, b, (p, q) -> smoothMin(p, q, range)); }

    /** Like Clamp, but the limits are approached smoothly over {@code range}. */
    public static DensityFunction smoothClamp(DensityFunction in, float wallA, float wallB, float range) {
        float lo = Math.min(wallA, wallB), hi = Math.max(wallA, wallB);
        return pointwise(in, v -> smoothMin(smoothMax(v, lo, range), hi, range));
    }

    public static DensityFunction inverter(DensityFunction in) { return pointwise(in, v -> -v); }

    /** Stretches (above 1), contracts (below 1) or flips (below 0) the input field per axis. */
    public static DensityFunction scale(DensityFunction in, float sx, float sy, float sz) {
        float ax = sx == 0 ? 1 : sx, ay = sy == 0 ? 1 : sy, az = sz == 0 ? 1 : sz;
        return (x, y, z) -> in.compute(x / ax, y / ay, z / az);
    }

    /** Linearly remaps the input range [fromMin, fromMax] to [toMin, toMax]. */
    public static DensityFunction normalizer(DensityFunction in, float fromMin, float fromMax, float toMin, float toMax) {
        float span = fromMax - fromMin;
        return pointwise(in, v -> span == 0f ? toMin : toMin + (v - fromMin) * (toMax - toMin) / span);
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
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) { return distance ? (float) (y - height.getAsDouble()) : (float) height.getAsDouble(); }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                float h = (float) height.getAsDouble();
                for (int i = 0, n = maxY - minY; i < n; i++) out[i] = distance ? minY + i - h : h;
            }
        };
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

    // ------------------------------------------------------------------ anchors and positions cell noise

    private static final ThreadLocal<java.util.ArrayDeque<double[]>> ANCHORS = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final ThreadLocal<double[]> CELL_WALL = ThreadLocal.withInitial(() -> new double[]{Float.MAX_VALUE});

    /** Evaluates the action with {@code anchor} as the contextual anchor (what a Graph node does for the content it carries). */
    public static <R> R withAnchor(double[] anchor, java.util.function.Supplier<R> action) {
        java.util.ArrayDeque<double[]> stack = ANCHORS.get();
        stack.push(anchor);
        try { return action.get(); } finally { stack.pop(); }
    }

    /** Puts the child's origin at the contextual anchor (the core of the cell a PositionsCellNoise is evaluating); Reverse moves it back. */
    public static DensityFunction anchor(DensityFunction in, boolean reverse) {
        return (x, y, z) -> {
            java.util.ArrayDeque<double[]> stack = ANCHORS.get();
            if (reverse) {
                stack.push(new double[]{0, 0, 0});
                try { return in.compute(x, y, z); } finally { stack.pop(); }
            }
            double[] a = stack.peek();
            return a == null ? in.compute(x, y, z) : in.compute((float) (x - a[0]), (float) (y - a[1]), (float) (z - a[2]));
        };
    }

    /** Distance to the nearest cell wall; 0 at the wall. Only meaningful inside a PositionsCellNoise with the Density return type, elsewhere it is the largest decimal number. */
    public static DensityFunction cellWallDistance() { return (x, y, z) -> (float) CELL_WALL.get()[0]; }

    /** How a PositionsCellNoise turns the cell it is in into a value. */
    public interface ReturnType {
        float value(CellHit hit, double maxDistance, float x, float y, float z);
    }

    /** The nearest two positions and their distances. */
    public record CellHit(double[] core, double d1, double d2) {}

    public record Delimiter(double min, double max, DensityFunction density) {}

    /** The traditional cell noise outputs on distances normalised by MaxDistance. */
    public static ReturnType distanceReturn(String kind) {
        return (h, max, x, y, z) -> {
            double n1 = Math.min(1.0, h.d1() / max), n2 = Math.min(1.0, h.d2() / max);
            return (float) switch (kind) {
                case "Distance2" -> 2 * n2 - 1;
                case "Distance2Add" -> (n1 + n2) - 1;
                case "Distance2Sub" -> 2 * (n2 - n1) - 1;
                case "Distance2Mul" -> n1 * n2 * 2 - 1;
                case "Distance2Div" -> n2 == 0 ? -1 : n1 / n2 * 2 - 1;
                default -> 2 * n1 - 1;
            };
        };
    }

    public static ReturnType cellValueReturn(DensityFunction density, double defaultValue) {
        return (h, max, x, y, z) -> h.d1() > max ? (float) defaultValue : density.compute((float) h.core()[0], (float) h.core()[1], (float) h.core()[2]);
    }

    public static ReturnType curveReturn(Curve curve) {
        return (h, max, x, y, z) -> (float) curve.evaluate(Math.min(h.d1(), max));
    }

    /** The first delimiter containing ChoiceDensity (sampled at the cell core) supplies the cell's density, anchored at the core. */
    public static ReturnType densityReturn(DensityFunction choice, List<Delimiter> delimiters, double defaultValue) {
        return (h, max, x, y, z) -> {
            if (h.d1() > max) return (float) defaultValue;
            float c = choice.compute((float) h.core()[0], (float) h.core()[1], (float) h.core()[2]);
            for (Delimiter d : delimiters) {
                if (c >= Math.min(d.min(), d.max()) && c <= Math.max(d.min(), d.max())) {
                    java.util.ArrayDeque<double[]> stack = ANCHORS.get();
                    double[] wall = CELL_WALL.get();
                    double before = wall[0];
                    stack.push(h.core());
                    wall[0] = Math.max(0.0, (h.d2() - h.d1()) / 2.0);
                    try { return d.density().compute(x, y, z); } finally { stack.pop(); wall[0] = before; }
                }
            }
            return (float) defaultValue;
        };
    }

    public static DensityFunction positionsCellNoise(com.worldgenv2.positions.PositionsProvider positions, ReturnType rt, boolean manhattan, double maxDistance) {
        double max = Math.max(1e-6, maxDistance);
        return (x, y, z) -> {
            double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
            double[] core = null;
            for (com.worldgenv2.vector.Vec3d p : positions.getPositions(x - max, y - max, z - max, x + max, y + max, z + max)) {
                double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
                double d = manhattan ? Math.abs(dx) + Math.abs(dy) + Math.abs(dz) : Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < d1) { d2 = d1; d1 = d; core = new double[]{p.x(), p.y(), p.z()}; }
                else if (d < d2) d2 = d;
            }
            if (core == null) return rt.value(new CellHit(new double[]{x, y, z}, max * 2, max * 2), max, x, y, z);
            return rt.value(new CellHit(core, d1, d2 == Double.MAX_VALUE ? max * 2 : d2), max, x, y, z);
        };
    }
    // ------------------------------------------------------------------ shapes, rotation, warps, switches

    /** Maps a world point into a shape's own space: aligns {@code newYAxis} with +Y, then spins around it by {@code spinDeg} degrees. */
    public record Frame(double ux, double uy, double uz, double vx, double vy, double vz, double wx, double wy, double wz, double cos, double sin) {
        public static Frame of(double ax, double ay, double az, double spinDeg) {
            double len = Math.sqrt(ax * ax + ay * ay + az * az);
            double vx = 0, vy = 1, vz = 0;
            if (len > 1e-9) { vx = ax / len; vy = ay / len; vz = az / len; }
            // u: world X projected onto the plane perpendicular to v (world Z when v is along X)
            double dx = 1 - vx * vx, dy = -vx * vy, dz = -vx * vz;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl < 1e-6) { dx = -vz * vx; dy = -vz * vy; dz = 1 - vz * vz; dl = Math.sqrt(dx * dx + dy * dy + dz * dz); }
            double ux = dx / dl, uy = dy / dl, uz = dz / dl;
            double wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;   // w = u x v
            double r = Math.toRadians(spinDeg);
            return new Frame(ux, uy, uz, vx, vy, vz, wx, wy, wz, Math.cos(r), Math.sin(r));
        }

        /** The point in shape space: {a, b, c} with b along the (new) Y axis. */
        public double[] apply(double x, double y, double z) {
            double a = x * ux + y * uy + z * uz, b = x * vx + y * vy + z * vz, c = x * wx + y * wy + z * wz;
            return new double[]{a * cos - c * sin, b, a * sin + c * cos};
        }
    }

    public static DensityFunction ellipsoid(Curve curve, double sx, double sy, double sz, Frame f) {
        return (x, y, z) -> {
            double[] p = f.apply(x, y, z);
            double a = p[0] / sx, b = p[1] / sy, c = p[2] / sz;
            return (float) curve.evaluate(Math.sqrt(a * a + b * b + c * c));
        };
    }

    public static DensityFunction cuboid(Curve curve, double sx, double sy, double sz, Frame f) {
        return (x, y, z) -> {
            double[] p = f.apply(x, y, z);
            return (float) curve.evaluate(Math.max(Math.abs(p[0] / sx), Math.max(Math.abs(p[1] / sy), Math.abs(p[2] / sz))));
        };
    }

    /** AxialCurve reads the signed distance above (positive) or below the cylinder's XZ plane, RadialCurve the distance from its Y axis; the results multiply. */
    public static DensityFunction cylinder(Curve axial, Curve radial, Frame f) {
        return (x, y, z) -> {
            double[] p = f.apply(x, y, z);
            return (float) (axial.evaluate(p[1]) * radial.evaluate(Math.sqrt(p[0] * p[0] + p[2] * p[2])));
        };
    }

    /** The input field with its Y axis aligned to NewYAxis and spun around it. */
    public static DensityFunction rotator(DensityFunction in, Frame f) {
        return (x, y, z) -> { double[] p = f.apply(x, y, z); return in.compute((float) p[0], (float) p[1], (float) p[2]); };
    }

    public enum TrigFn { SIN, COS, TAN, ASIN, ACOS, ATAN }

    /** The function of (input times InputScale, in radians); always finite (Asin/Acos clamp the input, Tan clamps near its asymptotes). */
    public static DensityFunction trig(DensityFunction in, TrigFn fn, double inputScale) {
        return (x, y, z) -> {
            double v = in.compute(x, y, z) * inputScale;
            double r = switch (fn) {
                case SIN -> Math.sin(v);
                case COS -> Math.cos(v);
                case TAN -> Math.max(-1e4, Math.min(1e4, Math.tan(v)));
                case ASIN -> Math.asin(Math.max(-1.0, Math.min(1.0, v)));
                case ACOS -> Math.acos(Math.max(-1.0, Math.min(1.0, v)));
                case ATAN -> Math.atan(v);
            };
            return (float) r;
        };
    }

    /** Warps the first input along WarpVector by (second input's value times WarpFactor) blocks. */
    public static DensityFunction vectorWarp(DensityFunction in, DensityFunction warp, double factor, double wx, double wy, double wz) {
        return (x, y, z) -> {
            float k = (float) (warp.compute(x, y, z) * factor);
            return in.compute(x + (float) wx * k, y + (float) wy * k, z + (float) wz * k);
        };
    }

    /** Warps the first input by the gradient of the second (central differences SampleRange apart), scaled by WarpFactor. */
    public static DensityFunction gradientWarp(DensityFunction in, DensityFunction source, double sampleRange, double factor, boolean twoD, double yFor2D) {
        return (x, y, z) -> {
            float sy = twoD ? (float) yFor2D : y;
            float h = (float) Math.max(1e-3, sampleRange);
            float gx = (source.compute(x + h, sy, z) - source.compute(x - h, sy, z)) / (2 * h);
            float gz = (source.compute(x, sy, z + h) - source.compute(x, sy, z - h)) / (2 * h);
            float gy = twoD ? 0f : (source.compute(x, sy + h, z) - source.compute(x, sy - h, z)) / (2 * h);
            return in.compute(x + gx * (float) factor, sy + gy * (float) factor, z + gz * (float) factor);
        };
    }

    /** Warps the input by up to WarpFactor blocks using an internal simplex field per axis. */
    public static DensityFunction fastGradientWarp(DensityFunction in, double scale, double lacunarity, double persistence, int octaves, double factor, int seed) {
        com.worldgenv2.density.FastNoiseLite[] n = new com.worldgenv2.density.FastNoiseLite[3];
        for (int i = 0; i < 3; i++) {
            n[i] = new com.worldgenv2.density.FastNoiseLite(seed + i * 7919);
            n[i].setNoiseType(com.worldgenv2.density.FastNoiseLite.NoiseType.OpenSimplex2);
            n[i].setFrequency((float) (1.0 / Math.max(1e-3, scale)));
            n[i].setFractalType(com.worldgenv2.density.FastNoiseLite.FractalType.FBm);
            n[i].setFractalOctaves(Math.max(1, octaves));
            n[i].setFractalLacunarity((float) lacunarity);
            n[i].setFractalGain((float) persistence);
        }
        return (x, y, z) -> in.compute(x + n[0].getNoise(x, y, z) * (float) factor, y + n[1].getNoise(x, y, z) * (float) factor, z + n[2].getNoise(x, y, z) * (float) factor);
    }

    // SwitchState: a string placed in the context of a density tree; Switch picks the branch that matches it ("Default" when unset)
    private static final ThreadLocal<String> SWITCH_STATE = ThreadLocal.withInitial(() -> "Default");

    public static DensityFunction switchState(DensityFunction in, String state) {
        return (x, y, z) -> {
            String before = SWITCH_STATE.get();
            SWITCH_STATE.set(state);
            try { return in.compute(x, y, z); } finally { SWITCH_STATE.set(before); }
        };
    }

    public static DensityFunction switchCases(java.util.Map<String, DensityFunction> cases) {
        return (x, y, z) -> {
            DensityFunction d = cases.get(SWITCH_STATE.get());
            if (d == null) d = cases.get("Default");
            return d == null ? 0f : d.compute(x, y, z);
        };
    }

    // ------------------------------------------------------------------ Y-independent caching

    private static final class Memo {
        final float[] xs = new float[8], zs = new float[8], vs = new float[8];
        int next;
        Memo() { java.util.Arrays.fill(xs, Float.NaN); }
    }

    /**
     * The input seen at a fixed height ({@code YOverride}, {@code Cache2D}): the value depends only on x and z, so a column of voxels
     * costs one evaluation. The last few columns are remembered per thread, which is what makes Hytale's deliberately placed
     * caches pay off.
     */
    public static DensityFunction atFixedY(DensityFunction in, float fixedY) {
        ThreadLocal<Memo> memo = ThreadLocal.withInitial(Memo::new);
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) {
                Memo m = memo.get();
                for (int i = 0; i < 8; i++) if (m.xs[i] == x && m.zs[i] == z) return m.vs[i];
                float v = in.compute(x, fixedY, z);
                int k = m.next++ & 7;
                m.xs[k] = x; m.zs[k] = z; m.vs[k] = v;
                return v;
            }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                float v = compute(x, 0f, z);
                java.util.Arrays.fill(out, 0, maxY - minY, v);
            }
        };
    }

    /** The input with one coordinate replaced by a constant (XOverride, ZOverride; Y goes through {@link #atFixedY}). */
    public static DensityFunction overrideX(DensityFunction in, float value) { return (x, y, z) -> in.compute(value, y, z); }

    public static DensityFunction overrideZ(DensityFunction in, float value) { return (x, y, z) -> in.compute(x, y, value); }

    /**
     * Hytale's YSampled: the input is evaluated only every {@code distance} blocks along Y (at heights on a world-fixed grid,
     * shifted by {@code offset}) and the blocks between are interpolated, which cuts the cost of a 3D field by that factor.
     */
    public static DensityFunction ySampled(DensityFunction in, double distance, double offset, boolean interpolate) {
        double d = Math.max(1.0, distance);
        return new DensityFunction() {
            @Override public float compute(float x, float y, float z) {
                double g = (y - offset) / d;
                int j = (int) Math.floor(g);
                float a = in.compute(x, (float) (j * d + offset), z);
                if (!interpolate) return a;
                float t = (float) (g - j);
                if (t == 0f) return a;
                return a + (in.compute(x, (float) ((j + 1) * d + offset), z) - a) * t;
            }
            @Override public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                int n = maxY - minY;
                int j0 = (int) Math.floor((minY - offset) / d), j1 = (int) Math.floor((maxY - 1 - offset) / d) + 1;
                float[] s = new float[j1 - j0 + 1];
                for (int j = j0; j <= j1; j++) s[j - j0] = in.compute(x, (float) (j * d + offset), z);
                for (int i = 0; i < n; i++) {
                    double g = (minY + i - offset) / d;
                    int j = (int) Math.floor(g);
                    float a = s[j - j0];
                    out[i] = interpolate && g != j ? a + (s[j + 1 - j0] - a) * (float) (g - j) : a;
                }
            }
        };
    }
}