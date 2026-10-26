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
        Vec3d samplePos = new Vec3d(x, y, z);
        List<GraphNode> nearbyNodes = graphGenerator.getNodesNear(seed, samplePos, maxSearchRadius);

        if (nearbyNodes.isEmpty()) {
            return backgroundValue;
        }

        double totalWeight = 0.0;
        double weightedSum = 0.0;

        for (GraphNode node : nearbyNodes) {
            Optional<GraphContent.DensityEntry> opt = node.getContent().getFirstDensity(contentLayer);
            if (opt.isPresent()) {
                GraphContent.DensityEntry entry = opt.get();
                double dist = node.getPosition().distance(samplePos);
                if (dist <= entry.range() && entry.range() > 0.0) {
                    double weight = 1.0 - (dist / entry.range());
                    float dVal = entry.density().compute(x, y, z);
                    weightedSum += weight * dVal;
                    totalWeight += weight;
                }
            }
        }

        if (totalWeight > 0.0) {
            return (float) (weightedSum / totalWeight);
        }
        return backgroundValue;
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
