package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.function.Predicate;

/**
 * Strategy interface probing local voxel space around an anchor coordinate to locate valid surface attachments.
 */
@FunctionalInterface
public interface Scanner {

    /**
     * Scans around the given anchor coordinate in voxel space.
     *
     * @param anchor initial anchor position
     * @param space voxel space to inspect
     * @param solidPredicate predicate identifying solid matter voxels
     * @return list of valid target positions
     */
    <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate);
}
