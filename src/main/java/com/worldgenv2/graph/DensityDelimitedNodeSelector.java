package com.worldgenv2.graph;

import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.Objects;

/**
 * Selects nodes where the evaluated continuous density falls within one of the specified range delimiters.
 */
public final class DensityDelimitedNodeSelector implements NodeSelector {

    private final DensityFunction density;
    private final List<DoubleRange> delimiters;

    public DensityDelimitedNodeSelector(DensityFunction density, List<DoubleRange> delimiters) {
        this.density = Objects.requireNonNull(density, "density");
        this.delimiters = delimiters != null ? List.copyOf(delimiters) : List.of();
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        double d = density.evaluate(node.getPosition().x(), node.getPosition().y(), node.getPosition().z());
        for (DoubleRange range : delimiters) {
            if (range.contains(d)) {
                return true;
            }
        }
        return false;
    }

    public DensityFunction getDensity() {
        return density;
    }

    public List<DoubleRange> getDelimiters() {
        return delimiters;
    }
}
