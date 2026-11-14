package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Applies discrete cardinal yaw rotations (0°, 90°, 180°, 270°) around the placement anchor.
 * Can use either a fixed rotation or a deterministic coordinate-seeded cardinal rotation.
 */
public final class StaticRotatorProp<T> implements Prop<T> {

    public enum CardinalRotation {
        DEG_0,
        DEG_90,
        DEG_180,
        DEG_270;

        public static CardinalRotation fromQuarterTurns(int turns) {
            return switch (Math.floorMod(turns, 4)) {
                case 0 -> DEG_0;
                case 1 -> DEG_90;
                case 2 -> DEG_180;
                default -> DEG_270;
            };
        }
    }

    private final Prop<T> childProp;
    private final CardinalRotation fixedRotation; // null means random cardinal per seed

    public StaticRotatorProp(Prop<T> childProp) {
        this(childProp, null);
    }

    public StaticRotatorProp(Prop<T> childProp, CardinalRotation fixedRotation) {
        this.childProp = Objects.requireNonNull(childProp, "childProp");
        this.fixedRotation = fixedRotation;
    }

    @Override
    public boolean generate(Context<T> context) {
        CardinalRotation rotation = fixedRotation != null
            ? fixedRotation
            : selectRotation(context.seed(), context.worldX(), context.worldZ());

        if (rotation == CardinalRotation.DEG_0) {
            return childProp.generate(context);
        }

        VoxelSpace<T> rotatedSpace = new CardinalRotatedVoxelSpace<>(
            context.voxelSpace(),
            context.worldX(),
            context.worldZ(),
            rotation
        );

        Context<T> rotatedContext = new Context<>(
            context.worldX(),
            context.worldY(),
            context.worldZ(),
            context.normalX(),
            context.normalY(),
            context.normalZ(),
            rotatedSpace,
            context.seed()
        );
        return childProp.generate(rotatedContext);
    }

    private static CardinalRotation selectRotation(long seed, int x, int z) {
        long h = seed ^ ((long) x * 0xBF58476D1CE4E5B9L) ^ ((long) z * 0x94D049BB133111EBL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        int idx = (int) ((h >>> 32) & 3L);
        return CardinalRotation.fromQuarterTurns(idx);
    }

    private static final class CardinalRotatedVoxelSpace<T> implements VoxelSpace<T> {
        private final VoxelSpace<T> delegate;
        private final int anchorX;
        private final int anchorZ;
        private final CardinalRotation rotation;

        CardinalRotatedVoxelSpace(VoxelSpace<T> delegate, int anchorX, int anchorZ, CardinalRotation rotation) {
            this.delegate = delegate;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
            this.rotation = rotation;
        }

        private int rotX(int x, int z) {
            int dx = x - anchorX;
            int dz = z - anchorZ;
            return anchorX + switch (rotation) {
                case DEG_0 -> dx;
                case DEG_90 -> -dz;
                case DEG_180 -> -dx;
                case DEG_270 -> dz;
            };
        }

        private int rotZ(int x, int z) {
            int dx = x - anchorX;
            int dz = z - anchorZ;
            return anchorZ + switch (rotation) {
                case DEG_0 -> dz;
                case DEG_90 -> dx;
                case DEG_180 -> -dz;
                case DEG_270 -> -dx;
            };
        }

        @Override
        public T getBlock(int x, int y, int z) {
            return delegate.getBlock(rotX(x, z), y, rotZ(x, z));
        }

        @Override
        public void setBlock(int x, int y, int z, T block) {
            delegate.setBlock(rotX(x, z), y, rotZ(x, z), block);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return delegate.isAir(rotX(x, z), y, rotZ(x, z));
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

    @Override
    public int horizontalReach() {
        return childProp.horizontalReach();
    }
}
