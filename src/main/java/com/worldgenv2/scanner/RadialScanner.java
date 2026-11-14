package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Radial scanner expanding outward from the anchor from closest to farthest.
 */
public final class RadialScanner implements Scanner {

    private final int maxReach;
    private final Scanner childScanner;

    public RadialScanner(int maxReach) {
        this(maxReach, null);
    }

    public RadialScanner(int maxReach, Scanner childScanner) {
        this.maxReach = Math.max(0, maxReach);
        this.childScanner = childScanner;
    }

    @Override
    public <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate) {
        List<Vec3d> found = new ArrayList<>();
        int bx = (int) Math.floor(anchor.x());
        int by = (int) Math.floor(anchor.y());
        int bz = (int) Math.floor(anchor.z());

        for (int r = 0; r <= maxReach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dy) != r && Math.abs(dz) != r) {
                            continue; // Only inspect outer shell of radius r
                        }
                        int cx = bx + dx;
                        int cy = by + dy;
                        int cz = bz + dz;

                        T state = space.getBlock(cx, cy, cz);
                        if (state != null && solidPredicate.test(state)) {
                            T above = space.getBlock(cx, cy + 1, cz);
                            if (above == null || !solidPredicate.test(above)) {
                                Vec3d pt = new Vec3d(cx, cy + 1, cz);
                                if (childScanner != null) {
                                    found.addAll(childScanner.scan(pt, space, solidPredicate));
                                } else {
                                    found.add(pt);
                                }
                                if (!found.isEmpty()) {
                                    return found; // Return closest match
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    public int getMaxReach() {
        return maxReach;
    }

    @Override
    public int horizontalReach() {
        return maxReach + (childScanner == null ? 0 : childScanner.horizontalReach());
    }
}
