package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adds pseudo-random 3D displacement jitter in all directions.
 */
public final class Jitter3dPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final double magnitude;
    private final long seed;

    public Jitter3dPositionsProvider(PositionsProvider source, double magnitude, long seed) {
        this.source = Objects.requireNonNull(source, "source");
        this.magnitude = Math.max(0.0, magnitude);
        this.seed = seed;
    }

    public Jitter3dPositionsProvider(PositionsProvider source, double magnitude, String seedStr) {
        this(source, magnitude, seedStr != null ? seedStr.hashCode() : 0L);
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (magnitude <= 1e-9) {
            return source.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        }

        List<Vec3d> candidates = source.getPositions(
            minX - magnitude, minY - magnitude, minZ - magnitude,
            maxX + magnitude, maxY + magnitude, maxZ + magnitude
        );

        List<Vec3d> result = new ArrayList<>(candidates.size());
        for (Vec3d p : candidates) {
            long h1 = hash(Double.doubleToLongBits(p.x()) ^ seed, Double.doubleToLongBits(p.y()), Double.doubleToLongBits(p.z()));
            long h2 = hash(h1, seed, Double.doubleToLongBits(p.x()));

            double u1 = (h1 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
            double u2 = (h2 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;

            double theta = 2.0 * Math.PI * u1;
            double cosPhi = 2.0 * u2 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            double dist = Math.cbrt(u1) * magnitude;

            double jx = p.x() + dist * sinPhi * Math.cos(theta);
            double jy = p.y() + dist * sinPhi * Math.sin(theta);
            double jz = p.z() + dist * cosPhi;

            if (jx >= minX && jx <= maxX && jy >= minY && jy <= maxY && jz >= minZ && jz <= maxZ) {
                result.add(new Vec3d(jx, jy, jz));
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

    public double getMagnitude() {
        return magnitude;
    }

    public long getSeed() {
        return seed;
    }
}
