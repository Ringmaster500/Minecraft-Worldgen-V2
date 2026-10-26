package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Vertical anchor scanner.
 * Traces along the Y column within a relative [minYOffset, maxYOffset] search window
 * to find either a valid floor contact (solid at y - 1, air at y) or ceiling contact
 * (solid at y + 1, air at y) before invoking the child Prop.
 */
public final class LocatorProp<T> implements Prop<T> {

    public enum TargetSurface {
        FLOOR,
        CEILING
    }

    private final int minYOffset;
    private final int maxYOffset;
    private final TargetSurface targetSurface;
    private final Prop<T> childProp;

    public LocatorProp(int minYOffset, int maxYOffset, TargetSurface targetSurface, Prop<T> childProp) {
        if (minYOffset > maxYOffset) {
            throw new IllegalArgumentException("minYOffset (" + minYOffset + ") must be <= maxYOffset (" + maxYOffset + ")");
        }
        this.minYOffset = minYOffset;
        this.maxYOffset = maxYOffset;
        this.targetSurface = Objects.requireNonNull(targetSurface, "targetSurface");
        this.childProp = Objects.requireNonNull(childProp, "childProp");
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = context.voxelSpace();
        int x = context.worldX();
        int z = context.worldZ();
        int startY = Math.min(space.getMaxY() - 2, context.worldY() + maxYOffset);
        int endY = Math.max(space.getMinY() + 1, context.worldY() + minYOffset);

        for (int y = startY; y >= endY; y--) {
            if (targetSurface == TargetSurface.FLOOR) {
                if (space.isAir(x, y, z) && !space.isAir(x, y - 1, z)) {
                    Context<T> located = new Context<>(
                        x, y, z,
                        context.normalX(), Math.abs(context.normalY()), context.normalZ(),
                        space,
                        context.seed() ^ y
                    );
                    return childProp.generate(located);
                }
            } else {
                if (space.isAir(x, y, z) && !space.isAir(x, y + 1, z)) {
                    Context<T> located = new Context<>(
                        x, y, z,
                        context.normalX(), -Math.abs(context.normalY()), context.normalZ(),
                        space,
                        context.seed() ^ y
                    );
                    return childProp.generate(located);
                }
            }
        }
        return false;
    }
}
