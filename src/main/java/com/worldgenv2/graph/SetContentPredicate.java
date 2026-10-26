package com.worldgenv2.graph;

import java.util.List;
import java.util.Set;

/**
 * Passes when the node's {@link GraphContent} carries any one of the required content tags.
 */
public final class SetContentPredicate implements ContentPredicate {

    private final Set<String> requiredTags;

    public SetContentPredicate(List<String> tags) {
        this.requiredTags = tags != null ? Set.copyOf(tags) : Set.of();
    }

    @Override
    public boolean test(GraphContent content) {
        if (content == null) {
            return false;
        }
        for (String tag : requiredTags) {
            if (content.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getRequiredTags() {
        return requiredTags;
    }
}
