package com.worldgenv2.graph;

import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds and caches a procedural graph composed of sequential {@link GraphPass} pipeline stages.
 */
public final class GraphGenerator {

    public static final Vec3d DEFAULT_CELL_SIZE = new Vec3d(320.0, 320.0, 320.0);
    public static final int DEFAULT_CAPACITY = 50;

    private final List<GraphPass> passes;
    private final Vec3d cacheCellSize;
    private final int cacheCapacity;

    // Thread-safe LRU cell cache
    private final Map<Long, ProceduralGraph> cellCache;
    private volatile double maxContentRange = 0.0;
    private volatile double contextMargin = 0.0;

    /** How far beyond a cell the passes need to look (caps such as Range or MaxDistance), so a cell's nodes do not depend on its edges. */
    public GraphGenerator withContextMargin(double margin) { this.contextMargin = Math.max(0.0, margin); return this; }

    public GraphGenerator(List<GraphPass> passes) {
        this(passes, DEFAULT_CELL_SIZE, DEFAULT_CAPACITY);
    }

    public GraphGenerator(List<GraphPass> passes, Vec3d cacheCellSize, int cacheCapacity) {
        this.passes = passes != null ? List.copyOf(passes) : List.of();
        this.cacheCellSize = cacheCellSize != null ? cacheCellSize : DEFAULT_CELL_SIZE;
        this.cacheCapacity = Math.max(1, cacheCapacity);

        this.cellCache = new LinkedHashMap<>(this.cacheCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ProceduralGraph> eldest) {
                return size() > GraphGenerator.this.cacheCapacity;
            }
        };
    }

    /**
     * Generates a discrete procedural graph strictly covering the given bounds.
     */
    public ProceduralGraph generate(long seed, GraphBounds bounds) {
        ProceduralGraph graph = new ProceduralGraph();
        for (GraphPass pass : passes) {
            pass.execute(graph, seed, bounds);
        }
        double r = maxContentRange;
        for (GraphNode n : graph.getNodes()) {
            GraphContent c = n.getContent();
            for (var e : c.getDensityContent()) r = Math.max(r, e.range());
            for (var e : c.getMaterialContent()) r = Math.max(r, e.range());
            for (var e : c.getPropDistributionContent()) r = Math.max(r, e.range());
            for (var e : c.getPositionsContent()) r = Math.max(r, e.range());
        }
        maxContentRange = r;
        return graph;
    }

    /**
     * Retrieves or generates the procedural graph for a specific grid cell (cx, cy, cz).
     */
    public ProceduralGraph getGraphForCell(long seed, int cx, int cy, int cz) {
        long key = (((long) cx & 0xFFFFF) << 40) | (((long) cy & 0xFFFFF) << 20) | ((long) cz & 0xFFFFF);
        key ^= seed * 0x9E3779B97F4A7C15L;

        synchronized (cellCache) {
            ProceduralGraph cached = cellCache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        double minX = cx * cacheCellSize.x();
        double minY = cy * cacheCellSize.y();
        double minZ = cz * cacheCellSize.z();
        double maxX = minX + cacheCellSize.x();
        double maxY = minY + cacheCellSize.y();
        double maxZ = minZ + cacheCellSize.z();

        GraphBounds cellBounds = new GraphBounds(minX, minY, minZ, maxX, maxY, maxZ);
        ProceduralGraph work = generate(seed, cellBounds.expand(contextMargin));
        ProceduralGraph generated = new ProceduralGraph();
        for (GraphNode n : work.getNodes()) if (ownedBy(cellBounds, n.getPosition())) generated.addNode(n.getId(), n.getPosition(), n.getContent());
        for (GraphEdge e : work.getEdges()) if (generated.hasNode(e.nodeA()) && generated.hasNode(e.nodeB())) generated.addEdge(e.nodeA(), e.nodeB());

        synchronized (cellCache) {
            cellCache.put(key, generated);
        }
        return generated;
    }

    /**
     * Queries all nodes across adjacent grid cells within a search radius of pos.
     */
    public List<GraphNode> getNodesNear(long seed, Vec3d pos, double searchRadius) {
        double r = Math.max(0.0, searchRadius);
        int minCx = (int) Math.floor((pos.x() - r) / cacheCellSize.x());
        int maxCx = (int) Math.floor((pos.x() + r) / cacheCellSize.x());
        int minCy = (int) Math.floor((pos.y() - r) / cacheCellSize.y());
        int maxCy = (int) Math.floor((pos.y() + r) / cacheCellSize.y());
        int minCz = (int) Math.floor((pos.z() - r) / cacheCellSize.z());
        int maxCz = (int) Math.floor((pos.z() + r) / cacheCellSize.z());

        double rSq = r * r;
        List<GraphNode> result = new ArrayList<>();

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    ProceduralGraph g = getGraphForCell(seed, cx, cy, cz);
                    for (GraphNode node : g.getNodes()) {
                        if (node.getPosition().distanceSquared(pos) <= rSq) {
                            result.add(node);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** A cell owns its minimum faces but not its maximum faces, so neighbouring cells never both keep a node. */
    private static boolean ownedBy(GraphBounds b, Vec3d p) {
        return p.x() >= b.minX() && p.x() < b.maxX() && p.y() >= b.minY() && p.y() < b.maxY() && p.z() >= b.minZ() && p.z() < b.maxZ();
    }

    /** The largest content Range seen on any node generated so far (readers search at least this far). */
    public double maxContentRange() { return maxContentRange; }

    /**
     * Nodes whose content can reach pos: searches at least {@code minRadius} and as far as the longest content Range seen, plus
     * {@code extra}. Cells generated by the first search may reveal a longer range, in which case the search widens once.
     */
    public List<GraphNode> getNodesInRange(long seed, Vec3d pos, double minRadius, double extra) {
        double r = Math.max(minRadius, maxContentRange) + extra;
        List<GraphNode> nodes = getNodesNear(seed, pos, r);
        double r2 = Math.max(minRadius, maxContentRange) + extra;
        return r2 > r ? getNodesNear(seed, pos, r2) : nodes;
    }

    public List<GraphPass> getPasses() {
        return passes;
    }

    public Vec3d getCacheCellSize() {
        return cacheCellSize;
    }

    public int getCacheCapacity() {
        return cacheCapacity;
    }

    public void clearCache() {
        synchronized (cellCache) {
            cellCache.clear();
        }
    }
}
