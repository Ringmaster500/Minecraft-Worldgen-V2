package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adds pseudo-random displacement jitter along the horizontal XZ plane.
 */
public final class Jitter2dPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final double magnitude;
    private final long seed;

    public Jitter2dPositionsProvider(PositionsProvider source, double magnitude, long seed) {
        this.source = Objects.requireNonNull(source, "source");
        this.magnitude = Math.max(0.0, magnitude);
        this.seed = seed;
    }

    public Jitter2dPositionsProvider(PositionsProvider source, double magnitude, String seedStr) {
        this(source, magnitude, seedStr != null ? seedStr.hashCode() : 0L);
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (magnitude <= 1e-9) {
            return source.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        }

        // Query expanded region to catch points jittered into the window
        List<Vec3d> candidates = source.getPositions(
            minX - magnitude, minY, minZ - magnitude,
            maxX + magnitude, maxY, maxZ + magnitude
        );

        List<Vec3d> result = new ArrayList<>(candidates.size());
        for (Vec3d p : candidates) {
            long h1 = hash(Double.doubleToLongBits(p.x()) ^ seed, Double.doubleToLongBits(p.z()));
            long h2 = hash(h1, seed);

            double uAngle = (h1 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
            double uDist = (h2 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;

            double angle = 2.0 * Math.PI * uAngle;
            double dist = Math.sqrt(uDist) * magnitude;

            double jx = p.x() + dist * Math.cos(angle);
            double jz = p.z() + dist * Math.sin(angle);

            if (jx >= minX && jx <= maxX && p.y() >= minY && p.y() <= maxY && jz >= minZ && jz <= maxZ) {
                result.add(new Vec3d(jx, p.y(), jz));
            }
        }
        return result;
    }

    private static long hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public PositionsProvider getSource() {
        return source;
    }

    public double getMagnitude() {
        return magnitude;
    }

    public long getSeed() {
        return seed;
    }
}
