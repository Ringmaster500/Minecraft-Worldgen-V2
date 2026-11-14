package com.worldgenv2.props;

import java.util.Objects;

/**
 * Offsets the placement coordinates of the child Prop.
 */
public final class OffsetProp<T> implements Prop<T> {

    private final Prop<T> childProp;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    public OffsetProp(Prop<T> childProp, int offsetX, int offsetY, int offsetZ) {
        this.childProp = Objects.requireNonNull(childProp, "childProp");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public Prop<T> childProp() {
        return childProp;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public int offsetZ() {
        return offsetZ;
    }

    @Override
    public boolean generate(Context<T> context) {
        Context<T> offsetContext = new Context<>(
            context.worldX() + offsetX,
            context.worldY() + offsetY,
            context.worldZ() + offsetZ,
            context.normalX(),
            context.normalY(),
            context.normalZ(),
            context.voxelSpace(),
            context.seed()
        );
        return childProp.generate(offsetContext);
    }

    @Override
    public int horizontalReach() {
        return Math.max(Math.abs(offsetX), Math.abs(offsetZ)) + childProp.horizontalReach();
    }
}
