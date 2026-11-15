package com.worldgenv2.curve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Keyframe curve connecting a sequence of (x, y) control points.
 * <p>
 * The output is constant before the first control point and after the last control point.
 * Between control points, values are interpolated linearly or via smooth Hermite spline.
 */
public final class ManualCurve implements Curve {

    public enum InterpolationMode {
        LINEAR,
        STEP,
        SMOOTH
    }

    public record Point(double x, double y) implements Comparable<Point> {
        @Override
        public int compareTo(Point o) {
            return Double.compare(this.x, o.x);
        }
    }

    private final List<Point> points;
    private final InterpolationMode mode;
    private final double[] xs, ys;

    public ManualCurve(List<Point> inputPoints) {
        this(inputPoints, InterpolationMode.LINEAR);
    }

    public ManualCurve(List<Point> inputPoints, InterpolationMode mode) {
        Objects.requireNonNull(inputPoints, "inputPoints");
        this.mode = mode != null ? mode : InterpolationMode.LINEAR;
        if (inputPoints.isEmpty()) {
            this.points = List.of(new Point(0.0, 0.0));
        } else {
            List<Point> sorted = new ArrayList<>(inputPoints);
            sorted.sort(Comparator.naturalOrder());
            this.points = List.copyOf(sorted);
        }
        this.xs = new double[points.size()];
        this.ys = new double[points.size()];
        for (int i = 0; i < xs.length; i++) { xs[i] = points.get(i).x(); ys[i] = points.get(i).y(); }
    }

    @Override
    public double evaluate(double input) {
        final double[] xs = this.xs, ys = this.ys;
        int n = xs.length;
        if (n == 1 || input <= xs[0]) return ys[0];
        if (input >= xs[n - 1]) return ys[n - 1];

        // last point whose x is <= input
        int low = 0, high = n - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (xs[mid] <= input) low = mid + 1; else high = mid - 1;
        }
        int idx = Math.max(0, high);
        int next = Math.min(n - 1, idx + 1);
        double dx = xs[next] - xs[idx];
        if (dx <= 1e-9) return ys[idx];
        double t = (input - xs[idx]) / dx;
        switch (mode) {
            case STEP: return ys[idx];
            case SMOOTH: return ys[idx] + (t * t * (3.0 - 2.0 * t)) * (ys[next] - ys[idx]);
            default: return ys[idx] + t * (ys[next] - ys[idx]);
        }
    }
    @Override
    public double[] range(double lo, double hi) {
        if (Double.isNaN(lo) || Double.isNaN(hi)) return null;
        // piecewise linear (or smoothstep, which stays between its end points): the extremes are at the ends or at points inside
        double a = evaluate(lo), b = evaluate(hi);
        double mn = Math.min(a, b), mx = Math.max(a, b);
        for (int i = 0; i < xs.length; i++) if (xs[i] > lo && xs[i] < hi) { mn = Math.min(mn, ys[i]); mx = Math.max(mx, ys[i]); }
        return new double[]{mn, mx};
    }

    public List<Point> getPoints() {
        return points;
    }

    public InterpolationMode getMode() {
        return mode;
    }
}
