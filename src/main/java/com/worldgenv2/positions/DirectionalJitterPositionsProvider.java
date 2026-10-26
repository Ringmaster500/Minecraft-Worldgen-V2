package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Perturbs points strictly along a specified 3D direction vector.
 */
public final class DirectionalJitterPositionsProvider implements PositionsProvider {

    private final PositionsProvider source;
    private final Vec3d direction;
    private final double magnitude;
    private final boolean bidirectional;
    private final long seed;

    public DirectionalJitterPositionsProvider(
        PositionsProvider source,
        Vec3d direction,
        double magnitude,
        boolean bidirectional,
        long seed
    ) {
        this.source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(direction, "direction");
        this.direction = direction.normalize();
        this.magnitude = Math.max(0.0, magnitude);
        this.bidirectional = bidirectional;
        this.seed = seed;
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (magnitude <= 1e-9 || direction.lengthSquared() <= 1e-12) {
            return source.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        }

        List<Vec3d> candidates = source.getPositions(
            minX - magnitude, minY - magnitude, minZ - magnitude,
            maxX + magnitude, maxY + magnitude, maxZ + magnitude
        );

        List<Vec3d> result = new ArrayList<>(candidates.size());
        for (Vec3d p : candidates) {
            long h = hash(Double.doubleToLongBits(p.x()) ^ seed, Double.doubleToLongBits(p.z()));
            double u = (h & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;

            double displacement = u * magnitude;
            if (bidirectional) {
                displacement = (u * 2.0 - 1.0) * magnitude;
            }

            double jx = p.x() + direction.x() * displacement;
            double jy = p.y() + direction.y() * displacement;
            double jz = p.z() + direction.z() * displacement;

            if (jx >= minX && jx <= maxX && jy >= minY && jy <= maxY && jz >= minZ && jz <= maxZ) {
                result.add(new Vec3d(jx, jy, jz));
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

    public Vec3d getDirection() {
        return direction;
    }

    public double getMagnitude() {
        return magnitude;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public long getSeed() {
        return seed;
    }
}
