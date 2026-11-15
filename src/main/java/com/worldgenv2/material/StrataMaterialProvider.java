package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Material provider delivering layered strata intervals along the vertical Y-axis.
 */
public final class StrataMaterialProvider<T> implements MaterialProvider<T> {

    public record Stratum<T>(int minY, int maxY, MaterialProvider<T> provider) {}

    private final List<Stratum<T>> strata;
    private final MaterialProvider<T> fallback;

    public StrataMaterialProvider(List<Stratum<T>> strata, MaterialProvider<T> fallback) {
        this.strata = strata != null ? List.copyOf(strata) : List.of();
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    public List<Stratum<T>> getStrata() {
        return strata;
    }

    public MaterialProvider<T> getFallback() {
        return fallback;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        return getMaterial(x, y, z, depth, space, -1, 0, slopeAngle, spaceRef);
    }

    @Override
    public T getMaterial(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                         int depthIntoCeiling, int spaceBelowCeiling, float slopeAngle, VoxelSpace<T> spaceRef) {
        for (Stratum<T> stratum : strata) {
            if (y >= stratum.minY && y <= stratum.maxY) {
                return stratum.provider.getMaterial(x, y, z, depthIntoFloor, spaceAboveFloor,
                        depthIntoCeiling, spaceBelowCeiling, slopeAngle, spaceRef);
            }
        }
        return fallback.getMaterial(x, y, z, depthIntoFloor, spaceAboveFloor,
                depthIntoCeiling, spaceBelowCeiling, slopeAngle, spaceRef);
    }
}
