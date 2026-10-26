package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Selects nodes whose {@link GraphContent} satisfies a {@link ContentPredicate}.
 */
public final class ContentNodeSelector implements NodeSelector {

    private final ContentPredicate predicate;

    public ContentNodeSelector(ContentPredicate predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean select(GraphNode node, ProceduralGraph graph) {
        return predicate.test(node.getContent());
    }

    public ContentPredicate getPredicate() {
        return predicate;
    }
}
