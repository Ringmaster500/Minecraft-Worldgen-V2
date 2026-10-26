package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Selects among a palette of materials or child MaterialProviders according to statistical weights,
 * using deterministic stateless 3D coordinate bit-mixing.
 */
public final class WeightedMaterialProvider<T> implements MaterialProvider<T> {

    public record Entry<T>(MaterialProvider<T> provider, float weight) {
        public Entry {
            Objects.requireNonNull(provider, "provider");
            if (weight < 0.0f || Float.isNaN(weight)) {
                throw new IllegalArgumentException("weight must be non-negative: " + weight);
            }
        }

        public static <T> Entry<T> ofMaterial(T material, float weight) {
            return new Entry<>(new ConstantMaterialProvider<>(material), weight);
        }
    }

    private final List<Entry<T>> entries;
    private final float totalWeight;
    private final long seed;

    public WeightedMaterialProvider(List<Entry<T>> entries) {
        this(entries, 0x9E3779B97F4A7C15L);
    }

    public WeightedMaterialProvider(List<Entry<T>> entries, long seed) {
        List<Entry<T>> filtered = new ArrayList<>();
        float sum = 0.0f;
        for (Entry<T> entry : entries) {
            if (entry.weight() > 0.0f) {
                filtered.add(entry);
                sum += entry.weight();
            }
        }
        if (filtered.isEmpty() || sum <= 0.0f) {
            throw new IllegalArgumentException("WeightedMaterialProvider requires at least one entry with positive weight");
        }
        this.entries = List.copyOf(filtered);
        this.totalWeight = sum;
        this.seed = seed;
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    public float totalWeight() {
        return totalWeight;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        if (entries.size() == 1) {
            return entries.getFirst().provider().getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
        }
        float target = hashToUnitFloat(x, y, z, seed) * totalWeight;
        float cumulative = 0.0f;
        for (int i = 0, size = entries.size(); i < size; i++) {
            Entry<T> entry = entries.get(i);
            cumulative += entry.weight();
            if (target <= cumulative) {
                return entry.provider().getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
            }
        }
        return entries.getLast().provider().getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
    }

    private static float hashToUnitFloat(int x, int y, int z, long seed) {
        long h = seed;
        h ^= (long) x * 0xBF58476D1CE4E5B9L;
        h ^= (long) y * 0x94D049BB133111EBL;
        h ^= (long) z * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return ((h >>> 40) & 0xFFFFFF) / (float) (1 << 24);
    }
}
