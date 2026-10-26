package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Supplies an invariant {@link GraphContent} instance.
 */
public final class ConstantContentSupplier implements ContentSupplier {

    private final GraphContent content;

    public ConstantContentSupplier(GraphContent content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public GraphContent supply(GraphNode node, long seed) {
        return content;
    }

    public GraphContent getContent() {
        return content;
    }
}
