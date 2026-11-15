package com.worldgenv2.worldstructure;

import com.worldgenv2.density.GraphDensity;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.GraphNode;

import java.util.List;

/**
 * Caves built the Hytale way: a Graph Generator whose nodes carry Density content on a layer, read like a Graph density and
 * subtracted from the terrain. Where the blended value is below 0 the rock is removed; the background is a positive number so
 * the rest of the terrain is untouched. What the caves look like (chambers, tunnels, how they branch) is entirely the graph.
 */
public final class GraphCaveCarver implements ColumnCarver {

    private static final int STEP = 2;
    private final GraphGenerator graph;
    private final long seed;
    private final GraphDensity reader;
    private final double reach;
    private final int bedrockShell;

    public GraphCaveCarver(GraphGenerator graph, String layer, long seed, int bedrockShell) {
        this.graph = graph;
        this.seed = seed;
        this.reader = new GraphDensity(graph, layer, 1.0f, seed).withTransition(1.0, 0.0, 0.0);   // weight falls off linearly, so the nearest node dominates
        this.reach = 64.0;
        this.bedrockShell = Math.max(0, bedrockShell);
    }

    @Override
    public void carveColumn(float x, float z, int minY, int maxY, float[] col) {
        double radius = Math.max(reach, graph.maxContentRange());
        List<GraphNode> near = graph.nodesNearColumn(seed, x, z, radius, minY, maxY);
        if (near.isEmpty()) {
            near = graph.nodesNearColumn(seed, x, z, Math.max(reach, graph.maxContentRange()), minY, maxY);   // the first query may have revealed a longer range
            if (near.isEmpty()) return;
        }
        double rangeY = Math.max(1.0, graph.maxContentRange());
        java.util.ArrayList<GraphNode> byY = new java.util.ArrayList<>(near);
        byY.sort(java.util.Comparator.comparingDouble(nd -> nd.getPosition().y()));
        double[] ys = new double[byY.size()];
        for (int q = 0; q < ys.length; q++) ys[q] = byY.get(q).getPosition().y();
        int height = maxY - minY;
        int floor = minY + bedrockShell;
        int n = (height + STEP - 1) / STEP + 1;
        float[] s = new float[n];
        boolean anyNegative = false;
        for (int j = 0; j < n; j++) {
            int y = Math.min(maxY - 1, minY + j * STEP);
            if (y < floor) { s[j] = 1f; continue; }
            int lo = java.util.Arrays.binarySearch(ys, y - rangeY), hi = java.util.Arrays.binarySearch(ys, y + rangeY);
            lo = lo < 0 ? -lo - 1 : lo;
            hi = hi < 0 ? -hi - 1 : hi + 1;
            s[j] = hi <= lo ? 1f : reader.blend(byY.subList(lo, hi), x, y, z);
            if (s[j] < 0f) anyNegative = true;
        }
        if (!anyNegative) return;
        for (int k = 0; k < height; k++) {
            int j = k / STEP;
            float t = (k - j * STEP) / (float) STEP;
            float v = s[j] + (s[Math.min(n - 1, j + 1)] - s[j]) * t;   // linear between samples so a tunnel is smooth rather than stepped
            if (v < 0f && col[k] > v) col[k] = v;
        }
    }
}