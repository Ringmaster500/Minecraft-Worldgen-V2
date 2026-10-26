package com.worldgenv2.graph;

import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.List;
import java.util.Objects;

/**
 * Spawns a cluster of new nodes positioned relative to the host node.
 * Optionally connects the host node to each newly spawned node.
 */
public final class SpawnerNodeAction implements NodeAction {

    private final VectorProvider offset;
    private final PositionsProvider positions;
    private final ContentSupplier content;
    private final GraphBounds clusterBounds;
    private final double maxOffsetExclusive;
    private final boolean createEdges;

    public SpawnerNodeAction(
        VectorProvider offset,
        PositionsProvider positions,
        ContentSupplier content,
        GraphBounds clusterBounds,
        double maxOffsetExclusive,
        boolean createEdges
    ) {
        this.offset = offset != null ? offset : (x, y, z) -> Vec3d.ZERO;
        this.positions = Objects.requireNonNull(positions, "positions");
        this.content = content != null ? content : (n, s) -> GraphContent.EMPTY;
        this.clusterBounds = clusterBounds != null ? clusterBounds : new GraphBounds(-50, -50, -50, 50, 50, 50);
        this.maxOffsetExclusive = Math.max(0.0, maxOffsetExclusive);
        this.createEdges = createEdges;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        Vec3d nodePos = node.getPosition();
        Vec3d off = offset.getVector(nodePos.x(), nodePos.y(), nodePos.z());

        if (maxOffsetExclusive > 0.0 && off.length() >= maxOffsetExclusive) {
            return;
        }

        Vec3d clusterOrigin = nodePos.add(off);
        List<Vec3d> relativePoints = positions.getPositions(
            clusterBounds.minX(), clusterBounds.minY(), clusterBounds.minZ(),
            clusterBounds.maxX(), clusterBounds.maxY(), clusterBounds.maxZ()
        );

        for (Vec3d rel : relativePoints) {
            if (clusterBounds.contains(rel)) {
                Vec3d worldPoint = clusterOrigin.add(rel);
                GraphNode spawned = graph.addNode(worldPoint, GraphContent.EMPTY);
                spawned.setContent(content.supply(spawned, seed));
                if (createEdges) {
                    graph.addEdge(node.getId(), spawned.getId());
                }
            }
        }
    }

    public VectorProvider getOffset() {
        return offset;
    }

    public PositionsProvider getPositions() {
        return positions;
    }

    public ContentSupplier getContent() {
        return content;
    }

    public GraphBounds getClusterBounds() {
        return clusterBounds;
    }

    public double getMaxOffsetExclusive() {
        return maxOffsetExclusive;
    }

    public boolean isCreateEdges() {
        return createEdges;
    }
}
