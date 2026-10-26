package com.worldgenv2.graph;

import java.util.Objects;

/**
 * Inverts the result of a child {@link ContentPredicate}.
 */
public final class NotContentPredicate implements ContentPredicate {

    private final ContentPredicate child;

    public NotContentPredicate(ContentPredicate child) {
        this.child = Objects.requireNonNull(child, "child");
    }

    @Override
    public boolean test(GraphContent content) {
        return !child.test(content);
    }

    public ContentPredicate getChild() {
        return child;
    }
}
