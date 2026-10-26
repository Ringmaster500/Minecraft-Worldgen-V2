package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Attaches {@link GraphContent} from a {@link ContentSupplier} to a node.
 */
public final class ContentNodeAction implements NodeAction {

    private final ContentSupplier supplier;

    public ContentNodeAction(ContentSupplier supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        node.setContent(supplier.supply(node, seed));
    }

    public ContentSupplier getSupplier() {
        return supplier;
    }
}
