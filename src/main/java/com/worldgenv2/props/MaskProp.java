package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.mask.BlockMask;
import com.worldgenv2.mask.MaskedVoxelSpace;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Surface slope, ground substrate, and voxel block masking filter.
 * <p>
 * Ensures child Props only generate on valid terrain substrates within allowable slope angles,
 * and restricts which blocks the child is allowed to place or replace via {@link BlockMask}.
 */
public final class MaskProp<T> implements Prop<T> {

    private final Predicate<T> groundFilter;
    private final float minSlopeDeg;
    private final float maxSlopeDeg;
    private final BlockMask<T> blockMask;
    private final Prop<T> childProp;

    public MaskProp(BlockMask<T> blockMask, Prop<T> childProp) {
        this(null, 0.0f, 90.0f, blockMask, childProp);
    }

    public MaskProp(Set<T> allowedGroundBlocks, float maxSlopeDeg, Prop<T> childProp) {
        this(allowedGroundBlocks != null ? allowedGroundBlocks::contains : null, 0.0f, maxSlopeDeg, null, childProp);
    }

    public MaskProp(Predicate<T> groundFilter, float minSlopeDeg, float maxSlopeDeg, Prop<T> childProp) {
        this(groundFilter, minSlopeDeg, maxSlopeDeg, null, childProp);
    }

    public MaskProp(
        Predicate<T> groundFilter,
        float minSlopeDeg,
        float maxSlopeDeg,
        BlockMask<T> blockMask,
        Prop<T> childProp
    ) {
        if (minSlopeDeg > maxSlopeDeg) {
            throw new IllegalArgumentException("minSlopeDeg (" + minSlopeDeg + ") must be <= maxSlopeDeg (" + maxSlopeDeg + ")");
        }
        this.groundFilter = groundFilter;
        this.minSlopeDeg = minSlopeDeg;
        this.maxSlopeDeg = maxSlopeDeg;
        this.blockMask = blockMask;
        this.childProp = Objects.requireNonNull(childProp, "childProp");
    }

    public Predicate<T> groundFilter() {
        return groundFilter;
    }

    public float minSlopeDeg() {
        return minSlopeDeg;
    }

    public float maxSlopeDeg() {
        return maxSlopeDeg;
    }

    public BlockMask<T> blockMask() {
        return blockMask;
    }

    public Prop<T> childProp() {
        return childProp;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = context.voxelSpace();

        if (groundFilter != null) {
            int groundY = context.worldY() - 1;
            if (groundY < space.getMinY() || groundY >= space.getMaxY()) {
                return false;
            }

            // Compute surface slope angle in degrees from normal vector's Y component
            float ny = Math.clamp(context.normalY(), -1.0f, 1.0f);
            float slopeDeg = (float) Math.toDegrees(Math.acos(ny));
            if (slopeDeg < minSlopeDeg || slopeDeg > maxSlopeDeg) {
                return false;
            }

            T groundBlock = space.getBlock(context.worldX(), groundY, context.worldZ());
            if (groundBlock == null || !groundFilter.test(groundBlock)) {
                return false;
            }
        }

        if (blockMask != null) {
            VoxelSpace<T> maskedSpace = new MaskedVoxelSpace<>(space, blockMask);
            Context<T> maskedContext = new Context<>(
                context.worldX(),
                context.worldY(),
                context.worldZ(),
                context.normalX(),
                context.normalY(),
                context.normalZ(),
                maskedSpace,
                context.seed()
            );
            return childProp.generate(maskedContext);
        }

        return childProp.generate(context);
    }
}
