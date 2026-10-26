package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

import java.util.Set;

/**
 * Pattern that evaluates to true if the target block is contained in an allowed set of materials.
 */
public final class BlockSetPattern<T> implements Pattern<T> {

    private final Set<T> allowedBlocks;

    public BlockSetPattern(Set<T> allowedBlocks) {
        this.allowedBlocks = allowedBlocks != null ? Set.copyOf(allowedBlocks) : Set.of();
    }

    public Set<T> getAllowedBlocks() {
        return allowedBlocks;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return allowedBlocks.contains(voxelSpace.getBlock(x, y, z));
    }
}
