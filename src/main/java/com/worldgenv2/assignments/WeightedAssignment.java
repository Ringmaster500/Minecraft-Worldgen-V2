package com.worldgenv2.assignments;

import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic weighted lottery assignment selector over child assignments.
 */
public final class WeightedAssignment<T> implements Assignments<T> {

    public record Entry<T>(Assignments<T> assignment, double weight) {}

    private final List<Entry<T>> entries;
    private final double totalWeight;
    private final double skipChance;
    private final long seed;

    public WeightedAssignment(List<Entry<T>> entries, double skipChance, long seed) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("WeightedAssignment requires at least one entry");
        }
        this.entries = List.copyOf(entries);
        double sum = 0.0;
        for (Entry<T> e : entries) {
            sum += Math.max(0.0, e.weight());
        }
        this.totalWeight = sum;
        this.skipChance = Math.clamp(skipChance, 0.0, 1.0);
        this.seed = seed;
    }

    @Override
    public Prop<T> assign(Vec3d position, long seed) {
        long h = hash(Double.doubleToLongBits(position.x()) ^ this.seed, Double.doubleToLongBits(position.z()));
        double skipRoll = (h & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
        if (skipRoll < skipChance || totalWeight <= 1e-9) {
            return null;
        }

        long h2 = hash(h, seed);
        double roll = ((h2 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL) * totalWeight;

        double accumulated = 0.0;
        for (Entry<T> entry : entries) {
            accumulated += entry.weight();
            if (roll <= accumulated) {
                return entry.assignment().assign(position, seed);
            }
        }
        return entries.getLast().assignment().assign(position, seed);
    }

    private static long hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public List<Entry<T>> getEntries() {
        return entries;
    }

    public double getSkipChance() {
        return skipChance;
    }
}
