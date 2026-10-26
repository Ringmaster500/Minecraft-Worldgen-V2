package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;

import java.util.List;
import java.util.Objects;

/**
 * Creates repeating alternating stripes of materials along the vertical Y axis with specific thicknesses.
 */
public final class StripedMaterialProvider<T> implements MaterialProvider<T> {

    public record Stripe<T>(int thickness, MaterialProvider<T> provider) {}

    private final List<Stripe<T>> stripes;
    private final int totalPeriod;

    public StripedMaterialProvider(List<Stripe<T>> stripes) {
        if (stripes == null || stripes.isEmpty()) {
            throw new IllegalArgumentException("Stripes cannot be empty");
        }
        this.stripes = List.copyOf(stripes);
        int sum = 0;
        for (Stripe<T> s : this.stripes) {
            sum += Math.max(1, s.thickness);
        }
        this.totalPeriod = sum;
    }

    public List<Stripe<T>> getStripes() {
        return stripes;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        int modY = Math.floorMod(y, totalPeriod);
        int acc = 0;
        for (Stripe<T> stripe : stripes) {
            acc += stripe.thickness;
            if (modY < acc) {
                return stripe.provider.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
            }
        }
        return stripes.get(0).provider.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
    }
}
