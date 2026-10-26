package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.mask.BlockMask;
import com.worldgenv2.mask.MaskedVoxelSpace;
import java.util.Objects;

/**
 * Places a single block or vertical column of blocks (such as flora, bushes, or stalagmites)
 * starting at the prop context anchor, with optional {@link BlockMask} filtering.
 */
public final class BlockProp<T> implements Prop<T> {

    private final T block;
    private final int height;
    private final boolean requireAirTarget;
    private final BlockMask<T> mask;

    public BlockProp(T block) {
        this(block, 1, true, null);
    }

    public BlockProp(T block, int height, boolean requireAirTarget) {
        this(block, height, requireAirTarget, null);
    }

    public BlockProp(T block, int height, boolean requireAirTarget, BlockMask<T> mask) {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be >= 1: " + height);
        }
        this.block = Objects.requireNonNull(block, "block");
        this.height = height;
        this.requireAirTarget = requireAirTarget;
        this.mask = mask;
    }

    public T block() {
        return block;
    }

    public int height() {
        return height;
    }

    public boolean requireAirTarget() {
        return requireAirTarget;
    }

    public BlockMask<T> mask() {
        return mask;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = mask != null ? new MaskedVoxelSpace<>(context.voxelSpace(), mask) : context.voxelSpace();
        int x = context.worldX();
        int y = context.worldY();
        int z = context.worldZ();

        if (y < space.getMinY() || y + height > space.getMaxY()) {
            return false;
        }

        if (requireAirTarget) {
            for (int dy = 0; dy < height; dy++) {
                if (!space.isAir(x, y + dy, z)) {
                    return false;
                }
            }
        }

        for (int dy = 0; dy < height; dy++) {
            space.setBlock(x, y + dy, z, block);
        }
        return true;
    }
}
