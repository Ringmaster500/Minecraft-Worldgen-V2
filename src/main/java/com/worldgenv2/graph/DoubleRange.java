package com.worldgenv2.graph;

/**
 * Closed interval [min, max] used for range checking in selectors and filters.
 */
public record DoubleRange(double min, double max) {

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    public static DoubleRange of(double min, double max) {
        return new DoubleRange(Math.min(min, max), Math.max(min, max));
    }

    public static DoubleRange all() {
        return new DoubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
