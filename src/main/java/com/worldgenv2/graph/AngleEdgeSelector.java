package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.List;
import java.util.Objects;

/**
 * Selects edges whose angle relative to a reference vector (sampled at the edge midpoint) falls within the specified delimiters.
 * Angles are measured in degrees in [0, 90] (angles above 90° fold to 180° - theta).
 */
public final class AngleEdgeSelector implements EdgeSelector {

    private final VectorProvider reference;
    private final List<DoubleRange> delimiters;

    public AngleEdgeSelector(VectorProvider reference, List<DoubleRange> delimiters) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.delimiters = delimiters != null ? List.copyOf(delimiters) : List.of();
    }

    @Override
    public boolean select(GraphEdge edge, ProceduralGraph graph) {
        Vec3d mid = edge.midpoint(graph);
        Vec3d ref = reference.getVector(mid.x(), mid.y(), mid.z());
        Vec3d edgeVec = edge.vector(graph);

        double edgeLen = edgeVec.length();
        double refLen = ref.length();
        if (edgeLen <= 1e-9 || refLen <= 1e-9) {
            return false;
        }

        double cosTheta = Math.clamp(edgeVec.dot(ref) / (edgeLen * refLen), -1.0, 1.0);
        double degrees = Math.toDegrees(Math.acos(cosTheta));
        if (degrees > 90.0) {
            degrees = 180.0 - degrees;
        }

        for (DoubleRange range : delimiters) {
            if (range.contains(degrees)) {
                return true;
            }
        }
        return false;
    }

    public VectorProvider getReference() {
        return reference;
    }

    public List<DoubleRange> getDelimiters() {
        return delimiters;
    }
}
