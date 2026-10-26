package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Pattern that evaluates to true if the target block exactly matches the specified material type.
 */
public final class BlockTypePattern<T> implements Pattern<T> {

    private final T targetBlock;

    public BlockTypePattern(T targetBlock) {
        this.targetBlock = Objects.requireNonNull(targetBlock, "targetBlock");
    }

    public T getTargetBlock() {
        return targetBlock;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return targetBlock.equals(voxelSpace.getBlock(x, y, z));
    }
}
