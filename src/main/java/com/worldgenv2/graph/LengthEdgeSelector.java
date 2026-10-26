package com.worldgenv2.graph;

import java.util.List;

/**
 * Selects edges whose spatial Euclidean length falls within the specified delimiters.
 */
public final class LengthEdgeSelector implements EdgeSelector {

    private final List<DoubleRange> delimiters;

    public LengthEdgeSelector(List<DoubleRange> delimiters) {
        this.delimiters = delimiters != null ? List.copyOf(delimiters) : List.of();
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        double len = edge.length(graph);
        for (DoubleRange range : delimiters) {
            if (range.contains(len)) {
                return true;
            }
        }
        return false;
    }

    public List<DoubleRange> getDelimiters() {
        return delimiters;
    }
}
