package com.worldgenv2.graph;

/**
 * Stage in a procedural graph generation pipeline that reads and edits the graph within the generation bounds.
 */
@FunctionalInterface
public interface GraphPass {
    void execute(ProceduralGraph graph, long seed, GraphBounds bounds);
}
