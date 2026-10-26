package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A discrete node in a {@link ProceduralGraph}, embedded at a continuous 3D coordinate and
 * carrying procedural {@link GraphContent}.
 */
public final class GraphNode {

    private final int id;
    private Vec3d position;
    private GraphContent content;
    private final Set<Integer> connections = new LinkedHashSet<>();
    private final Set<String> tags = new LinkedHashSet<>();

    public GraphNode(int id, Vec3d position, GraphContent content) {
        this.id = id;
        this.position = Objects.requireNonNull(position, "position");
        this.content = content != null ? content : GraphContent.EMPTY;
        if (content != null) {
            this.tags.addAll(content.getContentTags());
        }
    }

    public int getId() {
        return id;
    }

    public Vec3d getPosition() {
        return position;
    }

    public void setPosition(Vec3d position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public GraphContent getContent() {
        return content;
    }

    public void setContent(GraphContent content) {
        this.content = content != null ? content : GraphContent.EMPTY;
        if (content != null) {
            this.tags.addAll(content.getContentTags());
        }
    }

    public Set<Integer> getConnections() {
        return Collections.unmodifiableSet(connections);
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public boolean isConnectedTo(int otherId) {
        return connections.contains(otherId);
    }

    public void addConnection(int otherId) {
        if (otherId != this.id) {
            connections.add(otherId);
        }
    }

    public void removeConnection(int otherId) {
        connections.remove(otherId);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void addTag(String tag) {
        if (tag != null) {
            tags.add(tag);
        }
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return id == graphNode.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "GraphNode{id=" + id + ", pos=" + position + ", degree=" + connections.size() + "}";
    }
}
