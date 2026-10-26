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
        ProceduralGraph generated = generate(seed, cellBounds);

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
