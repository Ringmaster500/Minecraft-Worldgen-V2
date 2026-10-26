package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;

/**
 * Single-pass contextual prop generator.
 * Supersedes the legacy 2-phase scan()/place() system.
 */
@FunctionalInterface
public interface Prop<T> {

    record Context<T>(
        int worldX,
        int worldY,
        int worldZ,
        float normalX,
        float normalY,
        float normalZ,
        VoxelSpace<T> voxelSpace,
        long seed
    ) {}

    /**
     * Attempts to place the prop at the given context.
     * 
     * @param context Placement context including surface coordinates, normal vector, and target voxel buffer
     * @return true if successfully generated, false if aborted
     */
    boolean generate(Context<T> context);
}
