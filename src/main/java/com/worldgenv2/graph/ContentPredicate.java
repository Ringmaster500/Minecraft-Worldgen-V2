package com.worldgenv2.graph;

/**
 * Predicate testing whether a node's {@link GraphContent} satisfies specific conditions (e.g. ContentTags).
 */
@FunctionalInterface
public interface ContentPredicate {
    boolean test(GraphContent content);
}
