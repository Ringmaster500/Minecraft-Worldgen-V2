package com.worldgenv2.props;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Weighted lottery selector over a collection of child Props.
 * Uses deterministic seed bit-mixing from the Prop.Context to select a candidate prop.
 */
public final class WeightedProp<T> implements Prop<T> {

    public record Entry<T>(Prop<T> prop, float weight) {
        public Entry {
            Objects.requireNonNull(prop, "prop");
            if (weight < 0.0f || Float.isNaN(weight)) {
                throw new IllegalArgumentException("weight must be non-negative: " + weight);
            }
        }
    }

    private final List<Entry<T>> entries;
    private final float totalWeight;

    public WeightedProp(List<Entry<T>> entries) {
        List<Entry<T>> valid = new ArrayList<>();
        float sum = 0.0f;
        for (Entry<T> entry : entries) {
            if (entry.weight() > 0.0f) {
                valid.add(entry);
                sum += entry.weight();
            }
        }
        if (valid.isEmpty() || sum <= 0.0f) {
            throw new IllegalArgumentException("WeightedProp requires at least one entry with positive weight");
        }
        this.entries = List.copyOf(valid);
        this.totalWeight = sum;
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    @Override
    public boolean generate(Context<T> context) {
        if (entries.size() == 1) {
            return entries.getFirst().prop().generate(context);
        }
        float roll = seedToUnitFloat(context.seed(), context.worldX(), context.worldY(), context.worldZ()) * totalWeight;
        float cumulative = 0.0f;
        for (int i = 0, size = entries.size(); i < size; i++) {
            Entry<T> entry = entries.get(i);
            cumulative += entry.weight();
            if (roll <= cumulative) {
                return entry.prop().generate(context);
            }
        }
        return entries.getLast().prop().generate(context);
    }

    private static float seedToUnitFloat(long seed, int x, int y, int z) {
        long h = seed ^ ((long) x * 0xBF58476D1CE4E5B9L)
            ^ ((long) y * 0x94D049BB133111EBL)
            ^ ((long) z * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return ((h >>> 40) & 0xFFFFFF) / (float) (1 << 24);
    }

    @Override
    public int horizontalReach() {
        int r = 0;
        for (Entry<T> e : entries) r = Math.max(r, e.prop().horizontalReach());
        return r;
    }
}
