package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A prop that is a field of positioned props rather than a single placement: the engine asks it to evaluate every position in
 * a region (so anchors near a chunk border are decided identically by both chunks) instead of calling it once per column.
 */
public interface Distribution<T> extends Prop<T> {

    /**
     * Evaluates every position of this distribution inside [minX, maxX) x [minZ, maxZ), in a fixed global order, with seeds that
     * depend only on {@code seedBase} and the position. {@code accept} says whether the distribution owns a column (its biome).
     *
     * @return how many props were placed
     */
    int generateInRegion(int minX, int minZ, int maxX, int maxZ, VoxelSpace<T> space, long seedBase,
                         BiPredicate<Integer, Integer> accept, Predicate<T> solid);

    /**
     * Like the method above, when only blocks inside [needMinX, needMaxX) x [needMinZ, needMaxZ) will ever be read. A position whose
     * prop cannot build anything in that rectangle (its own reach is smaller than the distribution's worst case) may be skipped.
     * Implementations that cannot tell just ignore the rectangle.
     */
    default int generateInRegion(int minX, int minZ, int maxX, int maxZ, int needMinX, int needMinZ, int needMaxX, int needMaxZ,
                                 VoxelSpace<T> space, long seedBase, BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
        return generateInRegion(minX, minZ, maxX, maxZ, space, seedBase, accept, solid);
    }
}
