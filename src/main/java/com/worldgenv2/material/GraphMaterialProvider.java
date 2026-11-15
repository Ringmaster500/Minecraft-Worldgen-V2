package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.graph.GraphContent;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.GraphNode;
import com.worldgenv2.vector.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Material provider that resolves block materials by querying the nearest graph node in range
 * on a specified {@link GraphContent} layer.
 */
public final class GraphMaterialProvider<T> implements MaterialProvider<T> {

    private final GraphGenerator graphGenerator;
    private final String contentLayer;
    private final MaterialProvider<T> fallback;
    private final long seed;
    private final double maxSearchRadius;

    public GraphMaterialProvider(GraphGenerator graphGenerator, String contentLayer, MaterialProvider<T> fallback, long seed) {
        this(graphGenerator, contentLayer, fallback, seed, 128.0);
    }

    public GraphMaterialProvider(GraphGenerator graphGenerator, String contentLayer, MaterialProvider<T> fallback, long seed, double maxSearchRadius) {
        this.graphGenerator = Objects.requireNonNull(graphGenerator, "graphGenerator");
        this.contentLayer = contentLayer != null ? contentLayer : "";
        this.fallback = fallback != null ? fallback : (x, y, z, d, s, sl, ref) -> null;
        this.seed = seed;
        this.maxSearchRadius = Math.max(1.0, maxSearchRadius);
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        return getMaterial(x, y, z, depth, space, -1, 0, slopeAngle, spaceRef);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getMaterial(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                         int depthIntoCeiling, int spaceBelowCeiling, float slopeAngle, VoxelSpace<T> spaceRef) {
        Vec3d samplePos = new Vec3d(x, y, z);
        List<GraphNode> nearby = graphGenerator.getNodesInRange(seed, samplePos, maxSearchRadius, 0.0);

        GraphNode nearestNode = null;
        GraphContent.MaterialEntry nearestEntry = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (GraphNode node : nearby) {
            Optional<GraphContent.MaterialEntry> opt = node.getContent().getFirstMaterial(contentLayer);
            if (opt.isPresent()) {
                GraphContent.MaterialEntry entry = opt.get();
                double distSq = node.getPosition().distanceSquared(samplePos);
                double maxRange = entry.range();
                if (distSq <= maxRange * maxRange && distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestNode = node;
                    nearestEntry = entry;
                }
            }
        }

        if (nearestEntry != null) {
            MaterialProvider<T> childProvider = (MaterialProvider<T>) nearestEntry.provider();
            return childProvider.getMaterial(x, y, z, depthIntoFloor, spaceAboveFloor,
                    depthIntoCeiling, spaceBelowCeiling, slopeAngle, spaceRef);
        }

        return fallback.getMaterial(x, y, z, depthIntoFloor, spaceAboveFloor,
                depthIntoCeiling, spaceBelowCeiling, slopeAngle, spaceRef);
    }

    public GraphGenerator getGraphGenerator() {
        return graphGenerator;
    }

    public String getContentLayer() {
        return contentLayer;
    }

    public MaterialProvider<T> getFallback() {
        return fallback;
    }

    public long getSeed() {
        return seed;
    }

    public double getMaxSearchRadius() {
        return maxSearchRadius;
    }
}
