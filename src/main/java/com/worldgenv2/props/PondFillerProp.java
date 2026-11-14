package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Carves small depression basins on gentle terrain and fills them with fluid up to a hydrostatic rim,
 * lining the basin bed and perimeter banks with a sediment material (e.g., clay, gravel, or sand).
 */
public final class PondFillerProp<T> implements Prop<T> {

    private final T fluidMaterial;
    private final T basinMaterial;
    private final T airMaterial;
    private final int radius;
    private final int depth;

    public PondFillerProp(T fluidMaterial, T basinMaterial, T airMaterial, int radius, int depth) {
        if (radius < 1 || depth < 1) {
            throw new IllegalArgumentException("radius and depth must be >= 1");
        }
        this.fluidMaterial = Objects.requireNonNull(fluidMaterial, "fluidMaterial");
        this.basinMaterial = Objects.requireNonNull(basinMaterial, "basinMaterial");
        this.airMaterial = Objects.requireNonNull(airMaterial, "airMaterial");
        this.radius = radius;
        this.depth = depth;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = context.voxelSpace();
        int cx = context.worldX();
        int waterSurfaceY = context.worldY() - 1; // Top solid ground block becomes fluid surface level
        int cz = context.worldZ();

        if (waterSurfaceY - depth < space.getMinY() || waterSurfaceY + 2 >= space.getMaxY()) {
            return false;
        }

        // Verify that the perimeter rim at waterSurfaceY is enclosed by solid ground so fluid cannot spill out
        int rSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > rSq) {
                    continue;
                }
                if (space.isAir(cx + dx, waterSurfaceY, cz + dz)) {
                    return false;
                }
            }
        }

        // Carve the basin, fill with fluid, and line the bed & walls with basinMaterial
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > rSq) {
                    continue;
                }
                float normDist = (float) Math.sqrt((float) distSq / rSq);
                boolean isRim = (dx * dx + dz * dz) >= (radius - 1) * (radius - 1);
                int localDepth = Math.max(1, Math.round((1.0f - normDist * 0.7f) * depth));

                if (isRim) {
                    space.setBlock(cx + dx, waterSurfaceY, cz + dz, basinMaterial);
                } else {
                    // Clear air pocket 1 block above fluid surface
                    space.setBlock(cx + dx, waterSurfaceY + 1, cz + dz, airMaterial);
                    for (int dy = 0; dy < localDepth; dy++) {
                        space.setBlock(cx + dx, waterSurfaceY - dy, cz + dz, fluidMaterial);
                    }
                    // Line the floor beneath the fluid
                    space.setBlock(cx + dx, waterSurfaceY - localDepth, cz + dz, basinMaterial);
                }
            }
        }
        return true;
    }

    @Override
    public int horizontalReach() {
        return radius;
    }
}
