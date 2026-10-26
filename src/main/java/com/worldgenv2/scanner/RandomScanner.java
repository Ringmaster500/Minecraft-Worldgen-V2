package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Scans a line of blocks along an axis in a pseudo-random order determined by a seed.
 */
public final class RandomScanner implements Scanner {

    private final LinearScanner.Axis axis;
    private final int minOffset;
    private final int maxOffset;
    private final long seed;
    private final Scanner childScanner;

    public RandomScanner(LinearScanner.Axis axis, int minOffset, int maxOffset, long seed, Scanner childScanner) {
        this.axis = axis != null ? axis : LinearScanner.Axis.Y;
        this.minOffset = minOffset;
        this.maxOffset = maxOffset;
        this.seed = seed;
        this.childScanner = childScanner;
    }

    @Override
    public <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate) {
        int start = Math.min(minOffset, maxOffset);
        int end = Math.max(minOffset, maxOffset);
        List<Integer> offsets = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            offsets.add(i);
        }

        long pointSeed = seed ^ (Double.doubleToLongBits(anchor.x()) * 31 + Double.doubleToLongBits(anchor.z()));
        Collections.shuffle(offsets, new Random(pointSeed));

        int bx = (int) Math.floor(anchor.x());
        int by = (int) Math.floor(anchor.y());
        int bz = (int) Math.floor(anchor.z());

        List<Vec3d> found = new ArrayList<>();
        for (int offset : offsets) {
            int cx = bx + (axis == LinearScanner.Axis.X ? offset : 0);
            int cy = by + (axis == LinearScanner.Axis.Y ? offset : 0);
            int cz = bz + (axis == LinearScanner.Axis.Z ? offset : 0);

            T state = space.getBlock(cx, cy, cz);
            if (state != null && solidPredicate.test(state)) {
                int ox = cx + (axis == LinearScanner.Axis.X ? 1 : 0);
                int oy = cy + (axis == LinearScanner.Axis.Y ? 1 : 0);
                int oz = cz + (axis == LinearScanner.Axis.Z ? 1 : 0);
                T above = space.getBlock(ox, oy, oz);
                if (above == null || !solidPredicate.test(above)) {
                    Vec3d surfacePoint = new Vec3d(cx, cy + 1, cz);
                    if (childScanner != null) {
                        found.addAll(childScanner.scan(surfacePoint, space, solidPredicate));
                    } else {
                        found.add(surfacePoint);
                    }
                    if (!found.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return found;
    }
}
