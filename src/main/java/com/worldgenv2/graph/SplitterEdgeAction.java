package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Subdivides an edge by inserting N evenly spaced nodes along its length and connecting them in a chain.
 */
public final class SplitterEdgeAction implements EdgeAction {

    private final int nodeCount;
    private final ContentSupplier content;

    public SplitterEdgeAction(int nodeCount, ContentSupplier content) {
        this.nodeCount = Math.max(0, nodeCount);
        this.content = content != null ? content : (n, s) -> GraphContent.EMPTY;
    }

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        if (nodeCount <= 0) {
            return;
        }

        GraphNode a = graph.getNode(edge.nodeA());
        GraphNode b = graph.getNode(edge.nodeB());
        if (a == null || b == null) {
            return;
        }

        Vec3d posA = a.getPosition();
        Vec3d posB = b.getPosition();

        // Remove the original connecting edge
        graph.removeEdge(a.getId(), b.getId());

        List<GraphNode> chain = new ArrayList<>(nodeCount + 2);
        chain.add(a);

        for (int i = 1; i <= nodeCount; i++) {
            double t = (double) i / (nodeCount + 1);
            Vec3d interp = posA.scale(1.0 - t).add(posB.scale(t));
            GraphNode inserted = graph.addNode(interp, GraphContent.EMPTY);
            inserted.setContent(content.supply(inserted, seed));
            chain.add(inserted);
        }

        chain.add(b);

        // Chain edges
        for (int i = 0; i < chain.size() - 1; i++) {
            graph.addEdge(chain.get(i).getId(), chain.get(i + 1).getId());
        }
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public ContentSupplier getContent() {
        return content;
    }
}
