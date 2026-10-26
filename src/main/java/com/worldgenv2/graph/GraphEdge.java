package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

/**
 * An undirected edge connecting two {@link GraphNode} instances in a {@link ProceduralGraph}.
 */
public record GraphEdge(int nodeA, int nodeB) {

    public GraphEdge {
        if (nodeA == nodeB) {
            throw new IllegalArgumentException("Self-loop edges are not permitted: " + nodeA);
        }
        if (nodeA > nodeB) {
            int tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
    }

    public static long key(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return (((long) min) << 32) | (max & 0xFFFFFFFFL);
    }

    public long key() {
        return key(nodeA, nodeB);
    }

    public boolean connects(int nodeId) {
        return nodeA == nodeId || nodeB == nodeId;
    }

    public int other(int nodeId) {
        if (nodeA == nodeId) return nodeB;
        if (nodeB == nodeId) return nodeA;
        throw new IllegalArgumentException("Node " + nodeId + " is not connected to this edge " + this);
    }

    public Vec3d vector(ProceduralGraph graph) {
        GraphNode a = graph.getNode(nodeA);
        GraphNode b = graph.getNode(nodeB);
        if (a == null || b == null) return Vec3d.ZERO;
        return b.getPosition().subtract(a.getPosition());
    }

    public Vec3d midpoint(ProceduralGraph graph) {
        GraphNode a = graph.getNode(nodeA);
        GraphNode b = graph.getNode(nodeB);
        if (a == null || b == null) return Vec3d.ZERO;
        return a.getPosition().add(b.getPosition()).scale(0.5);
    }

    public double length(ProceduralGraph graph) {
        GraphNode a = graph.getNode(nodeA);
        GraphNode b = graph.getNode(nodeB);
        if (a == null || b == null) return 0.0;
        return a.getPosition().distance(b.getPosition());
    }
}
