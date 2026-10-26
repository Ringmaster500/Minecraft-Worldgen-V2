package com.worldgenv2.positions;

import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Combines positions from multiple PositionProviders into a single unified stream.
 */
public final class UnionPositionsProvider implements PositionsProvider {

    private final List<PositionsProvider> providers;

    public UnionPositionsProvider(List<PositionsProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        this.providers = List.copyOf(providers);
    }

    public UnionPositionsProvider(PositionsProvider... providers) {
        this(List.of(providers));
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> result = new ArrayList<>();
        for (PositionsProvider p : providers) {
            result.addAll(p.getPositions(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return result;
    }

    public List<PositionsProvider> getProviders() {
        return providers;
    }
}
