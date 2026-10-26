package com.worldgenv2.graph;

import java.util.List;

/**
 * Passes if any child {@link ContentPredicate} passes.
 */
public final class OrContentPredicate implements ContentPredicate {

    private final List<ContentPredicate> children;

    public OrContentPredicate(List<ContentPredicate> children) {
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public boolean test(GraphContent content) {
        for (ContentPredicate child : children) {
            if (child.test(content)) {
                return true;
            }
        }
        return false;
    }

    public List<ContentPredicate> getChildren() {
        return children;
    }
}
