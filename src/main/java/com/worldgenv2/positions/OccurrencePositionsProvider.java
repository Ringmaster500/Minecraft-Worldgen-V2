package com.worldgenv2.positions;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Filters input positions probabilistically based on a {@link DensityFunction}.
 * Density &lt;= 0.0 has a 0% chance of being kept; &gt;= 1.0 has 100% chance;
 * values between 0.0 and 1.0 have a proportional probability chance.
 */
public final class OccurrencePositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final DensityFunction fieldFunction;
    private final long seed;

    public OccurrencePositionsProvider(PositionsProvider source, DensityFunction fieldFunction, long seed) {
        this.source = Objects.requireNonNull(source, "source");
        this.fieldFunction = Objects.requireNonNull(fieldFunction, "fieldFunction");
        this.seed = seed;
    }

    public OccurrencePositionsProvider(PositionsProvider source, DensityFunction fieldFunction, String seedStr) {
        this(source, fieldFunction, seedStr != null ? seedStr.hashCode() : 0L);
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> candidates = source.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        List<Vec3d> result = new ArrayList<>(candidates.size());

        for (Vec3d p : candidates) {
            float density = fieldFunction.compute((float) p.x(), (float) p.y(), (float) p.z());
            if (density <= 0.0f) {
                continue;
            }
            if (density >= 1.0f) {
                result.add(p);
                continue;
            }

            long h = hash(Double.doubleToLongBits(p.x()) ^ seed, Double.doubleToLongBits(p.y()), Double.doubleToLongBits(p.z()));
            double roll = (h & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
            if (roll <= density) {
                result.add(p);
            }
        }
        return result;
    }

    private static long hash(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + c * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public PositionsProvider getSource() {
        return source;
    }

    public DensityFunction getFieldFunction() {
        return fieldFunction;
    }

    public long getSeed() {
        return seed;
    }
}
