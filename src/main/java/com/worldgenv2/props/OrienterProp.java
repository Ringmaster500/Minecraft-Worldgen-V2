package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Aligns a child Prop's local +Y up-vector (0, 1, 0) to the terrain surface normal N = -grad(D).
 * Uses an exact 3D orthogonal rotation matrix wrapper around the anchor coordinate, enabling
 * crystal spires, hillside trees, or cave ceiling formations to grow perpendicular to the surface.
 */
public final class OrienterProp<T> implements Prop<T> {

    private final Prop<T> childProp;
    private final float tiltStrength;

    public OrienterProp(Prop<T> childProp) {
        this(childProp, 1.0f);
    }

    public OrienterProp(Prop<T> childProp, float tiltStrength) {
        this.childProp = Objects.requireNonNull(childProp, "childProp");
        this.tiltStrength = Math.clamp(tiltStrength, 0.0f, 1.0f);
    }

    public Prop<T> childProp() {
        return childProp;
    }

    public float tiltStrength() {
        return tiltStrength;
    }

    @Override
    public boolean generate(Context<T> context) {
        // Blend between vertical (0, 1, 0) and the surface normal according to tiltStrength
        float tx = context.normalX() * tiltStrength;
        float ty = (1.0f - tiltStrength) + context.normalY() * tiltStrength;
        float tz = context.normalZ() * tiltStrength;
        float mag = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (mag < 1e-5f) {
            tx = 0.0f;
            ty = 1.0f;
            tz = 0.0f;
        } else {
            float inv = 1.0f / mag;
            tx *= inv;
            ty *= inv;
            tz *= inv;
        }

        // Fast path if normal is already nearly vertical (+Y)
        if (ty > 0.9995f) {
            return childProp.generate(context);
        }

        VoxelSpace<T> orientedSpace = new NormalOrientedVoxelSpace<>(
            context.voxelSpace(),
            context.worldX(),
            context.worldY(),
            context.worldZ(),
            tx, ty, tz
        );

        Context<T> orientedContext = new Context<>(
            context.worldX(),
            context.worldY(),
            context.worldZ(),
            tx, ty, tz,
            orientedSpace,
            context.seed()
        );
        return childProp.generate(orientedContext);
    }

    private static final class NormalOrientedVoxelSpace<T> implements VoxelSpace<T> {
        private final VoxelSpace<T> delegate;
        private final int anchorX;
        private final int anchorY;
        private final int anchorZ;
        // 3x3 rotation matrix columns mapping local (dx, dy, dz) -> world offset
        private final float m00, m01, m02;
        private final float m10, m11, m12;
        private final float m20, m21, m22;

        NormalOrientedVoxelSpace(
            VoxelSpace<T> delegate,
            int anchorX, int anchorY, int anchorZ,
            float tx, float ty, float tz
        ) {
            this.delegate = delegate;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;

            if (ty <= -0.9995f) {
                // 180-degree inversion (ceiling stalactite pointing straight down -Y)
                this.m00 = 1.0f;  this.m01 = 0.0f;  this.m02 = 0.0f;
                this.m10 = 0.0f;  this.m11 = -1.0f; this.m12 = 0.0f;
                this.m20 = 0.0f;  this.m21 = 0.0f;  this.m22 = -1.0f;
            } else {
                float k = 1.0f / (1.0f + ty);
                this.m00 = 1.0f - tx * tx * k;
                this.m01 = tx;
                this.m02 = -tx * tz * k;

                this.m10 = -tx;
                this.m11 = ty;
                this.m12 = -tz;

                this.m20 = -tx * tz * k;
                this.m21 = tz;
                this.m22 = 1.0f - tz * tz * k;
            }
        }

        private int mapX(int dx, int dy, int dz) {
            return anchorX + Math.round(dx * m00 + dy * m01 + dz * m02);
        }

        private int mapY(int dx, int dy, int dz) {
            return anchorY + Math.round(dx * m10 + dy * m11 + dz * m12);
        }

        private int mapZ(int dx, int dy, int dz) {
            return anchorZ + Math.round(dx * m20 + dy * m21 + dz * m22);
        }

        @Override
        public T getBlock(int x, int y, int z) {
            int dx = x - anchorX;
            int dy = y - anchorY;
            int dz = z - anchorZ;
            return delegate.getBlock(mapX(dx, dy, dz), mapY(dx, dy, dz), mapZ(dx, dy, dz));
        }

        @Override
        public void setBlock(int x, int y, int z, T block) {
            int dx = x - anchorX;
            int dy = y - anchorY;
            int dz = z - anchorZ;
            int wy = mapY(dx, dy, dz);
            if (wy >= delegate.getMinY() && wy < delegate.getMaxY()) {
                delegate.setBlock(mapX(dx, dy, dz), wy, mapZ(dx, dy, dz), block);
            }
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            int dx = x - anchorX;
            int dy = y - anchorY;
            int dz = z - anchorZ;
            int wy = mapY(dx, dy, dz);
            if (wy < delegate.getMinY() || wy >= delegate.getMaxY()) {
                return false;
            }
            return delegate.isAir(mapX(dx, dy, dz), wy, mapZ(dx, dy, dz));
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
