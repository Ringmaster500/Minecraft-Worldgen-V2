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
    }

    @Override
    public double evaluate(double input) {
        int n = points.size();
        if (n == 1) {
            return points.get(0).y();
        }

        // Constant before first point
        if (input <= points.get(0).x()) {
            return points.get(0).y();
        }
        // Constant after last point
        if (input >= points.get(n - 1).x()) {
            return points.get(n - 1).y();
        }

        // Binary search for interval
        int low = 0;
        int high = n - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (points.get(mid).x() <= input) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        int idx = Math.max(0, high);
        Point p0 = points.get(idx);
        Point p1 = points.get(Math.min(n - 1, idx + 1));

        double dx = p1.x() - p0.x();
        if (dx <= 1e-9) {
            return p0.y();
        }

        double t = (input - p0.x()) / dx;

        switch (mode) {
            case STEP -> {
                return p0.y();
            }
            case SMOOTH -> {
                // Smooth Hermite interpolation (smoothstep)
                double smoothT = t * t * (3.0 - 2.0 * t);
                return p0.y() + smoothT * (p1.y() - p0.y());
            }
            case LINEAR -> {
                return p0.y() + t * (p1.y() - p0.y());
            }
            default -> {
                return p0.y() + t * (p1.y() - p0.y());
            }
        }
    }

    public List<Point> getPoints() {
        return points;
    }

    public InterpolationMode getMode() {
        return mode;
    }
}
