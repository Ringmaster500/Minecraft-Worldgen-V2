package com.worldgenv2.props;

import com.worldgenv2.graph.GraphContent;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.GraphNode;
import com.worldgenv2.vector.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Prop generator that reads and combines props carried by all graph nodes in range on a specified {@link GraphContent} layer.
 */
public final class GraphProp<T> implements Prop<T> {

    private final GraphGenerator graphGenerator;
    private final String contentLayer;
    private final double maxSearchRadius;

    public GraphProp(GraphGenerator graphGenerator, String contentLayer) {
        this(graphGenerator, contentLayer, 128.0);
    }

    public GraphProp(GraphGenerator graphGenerator, String contentLayer, double maxSearchRadius) {
        this.graphGenerator = Objects.requireNonNull(graphGenerator, "graphGenerator");
        this.contentLayer = contentLayer != null ? contentLayer : "";
        this.maxSearchRadius = Math.max(1.0, maxSearchRadius);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean generate(Context<T> context) {
        Vec3d samplePos = new Vec3d(context.worldX(), context.worldY(), context.worldZ());
        List<GraphNode> nearby = graphGenerator.getNodesNear(context.seed(), samplePos, maxSearchRadius);

        boolean placedAny = false;
        for (GraphNode node : nearby) {
            Optional<GraphContent.PropDistributionEntry> opt = node.getContent().getFirstProp(contentLayer);
            if (opt.isPresent()) {
                GraphContent.PropDistributionEntry entry = opt.get();
                double dist = node.getPosition().distance(samplePos);
                if (dist <= entry.range() && entry.range() > 0.0) {
                    Prop<T> childProp = (Prop<T>) entry.prop();
                    if (childProp.generate(context)) {
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }

    public GraphGenerator getGraphGenerator() {
        return graphGenerator;
    }

    public String getContentLayer() {
        return contentLayer;
    }

    public double getMaxSearchRadius() {
        return maxSearchRadius;
    }
}
