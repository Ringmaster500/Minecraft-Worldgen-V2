package com.worldgenv2.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Copies the {@link GraphContent} of a qualifying connected node onto the current node.
 */
public final class ContentCopyNodeAction implements NodeAction {

    private final ContentPredicate predicate;
    private final long actionSeed;

    public ContentCopyNodeAction(ContentPredicate predicate, long actionSeed) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphNode node, ProceduralGraph graph, long seed) {
        List<GraphNode> qualifying = new ArrayList<>();
        for (GraphNode neighbor : graph.getConnectedNodes(node.getId())) {
            if (predicate.test(neighbor.getContent())) {
                qualifying.add(neighbor);
            }
        }

        if (qualifying.isEmpty()) {
            return;
        }

        if (qualifying.size() == 1) {
            node.setContent(qualifying.getFirst().getContent());
            return;
        }

        long h = seed ^ actionSeed ^ (node.stableKey() * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);

        int idx = (int) (Math.abs(h) % qualifying.size());
        node.setContent(qualifying.get(idx).getContent());
    }

    public ContentPredicate getPredicate() {
        return predicate;
    }
}
