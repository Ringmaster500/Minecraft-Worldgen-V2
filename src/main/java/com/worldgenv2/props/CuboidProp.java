package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.mask.BlockMask;
import com.worldgenv2.mask.MaskedVoxelSpace;
import java.util.Objects;

/**
 * Generates procedural 3D cuboid volumes (stone pillars, monoliths, boulders, or foundation plinths)
 * relative to the placement anchor, with optional edge/corner beveling and optional {@link BlockMask}.
 */
public final class CuboidProp<T> implements Prop<T> {

    private final T material;
    private final int radiusX;
    private final int height;
    private final int radiusZ;
    private final int yOffset;
    private final boolean bevelEdges;
    private final BlockMask<T> mask;

    public CuboidProp(T material, int radiusX, int height, int radiusZ) {
        this(material, radiusX, height, radiusZ, 0, false, null);
    }

    public CuboidProp(T material, int radiusX, int height, int radiusZ, int yOffset, boolean bevelEdges) {
        this(material, radiusX, height, radiusZ, yOffset, bevelEdges, null);
    }

    public CuboidProp(
        T material,
        int radiusX,
        int height,
        int radiusZ,
        int yOffset,
        boolean bevelEdges,
        BlockMask<T> mask
    ) {
        if (radiusX < 0 || radiusZ < 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid cuboid dimensions");
        }
        this.material = Objects.requireNonNull(material, "material");
        this.radiusX = radiusX;
        this.height = height;
        this.radiusZ = radiusZ;
        this.yOffset = yOffset;
        this.bevelEdges = bevelEdges;
        this.mask = mask;
    }

    public T material() {
        return material;
    }

    public int radiusX() {
        return radiusX;
    }

    public int height() {
        return height;
    }

    public int radiusZ() {
        return radiusZ;
    }

    public int yOffset() {
        return yOffset;
    }

    public boolean bevelEdges() {
        return bevelEdges;
    }

    public BlockMask<T> mask() {
        return mask;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = mask != null ? new MaskedVoxelSpace<>(context.voxelSpace(), mask) : context.voxelSpace();
        int cx = context.worldX();
        int baseY = context.worldY() + yOffset;
        int cz = context.worldZ();

        int minY = Math.max(space.getMinY(), baseY);
        int maxY = Math.min(space.getMaxY() - 1, baseY + height - 1);
        if (minY > maxY) {
            return false;
        }

        for (int y = minY; y <= maxY; y++) {
            boolean isTopOrBottom = (y == baseY || y == baseY + height - 1);
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    if (bevelEdges) {
                        boolean isCornerXZ = (radiusX > 0 && Math.abs(dx) == radiusX)
                            && (radiusZ > 0 && Math.abs(dz) == radiusZ);
                        if (isCornerXZ && (isTopOrBottom || height > 2)) {
                            continue;
                        }
                    }
                    space.setBlock(cx + dx, y, cz + dz, material);
                }
            }
        }
        return true;
    }

    @Override
    public int horizontalReach() {
        return Math.max(radiusX, radiusZ) + 1;
    }
}
