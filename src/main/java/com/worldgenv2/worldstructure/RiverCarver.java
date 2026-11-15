package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rivers that follow the land instead of cutting through it.
 *
 * <p>A river is a winding line (a warped noise ridge). Its <b>water level is taken from the local valley floor</b> at the
 * river's centerline, so a river in a lowland sits flush with the land and a river in higher country sits just as flush
 * at its own height; it never digs a canyon down to sea level. Across any cross-section the water is flat, and along the
 * river it steps down gently with the ground.
 *
 * <ul>
 *   <li><b>Channel</b>: a shallow, soft-bottomed trough a few blocks deep, filled with water up to the river level.</li>
 *   <li><b>Banks</b>: the surrounding ground blends into the river level over the bank width. High ground is eased down a
 *       little, low ground is raised to a beach, so the shore is flush with the water.</li>
 *   <li><b>Meander and width</b>: domain warping and width noise make rivers wander and widen into pools.</li>
 * </ul>
 */
public final class RiverCarver {

    /**
     * Everything a creator can tune about rivers, in blocks.
     *
     * @param spacing        roughly how far apart river lines are (bigger = fewer, longer rivers)
     * @param channelWidth   water surface width
     * @param valleyWidth    bank to bank width: the channel plus the soft banks on both sides
     * @param depth          how deep the channel is below the water level at its centre
     * @param meander        how strongly the river wanders (domain warp amplitude, blocks)
     * @param widthVariation 0..1, how much the width changes along the river
     */
    public record Settings(float spacing, float channelWidth, float valleyWidth, int depth, float meander, float widthVariation) {
        public static Settings defaults() { return new Settings(1000f, 30f, 60f, 3, 96f, 0.22f); }
    }

    /** Native (pre-river) surface height of the terrain at a block column. */
    @FunctionalInterface
    public interface HeightSampler {
        float height(int x, int z);
    }

    private static final int CELL = 16;
    /** Rivers fade out and stop above this water level (mountains), rather than carving through them. */
    private static final int ALPINE_START = 96, ALPINE_END = 124;

    private final float channelHalfWidth, valleyHalfWidth, meanderAmp, widthVar;
    private final FastNoiseLite riverNoise, warpX, warpZ, widthNoise;
    private final int seaLevel;
    private final int bedDepth;
    private com.worldgenv2.graph.ProceduralGraph riverGraph;
    // Graph mode (Hytale): the river follows the edges of a Graph Generator the creator builds from nodes
    private com.worldgenv2.graph.GraphGenerator graph;
    private long graphSeed;
    private float chHalfBlocks, valHalfBlocks;
    private float graphValleyRadius = 50.0f;

    private volatile HeightSampler sampler;
    private final ConcurrentHashMap<Long, Float> rawCells = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Float> minCells = new ConcurrentHashMap<>();

    public RiverCarver(long seed, int seaLevel, int bedDepth) {
        this(seed, seaLevel, new Settings(1000f, 30f, 60f, bedDepth, 96f, 0.22f));
    }

    public RiverCarver(long seed, int seaLevel, Settings st) {
        int s = (int) (seed ^ (seed >>> 32));
        this.seaLevel = seaLevel;
        this.bedDepth = Math.max(1, st.depth());
        float freq = 1.0f / Math.max(100f, st.spacing());
        float fScale = freq / 0.0010f;
        this.channelHalfWidth = 0.055f * (Math.max(2f, st.channelWidth()) / 30f) * fScale;
        this.valleyHalfWidth = Math.max(channelHalfWidth * 1.3f, 0.155f * (Math.max(4f, st.valleyWidth()) / 60f) * fScale);
        this.meanderAmp = Math.max(0f, st.meander());
        this.widthVar = Math.clamp(st.widthVariation(), 0f, 1f);

        this.riverNoise = new FastNoiseLite(s + 8101);
        this.riverNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.riverNoise.setFrequency(freq);
        this.warpX = new FastNoiseLite(s + 8102);
        this.warpX.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.warpX.setFrequency(0.0032f);
        this.warpZ = new FastNoiseLite(s + 8103);
        this.warpZ.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.warpZ.setFrequency(0.0032f);
        this.widthNoise = new FastNoiseLite(s + 8104);
        this.widthNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.widthNoise.setFrequency(0.0040f);
    }

    /** Gives the carver a way to read the land's own height, so the river level can follow the ground. */
    public void setHeightSampler(HeightSampler s) {
        this.sampler = s;
        rawCells.clear();
        minCells.clear();
    }

    public void setRiverGraph(com.worldgenv2.graph.ProceduralGraph riverGraph, float valleyRadius) {
        this.riverGraph = riverGraph;
        this.graphValleyRadius = Math.max(1.0f, valleyRadius);
    }

    /**
     * Makes the river follow the edges of a Graph Generator instead of the noise ridge. The graph decides where rivers run; the
     * settings still decide how wide, deep and wavy-edged they are.
     */
    public RiverCarver useGraph(com.worldgenv2.graph.GraphGenerator generator, long seed, Settings st) {
        this.graph = generator;
        this.graphSeed = seed;
        this.chHalfBlocks = Math.max(1f, st.channelWidth() / 2f);
        this.valHalfBlocks = Math.max(chHalfBlocks * 1.3f, st.valleyWidth() / 2f);
        return this;
    }

    public boolean usesGraph() { return graph != null; }

    private double[] nearestOnGraph(float x, float z) {
        return graph.nearestEdge(graphSeed, x, 0, z, valHalfBlocks * (1f + widthVar) + 2f, true, 0);
    }

    private float channelRatio() { return graph != null ? chHalfBlocks / valHalfBlocks : channelHalfWidth / valleyHalfWidth; }

    private float bankRatio() { return graph != null ? (chHalfBlocks + 2.5f) / valHalfBlocks : (channelHalfWidth + 0.030f) / valleyHalfWidth; }

    public com.worldgenv2.graph.ProceduralGraph getRiverGraph() {
        return riverGraph;
    }

    // ------------------------------------------------------------------ river geometry

    private float signedNoise(float x, float z) {
        float wx = x + warpX.getNoise(x, z) * meanderAmp;
        float wz = z + warpZ.getNoise(x, z) * meanderAmp;
        return riverNoise.getNoise(wx, wz);
    }

    /**
     * Effective distance metric in normalized river space: 0.0 at the centerline, 1.0 at the outer edge of the banks.
     */
    public float riverDistance(float x, float z) {
        if (graph != null) {
            double[] n = nearestOnGraph(x, z);
            if (n == null) return 1.0f;
            float wm = 1.0f + widthNoise.getNoise(x, z) * widthVar;
            return Math.clamp((float) (n[0] / (valHalfBlocks * wm)), 0.0f, 1.0f);
        }
        float n = Math.abs(signedNoise(x, z));
        float wMod = 1.0f + widthNoise.getNoise(x, z) * widthVar;
        float noiseDist = Math.clamp(n / (valleyHalfWidth * wMod), 0.0f, 1.0f);

        if (riverGraph != null && riverGraph.edgeCount() > 0) {
            float minGraphDist = Float.MAX_VALUE;
            for (com.worldgenv2.graph.GraphEdge edge : riverGraph.getEdges()) {
                com.worldgenv2.graph.GraphNode a = riverGraph.getNode(edge.nodeA());
                com.worldgenv2.graph.GraphNode b = riverGraph.getNode(edge.nodeB());
                if (a != null && b != null) {
                    float ax = (float) a.getPosition().x(), az = (float) a.getPosition().z();
                    float bx = (float) b.getPosition().x(), bz = (float) b.getPosition().z();
                    float dx = bx - ax, dz = bz - az;
                    float lenSq = dx * dx + dz * dz;
                    float t = lenSq > 1e-6f ? Math.clamp(((x - ax) * dx + (z - az) * dz) / lenSq, 0.0f, 1.0f) : 0.0f;
                    float d = (float) Math.hypot(x - (ax + t * dx), z - (az + t * dz));
                    minGraphDist = Math.min(minGraphDist, d);
                }
            }
            return Math.min(noiseDist, Math.clamp(minGraphDist / graphValleyRadius, 0.0f, 1.0f));
        }
        return noiseDist;
    }

    /** True when (x, z) lies inside the water-filled river channel proper. */
    public boolean isChannel(float x, float z) {
        return riverDistance(x, z) <= channelRatio();
    }

    /** True when (x, z) lies on the immediate riparian shoreline (water's edge and a few blocks back). */
    public boolean isRiparianBank(float x, float z) {
        float dist = riverDistance(x, z);
        return dist > channelRatio() && dist <= bankRatio();
    }

    // ------------------------------------------------------------------ water level

    private float rawCell(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Float v = rawCells.get(key);
        if (v == null) {
            HeightSampler s = sampler;
            v = s == null ? (float) seaLevel : s.height(cx * CELL + CELL / 2, cz * CELL + CELL / 2);
            rawCells.put(key, v);
        }
        return v;
    }

    /** Lowest ground height among the four cells around a grid corner: the local valley floor. */
    private float minCell(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Float v = minCells.get(key);
        if (v == null) {
            v = Math.min(Math.min(rawCell(cx, cz), rawCell(cx + 1, cz)), Math.min(rawCell(cx, cz + 1), rawCell(cx + 1, cz + 1)));
            minCells.put(key, v);
        }
        return v;
    }

    private float valleyFloor(float x, float z) {
        float fx = x / CELL - 0.5f, fz = z / CELL - 0.5f;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        float tx = fx - ix, tz = fz - iz;
        float a = minCell(ix, iz), b = minCell(ix + 1, iz), c = minCell(ix, iz + 1), d = minCell(ix + 1, iz + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    /**
     * Water level of the river that passes near (x, z). Every point of a cross-section maps to the same centerline point,
     * so the surface is flat across the river and follows the valley floor along it.
     */
    public int levelAt(float x, float z) {
        if (sampler == null) return seaLevel;
        if (graph != null) {
            double[] near = nearestOnGraph(x, z);
            if (near == null) return seaLevel;
            return Math.max(seaLevel, (int) Math.floor(valleyFloor((float) near[1], (float) near[3])) - 1);
        }
        float n = signedNoise(x, z);
        float h = 3.0f;
        float gx = (signedNoise(x + h, z) - signedNoise(x - h, z)) / (2 * h);
        float gz = (signedNoise(x, z + h) - signedNoise(x, z - h)) / (2 * h);
        float g2 = gx * gx + gz * gz;
        float cx = x, cz = z;
        if (g2 > 1e-10f) {
            float k = n / g2;
            float mx = k * gx, mz = k * gz;
            float len = (float) Math.sqrt(mx * mx + mz * mz);
            if (len > 40f) { mx *= 40f / len; mz *= 40f / len; }
            cx = x - mx;
            cz = z - mz;
        }
        return Math.max(seaLevel, (int) Math.floor(valleyFloor(cx, cz)) - 1);
    }

    /** Water level inside the river channel at (x, z), or {@link Integer#MIN_VALUE} when (x, z) is not river water. */
    public int channelWaterLevel(float x, float z) {
        if (!isChannel(x, z)) return Integer.MIN_VALUE;
        int level = levelAt(x, z);
        return level >= ALPINE_END ? Integer.MIN_VALUE : level;
    }

    // ------------------------------------------------------------------ carving

    /**
     * Shapes the density column: a shallow channel below the river level and banks that blend flush into the land.
     */
    public void carveColumn(float x, float z, int minY, int maxY, float[] col) {
        float dist = riverDistance(x, z);
        if (dist >= 0.999f) return;

        int height = maxY - minY;
        int surfaceIdx = -1;
        for (int i = height - 1; i >= 0; i--) {
            if (col[i] > 0.0f) { surfaceIdx = i; break; }
        }
        if (surfaceIdx < 0) return;
        int surfaceY = minY + surfaceIdx;

        int level = levelAt(x, z);
        // rivers do not cut across open ocean, and fade out before reaching mountains
        float landFade = Math.clamp((surfaceY - (seaLevel - 2)) / 4.0f, 0.0f, 1.0f);
        float alpineFade = 1.0f - Math.clamp((level - ALPINE_START) / (float) (ALPINE_END - ALPINE_START), 0.0f, 1.0f);
        float blendRate = Math.min(1.0f, landFade * alpineFade * 1.25f);
        if (blendRate <= 0.001f) return;

        float channelRatio = channelRatio();
        float targetY;
        if (dist <= channelRatio) {
            // shallow trough: deepest in the middle, rising to the water level at the water's edge
            float t = dist / channelRatio;
            float bedY = level - bedDepth;
            targetY = bedY + (level - bedY) * (t * t);
        } else {
            // banks: flush with the water at the edge, easing into the native ground at the outer edge
            float vt = (dist - channelRatio) / (1.0f - channelRatio);
            float curve = vt * vt * (3.0f - 2.0f * vt);
            float shore = level + 1.0f;
            targetY = shore + (surfaceY - shore) * curve;
        }

        int startY = Math.max(minY, level - bedDepth - 6);
        int endY = Math.min(maxY - 1, Math.max(surfaceY, (int) Math.ceil(targetY)) + 2);
        for (int y = startY; y <= endY; y++) {
            int i = y - minY;
            float targetDensity = (targetY - y) * 0.12f;
            if (col[i] > targetDensity) {
                col[i] += (targetDensity - col[i]) * blendRate;          // ease high ground down
            } else if (y <= targetY) {
                col[i] += (targetDensity - col[i]) * blendRate;          // raise low ground up to the shore
            }
        }
    }
}
