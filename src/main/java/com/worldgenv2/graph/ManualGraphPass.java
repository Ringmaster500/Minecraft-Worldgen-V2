package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Places nodes manually from predefined coordinate and connection configurations.
 */
public final class ManualGraphPass implements GraphPass {

    public record ManualNode(Vec3d position, GraphContent content, String name, List<String> connections) {
        public ManualNode {
            Objects.requireNonNull(position, "position");
            if (content == null) content = GraphContent.EMPTY;
            if (name == null) name = "";
            connections = connections != null ? List.copyOf(connections) : List.of();
        }
    }

    private final List<ManualNode> nodes;

    public ManualGraphPass(List<ManualNode> nodes) {
        this.nodes = nodes != null ? List.copyOf(nodes) : List.of();
    }

    @Override
    public void execute(ProceduralGraph graph, long seed, GraphBounds bounds) {
        Map<String, GraphNode> nameToNode = new HashMap<>();

        for (ManualNode config : nodes) {
            if (bounds.contains(config.position())) {
                GraphNode node = graph.addNode(config.position(), config.content());
                if (!config.name().isEmpty()) {
                    nameToNode.put(config.name(), node);
                }
            }
        }

        // Establish connections by name
        for (ManualNode config : nodes) {
            GraphNode src = nameToNode.get(config.name());
            if (src != null) {
                for (String conn : config.connections()) {
                    GraphNode dst = nameToNode.get(conn);
                    if (dst != null && dst.getId() != src.getId()) {
                        graph.addEdge(src.getId(), dst.getId());
                    }
                }
            }
        }
    }

    public List<ManualNode> getNodes() {
        return nodes;
    }
}
