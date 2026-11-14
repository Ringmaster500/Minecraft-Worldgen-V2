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

    /**
     * How far, horizontally, this prop can reach from its anchor: what it scans, what it tests and what it builds. The prop
     * runtime sums these to size the terrain context a chunk needs, so props near a chunk edge see the real world.
     */
    default int horizontalReach() {
        return 0;
    }

    /** The part of {@link #horizontalReach()} that is content the prop builds (the rest is what it searches). Defaults to all of it. */
    default int contentReach() {
        return horizontalReach();
    }
}
