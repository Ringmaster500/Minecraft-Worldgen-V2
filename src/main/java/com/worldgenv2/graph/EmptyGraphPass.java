package com.worldgenv2.graph;

/**
 * No-op graph pass.
 */
public final class EmptyGraphPass implements GraphPass {

    public static final EmptyGraphPass INSTANCE = new EmptyGraphPass();

    @Override
    public void execute(ProceduralGraph graph, long seed, GraphBounds bounds) {
        // No-op
    }
}
