package com.worldgenv2.mask;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Decorates a {@link VoxelSpace} by filtering all voxel modifications through a {@link BlockMask}.
 */
public final class MaskedVoxelSpace<T> implements VoxelSpace<T> {

    private final VoxelSpace<T> delegate;
    private final BlockMask<T> mask;

    public MaskedVoxelSpace(VoxelSpace<T> delegate, BlockMask<T> mask) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.mask = mask != null ? mask : BlockMask.always();
    }

    public VoxelSpace<T> delegate() {
        return delegate;
    }

    public BlockMask<T> mask() {
        return mask;
    }

    @Override
    public T getBlock(int x, int y, int z) {
        return delegate.getBlock(x, y, z);
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        T destination = delegate.getBlock(x, y, z);
        if (mask.canPlace(block, destination)) {
            delegate.setBlock(x, y, z, block);
        }
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        return delegate.isAir(x, y, z);
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getMaxY() {
        return delegate.getMaxY();
    }
}
