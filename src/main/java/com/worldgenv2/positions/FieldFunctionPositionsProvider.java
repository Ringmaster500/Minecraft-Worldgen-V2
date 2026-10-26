package com.worldgenv2.positions;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.vector.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Filters positions using a Density field: keeps points only where the density value
 * falls within one of the configured delimiter ranges [min, max).
 */
public final class FieldFunctionPositionsProvider implements PositionsProvider {

    public record Delimiter(double min, double max) {
        public boolean matches(double value) {
            return value >= min && value < max;
        }
    }

    private final PositionsProvider source;
    private final DensityFunction density;
    private final List<Delimiter> delimiters;

    public FieldFunctionPositionsProvider(
        PositionsProvider source,
        DensityFunction density,
        List<Delimiter> delimiters
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.density = Objects.requireNonNull(density, "density");
        this.delimiters = List.copyOf(Objects.requireNonNull(delimiters, "delimiters"));
    }

    public FieldFunctionPositionsProvider(PositionsProvider source, DensityFunction density, double min, double max) {
        this(source, density, List.of(new Delimiter(min, max)));
    }

    @Override
    public List<Vec3d> getPositions(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Vec3d> candidates = source.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        List<Vec3d> result = new ArrayList<>();

        for (Vec3d p : candidates) {
            float val = density.compute((float) p.x(), (float) p.y(), (float) p.z());
            for (Delimiter d : delimiters) {
                if (d.matches(val)) {
                    result.add(p);
                    break;
                }
            }
        }
        return result;
    }

    public PositionsProvider getSource() {
        return source;
    }

    public DensityFunction getDensity() {
        return density;
    }

    public List<Delimiter> getDelimiters() {
        return delimiters;
    }
}
