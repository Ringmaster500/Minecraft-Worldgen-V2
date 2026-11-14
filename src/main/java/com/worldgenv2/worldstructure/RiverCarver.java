package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;

/**
 * Natural organic river network and valley sculpt engine.
 *
 * <ul>
 *   <li><b>Wide Gentle Valleys (40–70 blocks)</b> - slopes the surrounding terrain gently down
 *       toward water level at mild angles (&lt; 22°), completely eliminating steep artificial quarry
 *       cliff walls and keeping rich biome vegetation (grass, savanna dirt, flowers) intact on banks.</li>
 *   <li><b>Parabolic U-Shaped Riverbed</b> - smoothly descends from water level (Y = 63) to bed
 *       depth (Y = 57) in a smooth concave basin rather than a sharp V-ditch.</li>
 *   <li><b>Meander & Width Modulation</b> - 2D domain warping combined with width noise makes rivers
 *       naturally meander, widen into scenic river pools, and narrow through canyons.</li>
 *   <li><b>River Sediment Bed</b> - identifies river channel and bank positions so the generator
 *       lines beds with natural sediments (gravel, sand, clay) rather than bare gray stone.</li>
 * </ul>
 */
public final class RiverCarver {

    // Thresholds in absolute noise space (|N|)
    /**
     * Everything a creator can tune about rivers, in blocks.
     *
     * @param spacing        roughly how far apart river lines are (bigger = fewer, longer rivers)
     * @param channelWidth   water surface width
     * @param valleyWidth    width of the gentle valley around the water
     * @param depth          how far below sea level the riverbed sits
     * @param meander        how strongly the river wanders (domain warp amplitude, blocks)
     * @param widthVariation 0..1, how much the width changes along the river
     */
    public record Settings(float spacing, float channelWidth, float valleyWidth, int depth, float meander, float widthVariation) {
        public static Settings defaults() { return new Settings(1000f, 30f, 60f, 6, 96f, 0.22f); }
    }

    private final float coreHalfWidth, channelHalfWidth, valleyHalfWidth, meanderAmp, widthVar;

    private final FastNoiseLite riverNoise;
    private final FastNoiseLite warpX;
    private final FastNoiseLite warpZ;
    private final FastNoiseLite widthNoise;
    private final int seaLevel;
    private final int bedDepth;
    private com.worldgenv2.graph.ProceduralGraph riverGraph;
    private float graphValleyRadius = 50.0f;

    public RiverCarver(long seed, int seaLevel, int bedDepth) {
        this(seed, seaLevel, Settings.defaults().depth() == bedDepth ? Settings.defaults()
                : new Settings(1000f, 30f, 60f, bedDepth, 96f, 0.22f));
    }

    public RiverCarver(long seed, int seaLevel, Settings st) {
        int s = (int) (seed ^ (seed >>> 32));
        this.seaLevel = seaLevel;
        this.bedDepth = Math.max(1, st.depth());
        float freq = 1.0f / Math.max(100f, st.spacing());
        float fScale = freq / 0.0010f;
        this.channelHalfWidth = 0.055f * (Math.max(2f, st.channelWidth()) / 30f) * fScale;
        this.valleyHalfWidth = Math.max(channelHalfWidth * 1.3f, 0.155f * (Math.max(4f, st.valleyWidth()) / 60f) * fScale);
        this.coreHalfWidth = channelHalfWidth * (0.024f / 0.055f);
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

    public void setRiverGraph(com.worldgenv2.graph.ProceduralGraph riverGraph, float valleyRadius) {
        this.riverGraph = riverGraph;
        this.graphValleyRadius = Math.max(1.0f, valleyRadius);
    }

    public com.worldgenv2.graph.ProceduralGraph getRiverGraph() {
        return riverGraph;
    }

    /**
     * Evaluates effective distance metric in normalized river space:
     * 0.0 at the centerline, 1.0 at the outer edge of the valley basin.
     */
    public float riverDistance(float x, float z) {
        float wx = x + warpX.getNoise(x, z) * meanderAmp;
        float wz = z + warpZ.getNoise(x, z) * meanderAmp;
        float n = Math.abs(riverNoise.getNoise(wx, wz));

        // Modulate valley width organically along the river
        float wMod = 1.0f + widthNoise.getNoise(x, z) * widthVar;
        float effectiveValley = valleyHalfWidth * wMod;

        float noiseDist = Math.clamp(n / effectiveValley, 0.0f, 1.0f);

        if (riverGraph != null && riverGraph.edgeCount() > 0) {
            float minGraphDist = Float.MAX_VALUE;
            for (com.worldgenv2.graph.GraphEdge edge : riverGraph.getEdges()) {
                com.worldgenv2.graph.GraphNode a = riverGraph.getNode(edge.nodeA());
                com.worldgenv2.graph.GraphNode b = riverGraph.getNode(edge.nodeB());
                if (a != null && b != null) {
                    float ax = (float) a.getPosition().x();
                    float az = (float) a.getPosition().z();
                    float bx = (float) b.getPosition().x();
                    float bz = (float) b.getPosition().z();

                    float dx = bx - ax;
                    float dz = bz - az;
                    float lenSq = dx * dx + dz * dz;
                    float t = 0.0f;
                    if (lenSq > 1e-6f) {
                        t = Math.clamp(((x - ax) * dx + (z - az) * dz) / lenSq, 0.0f, 1.0f);
                    }
                    float px = ax + t * dx;
                    float pz = az + t * dz;
                    float d = (float) Math.hypot(x - px, z - pz);
                    minGraphDist = Math.min(minGraphDist, d);
                }
            }
            float graphDistNorm = Math.clamp(minGraphDist / graphValleyRadius, 0.0f, 1.0f);
            return Math.min(noiseDist, graphDistNorm);
        }

        return noiseDist;
    }

    /**
     * True when (x, z) lies inside the water-filled river channel proper.
     */
    public boolean isChannel(float x, float z) {
        float dist = riverDistance(x, z);
        float channelRatio = channelHalfWidth / valleyHalfWidth;
        return dist <= channelRatio;
    }

    /**
     * True when (x, z) lies on the immediate riparian shoreline (water's edge up to 4 blocks back).
     */
    public boolean isRiparianBank(float x, float z) {
        float dist = riverDistance(x, z);
        float channelRatio = channelHalfWidth / valleyHalfWidth;
        float bankRatio = (channelHalfWidth + 0.030f) / valleyHalfWidth;
        return dist > channelRatio && dist <= bankRatio;
    }

    /**
     * Carves a natural U-shaped river valley with gentle slopes down to sea level, then
     * hollows out the submerged riverbed below sea level.
     */
    public void carveColumn(float x, float z, int minY, int maxY, float[] col) {
        float dist = riverDistance(x, z);
        if (dist >= 0.999f) {
            return;
        }

        int height = maxY - minY;
        int surfaceIdx = -1;
        for (int i = height - 1; i >= 0; i--) {
            if (col[i] > 0.0f) {
                surfaceIdx = i;
                break;
            }
        }
        if (surfaceIdx < 0) {
            return;
        }
        int surfaceY = minY + surfaceIdx;

        // Rivers do not cut across oceans or low shallows, and fade out before cutting extreme alpine peaks
        float landFade = Math.clamp((surfaceY - (seaLevel - 1)) / 5.0f, 0.0f, 1.0f);
        float alpineFade = 1.0f - Math.clamp((surfaceY - 96) / 24.0f, 0.0f, 1.0f);
        float eligibility = landFade * alpineFade;
        if (eligibility <= 0.001f) {
            return;
        }

        // Smooth cubic Hermite profile for the valley basin:
        // dist in [0.0, 1.0] -> valleyFactor in [1.0, 0.0]
        float t = 1.0f - dist;
        float valleyFactor = t * t * (3.0f - 2.0f * t) * eligibility;

        float channelRatio = channelHalfWidth / valleyHalfWidth;
        float coreRatio = coreHalfWidth / valleyHalfWidth;

        // Target surface height in the valley
        // In the outer valley: terrain slopes gently down toward seaLevel + 1
        // In the channel: terrain dips in a smooth parabola down to bedY = seaLevel - bedDepth
        float targetY;
        if (dist > channelRatio) {
            // Outer valley: gentle transition from native hill height down to seaLevel + 1
            float valleyT = (dist - channelRatio) / (1.0f - channelRatio);
            float valleyCurve = valleyT * valleyT * (3.0f - 2.0f * valleyT);
            targetY = (seaLevel + 1.2f) + (surfaceY - (seaLevel + 1.2f)) * valleyCurve;
        } else {
            // Inside the river channel: smooth parabolic bed from seaLevel down to bedY
            float channelT = Math.clamp(dist / channelRatio, 0.0f, 1.0f);
            float bedY = (float) (seaLevel - bedDepth);
            // Parabola: at center (dist=0) -> bedY; at channel bank (dist=channelRatio) -> seaLevel
            targetY = bedY + (seaLevel - bedY) * (channelT * channelT);
        }

        // Apply smooth vertical density pull toward the target surface
        // Below targetY: positive (solid bed). Above targetY: negative (water or open sky).
        float blendRate = Math.min(1.0f, valleyFactor * 1.35f);
        int startY = Math.max(minY, (int) (seaLevel - bedDepth - 10));
        int endY = Math.min(maxY - 1, surfaceY + 2);

        for (int y = startY; y <= endY; y++) {
            int i = y - minY;
            // Target density gradient: ~0.10 slope near the surface gives gentle ~15–20° bank angles
            float targetDensity = (targetY - y) * 0.12f;
            // Smoothly blend native density toward target valley density
            if (col[i] > targetDensity) {
                col[i] += (targetDensity - col[i]) * blendRate;
            }
        }
    }
}
