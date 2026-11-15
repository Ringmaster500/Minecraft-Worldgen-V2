package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Topologically interconnected procedural graph embedded in 3D coordinate space.
 */
public final class ProceduralGraph {

    private final Map<Integer, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<Long, GraphEdge> edges = new LinkedHashMap<>();
    private int nextNodeId = 0;

    public GraphNode addNode(Vec3d position, GraphContent content) {
        while (nodes.containsKey(nextNodeId)) {
            nextNodeId++;
        }
        return addNode(nextNodeId++, position, content);
    }

    public GraphNode addNode(int id, Vec3d position, GraphContent content) {
        GraphNode node = new GraphNode(id, position, content);
        nodes.put(id, node);
        if (id >= nextNodeId) {
            nextNodeId = id + 1;
        }
        return node;
    }

    public boolean removeNode(int id) {
        GraphNode node = nodes.remove(id);
        if (node == null) {
            return false;
        }

        // Remove all incident edges
        List<Integer> neighbors = new ArrayList<>(node.getConnections());
        for (int neighborId : neighbors) {
            GraphNode neighbor = nodes.get(neighborId);
            if (neighbor != null) {
                neighbor.removeConnection(id);
            }
            edges.remove(GraphEdge.key(id, neighborId));
        }
        return true;
    }

    public GraphEdge addEdge(int nodeA, int nodeB) {
        if (nodeA == nodeB) {
            return null;
        }
        GraphNode a = nodes.get(nodeA);
        GraphNode b = nodes.get(nodeB);
        if (a == null || b == null) {
            return null;
        }

        GraphEdge edge = new GraphEdge(nodeA, nodeB);
        long key = edge.key();
        if (!edges.containsKey(key)) {
            edges.put(key, edge);
            a.addConnection(nodeB);
            b.addConnection(nodeA);
        }
        return edges.get(key);
    }

    public boolean removeEdge(int nodeA, int nodeB) {
        long key = GraphEdge.key(nodeA, nodeB);
        GraphEdge edge = edges.remove(key);
        if (edge != null) {
            GraphNode a = nodes.get(nodeA);
            if (a != null) a.removeConnection(nodeB);
            GraphNode b = nodes.get(nodeB);
            if (b != null) b.removeConnection(nodeA);
            return true;
        }
        return false;
    }

    public boolean hasNode(int id) {
        return nodes.containsKey(id);
    }

    public GraphNode getNode(int id) {
        return nodes.get(id);
    }

    public Collection<GraphNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public boolean hasEdge(int nodeA, int nodeB) {
        return edges.containsKey(GraphEdge.key(nodeA, nodeB));
    }

    public GraphEdge getEdge(int nodeA, int nodeB) {
        return edges.get(GraphEdge.key(nodeA, nodeB));
    }

    public Collection<GraphEdge> getEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public List<GraphNode> getConnectedNodes(int nodeId) {
        GraphNode node = nodes.get(nodeId);
        if (node == null) return List.of();
        List<GraphNode> result = new ArrayList<>(node.getConnectionCount());
        for (int neighborId : node.getConnections()) {
            GraphNode neighbor = nodes.get(neighborId);
            if (neighbor != null) {
                result.add(neighbor);
            }
        }
        return result;
    }

    public List<GraphEdge> getConnectedEdges(int nodeId) {
        GraphNode node = nodes.get(nodeId);
        if (node == null) return List.of();
        List<GraphEdge> result = new ArrayList<>(node.getConnectionCount());
        for (int neighborId : node.getConnections()) {
            GraphEdge edge = edges.get(GraphEdge.key(nodeId, neighborId));
            if (edge != null) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<GraphNode> getNodesInBounds(GraphBounds bounds) {
        List<GraphNode> result = new ArrayList<>();
        for (GraphNode node : nodes.values()) {
            if (bounds.contains(node.getPosition())) {
                result.add(node);
            }
        }
        return result;
    }

    public List<GraphNode> getNodesInRadius(Vec3d center, double radius) {
        double rSq = radius * radius;
        List<GraphNode> result = new ArrayList<>();
        for (GraphNode node : nodes.values()) {
            if (node.getPosition().distanceSquared(center) <= rSq) {
                result.add(node);
            }
        }
        return result;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public void clear() {
        nodes.clear();
        edges.clear();
        nextNodeId = 0;
    }

    private java.util.List<double[]> segments = java.util.List.of();

    /** Every edge that touches this graph's cell as {ax, ay, az, bx, by, bz}, including edges whose other end is in a neighbouring cell. */
    public java.util.List<double[]> getSegments() { return segments; }

    public void setSegments(java.util.List<double[]> segments) { this.segments = segments; }
}
