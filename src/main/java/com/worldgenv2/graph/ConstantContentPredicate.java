package com.worldgenv2.graph;

/**
 * Invariant boolean content predicate.
 */
public final class ConstantContentPredicate implements ContentPredicate {

    private final boolean value;

    public ConstantContentPredicate(boolean value) {
        this.value = value;
    }

    @Override
    public boolean test(GraphContent content) {
        return value;
    }

    public boolean getValue() {
        return value;
    }
}
