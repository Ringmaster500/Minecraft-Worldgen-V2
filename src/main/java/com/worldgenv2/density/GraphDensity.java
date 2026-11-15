package com.worldgenv2.density;

import com.worldgenv2.graph.GraphContent;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.GraphNode;
import com.worldgenv2.vector.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Density function that reads density fields carried by graph nodes on a specific {@link GraphContent} layer,
 * smoothly blending nodes based on inverse-distance weights.
 */
public final class GraphDensity implements DensityFunction {

    private final GraphGenerator graphGenerator;
    private final String contentLayer;
    private final float backgroundValue;
    private final long seed;
    private final double maxSearchRadius;
    private double slope = 2.5, offset = 0.0, smooth = 0.2;

    /** Hytale's transition curve: how steeply weight rises from the edge of a Range towards the node (see Graph density docs). */
    public GraphDensity withTransition(double slope, double offset, double smooth) {
        this.slope = slope; this.offset = offset; this.smooth = Math.max(0.0, smooth);
        return this;
    }

    private double weight(double dist, double range) {
        double t = 1.0 - dist / range;
        double x = Math.max(0.0, slope * (t + offset));
        if (smooth <= 0.0) return Math.min(1.0, x);
        double h = Math.max(smooth - Math.abs(x - 1.0), 0.0) / smooth;
        return Math.max(0.0, Math.min(x, 1.0) - h * h * smooth * 0.25);
    }

    public GraphDensity(GraphGenerator graphGenerator, String contentLayer, float backgroundValue, long seed) {
        this(graphGenerator, contentLayer, backgroundValue, seed, 128.0);
    }

    public GraphDensity(GraphGenerator graphGenerator, String contentLayer, float backgroundValue, long seed, double maxSearchRadius) {
        this.graphGenerator = Objects.requireNonNull(graphGenerator, "graphGenerator");
        this.contentLayer = contentLayer != null ? contentLayer : "";
        this.backgroundValue = backgroundValue;
        this.seed = seed;
        this.maxSearchRadius = Math.max(1.0, maxSearchRadius);
    }

    @Override
    public float compute(float x, float y, float z) {
        return blend(graphGenerator.getNodesInRange(seed, new Vec3d(x, y, z), maxSearchRadius, 0.0), x, y, z);
    }

    /** The blended value at (x, y, z) from the given nodes' content on this reader's layer; each entry is evaluated with its node as the anchor. */
    public float blend(List<GraphNode> nearbyNodes, float x, float y, float z) {
        if (nearbyNodes.isEmpty()) return backgroundValue;
        Vec3d samplePos = new Vec3d(x, y, z);
        double totalWeight = 0.0, weightedSum = 0.0;
        for (GraphNode node : nearbyNodes) {
            Optional<GraphContent.DensityEntry> opt = node.getContent().getFirstDensity(contentLayer);
            if (opt.isPresent()) {
                GraphContent.DensityEntry entry = opt.get();
                double dist = node.getPosition().distance(samplePos);
                if (dist <= entry.range() && entry.range() > 0.0) {
                    double weight = weight(dist, entry.range());
                    Vec3d np = node.getPosition();
                    float dVal = com.worldgenv2.hytale.HDensity.withAnchor(new double[]{np.x(), np.y(), np.z()}, () -> entry.density().compute(x, y, z));
                    weightedSum += weight * dVal;
                    totalWeight += weight;
                }
            }
        }
        if (totalWeight <= 0.0) return backgroundValue;
        // Where the combined weight is below 1 the background shows through, so the field stays continuous at a Range's edge.
        if (totalWeight >= 1.0) return (float) (weightedSum / totalWeight);
        return (float) (weightedSum + (1.0 - totalWeight) * backgroundValue);
    }
    public GraphGenerator getGraphGenerator() {
        return graphGenerator;
    }

    public String getContentLayer() {
        return contentLayer;
    }

    public float getBackgroundValue() {
        return backgroundValue;
    }

    public long getSeed() {
        return seed;
    }

    public double getMaxSearchRadius() {
        return maxSearchRadius;
    }
}
