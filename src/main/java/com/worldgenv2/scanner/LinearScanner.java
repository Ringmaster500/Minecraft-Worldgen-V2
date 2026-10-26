package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Scans linearly block by block along an axis searching for valid surface transitions.
 */
public final class LinearScanner implements Scanner {

    public enum Axis {
        X, Y, Z
    }

    private final Axis axis;
    private final int minOffset;
    private final int maxOffset;
    private final Scanner childScanner;

    public LinearScanner(Axis axis, int minOffset, int maxOffset) {
        this(axis, minOffset, maxOffset, null);
    }

    public LinearScanner(Axis axis, int minOffset, int maxOffset, Scanner childScanner) {
        this.axis = axis != null ? axis : Axis.Y;
        this.minOffset = minOffset;
        this.maxOffset = maxOffset;
        this.childScanner = childScanner;
    }

    @Override
    public <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate) {
        List<Vec3d> found = new ArrayList<>();
        int bx = (int) Math.floor(anchor.x());
        int by = (int) Math.floor(anchor.y());
        int bz = (int) Math.floor(anchor.z());

        int start = Math.min(minOffset, maxOffset);
        int end = Math.max(minOffset, maxOffset);

        for (int offset = start; offset <= end; offset++) {
            int cx = bx + (axis == Axis.X ? offset : 0);
            int cy = by + (axis == Axis.Y ? offset : 0);
            int cz = bz + (axis == Axis.Z ? offset : 0);

            T state = space.getBlock(cx, cy, cz);
            if (state != null && solidPredicate.test(state)) {
                // Check if adjacent space along axis is open / air
                int ox = cx + (axis == Axis.X ? 1 : 0);
                int oy = cy + (axis == Axis.Y ? 1 : 0);
                int oz = cz + (axis == Axis.Z ? 1 : 0);
                T above = space.getBlock(ox, oy, oz);
                if (above == null || !solidPredicate.test(above)) {
                    Vec3d surfacePoint = new Vec3d(cx, cy + 1, cz);
                    if (childScanner != null) {
                        found.addAll(childScanner.scan(surfacePoint, space, solidPredicate));
                    } else {
                        found.add(surfacePoint);
                    }
                }
            }
        }
        return found;
    }

    public Axis getAxis() {
        return axis;
    }

    public int getMinOffset() {
        return minOffset;
    }

    public int getMaxOffset() {
        return maxOffset;
    }

    public Scanner getChildScanner() {
        return childScanner;
    }
}
