package com.worldgenv2.graph;

import java.util.List;

/**
 * Passes if and only if all child {@link ContentPredicate} instances pass.
 */
public final class AndContentPredicate implements ContentPredicate {

    private final List<ContentPredicate> children;

    public AndContentPredicate(List<ContentPredicate> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean test(GraphContent content) {
        for (ContentPredicate child : children) {
            if (!child.test(content)) {
                return false;
            }
        }
        return true;
    }

    public List<ContentPredicate> getChildren() {
        return children;
    }
}
