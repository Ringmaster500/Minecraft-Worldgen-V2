package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;

import java.util.List;

/**
 * High-performance 2D cellular partitioning map (BiCarta).
 * Evaluates multiplicatively weighted 5x5 Voronoi point generators with organic 2D coordinate
 * domain warping, distinct neighbor tracking, and C1-continuous multi-biome boundary weights
 * that eliminate sizeModifier asymmetry, cell-window popping, and triple-junction seams.
 */
public final class BiCarta<T> {

    private static final int SEARCH_RADIUS = 2;
    private static final int WINDOW_CELLS = (SEARCH_RADIUS * 2 + 1) * (SEARCH_RADIUS * 2 + 1); // 25 cells (5x5)
    private static final float DEFAULT_WARP_AMPLITUDE = 64.0f;
    private static final float DEFAULT_WARP_FREQUENCY = 0.0028f;
    private static final float DETAIL_WARP_RATIO = 0.35f;
    private static final float DETAIL_WARP_FREQUENCY = 0.011f;
    private static final float MICRO_WARP_RATIO = 0.10f;
    private static final float MICRO_WARP_FREQUENCY = 0.032f;
    private static final float MACRO_CLIMATE_FREQUENCY = 0.00032f;
    private static final float CONTINENTAL_FREQUENCY = 0.00040f;
    private static final float TEMPERATURE_FREQUENCY = 0.00055f;
    private static final float HUMIDITY_FREQUENCY = 0.00065f;
    private static final float CONTINENTAL_AXIS_WEIGHT = 1.6f;
    private static final float SPAWN_LAND_RADIUS = 1400.0f;

    public record CellQueryResult<T>(
        T primary,
        T secondary,
        T tertiary,
        float distanceToEdge,
        float blendFactor,
        float tertiaryBlendFactor
    ) {
        public CellQueryResult(T primary, T secondary, float distanceToEdge, float blendFactor) {
            this(primary, secondary, primary, distanceToEdge, blendFactor, 0.0f);
        }
    }

    private final java.util.concurrent.CopyOnWriteArrayList<TileBiome> tileBiomes;
    private volatile float totalWeight;
    private final int cellSize;
    private final long seed;
    private final float warpAmplitude;
    private final FastNoiseLite warpNoiseX;
    private final FastNoiseLite warpNoiseZ;
    private final FastNoiseLite detailWarpX;
    private final FastNoiseLite detailWarpZ;
    private final FastNoiseLite microWarpX;
    private final FastNoiseLite microWarpZ;
    private final FastNoiseLite macroClimateNoise;
    private final FastNoiseLite continentalNoise;
    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;
    private final boolean climateDriven;
    private final java.util.concurrent.ConcurrentHashMap<Long, TileBiome> cellBiomeCache = new java.util.concurrent.ConcurrentHashMap<>();

    public BiCarta(List<TileBiome> tileBiomes, int cellSize, long seed) {
        this(tileBiomes, cellSize, seed, DEFAULT_WARP_AMPLITUDE);
    }

    public BiCarta(List<TileBiome> tileBiomes, int cellSize, long seed, float warpAmplitude) {
        this.tileBiomes = new java.util.concurrent.CopyOnWriteArrayList<>(tileBiomes);
        this.cellSize = Math.max(16, cellSize);
        this.seed = seed;
        this.warpAmplitude = Math.max(0.0f, warpAmplitude);

        int seedX = (int) (seed ^ (seed >>> 32) ^ 0x5DEECE66DL);
        int seedZ = (int) ((seed ^ 0xB504F333L) + 1442695041L);

        this.warpNoiseX = new FastNoiseLite(seedX);
        this.warpNoiseX.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.warpNoiseX.setFrequency(DEFAULT_WARP_FREQUENCY);
        this.warpNoiseX.setFractalType(FastNoiseLite.FractalType.FBm);
        this.warpNoiseX.setFractalOctaves(2);

        this.warpNoiseZ = new FastNoiseLite(seedZ);
        this.warpNoiseZ.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.warpNoiseZ.setFrequency(DEFAULT_WARP_FREQUENCY);
        this.warpNoiseZ.setFractalType(FastNoiseLite.FractalType.FBm);
        this.warpNoiseZ.setFractalOctaves(2);

        this.detailWarpX = new FastNoiseLite(seedX ^ 0x7F4A7C15);
        this.detailWarpX.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.detailWarpX.setFrequency(DETAIL_WARP_FREQUENCY);

        this.detailWarpZ = new FastNoiseLite(seedZ ^ 0x94D049BB);
        this.detailWarpZ.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.detailWarpZ.setFrequency(DETAIL_WARP_FREQUENCY);

        this.microWarpX = new FastNoiseLite(seedX ^ 0x6C8E9CF5);
        this.microWarpX.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.microWarpX.setFrequency(MICRO_WARP_FREQUENCY);

        this.microWarpZ = new FastNoiseLite(seedZ ^ 0x517CC1B7);
        this.microWarpZ.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.microWarpZ.setFrequency(MICRO_WARP_FREQUENCY);

        this.macroClimateNoise = new FastNoiseLite((int) (seed ^ 0x9E3779B9L));
        this.macroClimateNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.macroClimateNoise.setFrequency(MACRO_CLIMATE_FREQUENCY);
        this.macroClimateNoise.setFractalType(FastNoiseLite.FractalType.None);

        float weightSum = 0.0f;
        boolean allHaveClimate = true;
        for (TileBiome biome : this.tileBiomes) {
            weightSum += biome.weight();
            if (biome.climate() == null) {
                allHaveClimate = false;
            }
        }
        this.totalWeight = weightSum;
        this.climateDriven = allHaveClimate && !this.tileBiomes.isEmpty();

        this.continentalNoise = new FastNoiseLite((int) (seed ^ 0x1B873593L));
        this.continentalNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.continentalNoise.setFrequency(CONTINENTAL_FREQUENCY);
        this.continentalNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.continentalNoise.setFractalOctaves(3);

        this.temperatureNoise = new FastNoiseLite((int) (seed ^ 0x2545F491L));
        this.temperatureNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.temperatureNoise.setFrequency(TEMPERATURE_FREQUENCY);
        this.temperatureNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.temperatureNoise.setFractalOctaves(2);

        this.humidityNoise = new FastNoiseLite((int) (seed ^ 0x68E31DA4L));
        this.humidityNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.humidityNoise.setFrequency(HUMIDITY_FREQUENCY);
        this.humidityNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.humidityNoise.setFractalOctaves(2);
    }

    /**
     * Resolves the primary TileBiome, nearest distinct neighbor TileBiomes (secondary & tertiary),
     * and C1-continuous boundary blend weights at continuous world coordinates using a 5x5
     * domain-warped Voronoi lookup.
     */
    public CellQueryResult<TileBiome> query(float x, float z) {
        if (tileBiomes.isEmpty()) {
            throw new IllegalStateException("BiCarta contains no registered TileBiomes.");
        }
        if (tileBiomes.size() == 1) {
            TileBiome only = tileBiomes.getFirst();
            return new CellQueryResult<>(only, only, only, Float.MAX_VALUE, 0.0f, 0.0f);
        }

        // Apply three-tier 2D coordinate domain warp (macro meander + mesoscale coves + fine shoreline)
        // so Voronoi borders never appear as straight geometric polygon lines
        float wx = x;
        float wz = z;
        if (warpAmplitude > 0.0f) {
            float detailAmp = warpAmplitude * DETAIL_WARP_RATIO;
            float microAmp = warpAmplitude * MICRO_WARP_RATIO;
            wx += warpNoiseX.getNoise(x, z) * warpAmplitude
                + detailWarpX.getNoise(x, z) * detailAmp
                + microWarpX.getNoise(x, z) * microAmp;
            wz += warpNoiseZ.getNoise(x, z) * warpAmplitude
                + detailWarpZ.getNoise(x, z) * detailAmp
                + microWarpZ.getNoise(x, z) * microAmp;
        }

        int cellX = (int) Math.floor(wx / cellSize);
        int cellZ = (int) Math.floor(wz / cellSize);

        float[] weightedDistSq = new float[WINDOW_CELLS];
        TileBiome[] cellBiomes = new TileBiome[WINDOW_CELLS];

        // Pass 1: Evaluate all 25 Voronoi cells in the 5x5 horizon and locate the closest weighted cell
        int bestIdx = 0;
        float minWeightedDistSq = Float.MAX_VALUE;
        int idx = 0;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;

                long cellHash = hashCoordinates(cx, cz, seed);
                float jitterX = (float) ((cellHash & 0xFFFF) / 65535.0) * 0.72f + 0.14f;
                float jitterZ = (float) (((cellHash >> 16) & 0xFFFF) / 65535.0) * 0.72f + 0.14f;

                float pointX = (cx + jitterX) * cellSize;
                float pointZ = (cz + jitterZ) * cellSize;

                float rawDistSq = (wx - pointX) * (wx - pointX) + (wz - pointZ) * (wz - pointZ);
                TileBiome biomeAtPoint = cachedClimaticBiome(cx, cz, pointX, pointZ, cellHash);

                // Clamp sizeModifier to [0.5f, 1.8f] so Voronoi influence stays well inside the 5x5 window
                float sizeMod = Math.clamp(biomeAtPoint.sizeModifier(), 0.5f, 1.8f);
                float wDistSq = rawDistSq / (sizeMod * sizeMod);

                weightedDistSq[idx] = wDistSq;
                cellBiomes[idx] = biomeAtPoint;

                if (wDistSq < minWeightedDistSq) {
                    minWeightedDistSq = wDistSq;
                    bestIdx = idx;
                }
                idx++;
            }
        }

        TileBiome primaryBiome = cellBiomes[bestIdx];
        int primaryId = primaryBiome.id();

        // Pass 2: Find the closest and second-closest DISTINCT biomes in the 5x5 window
        TileBiome secondBiome = null;
        float secondWeightedDistSq = Float.MAX_VALUE;

        for (int i = 0; i < WINDOW_CELLS; i++) {
            if (cellBiomes[i].id() != primaryId && weightedDistSq[i] < secondWeightedDistSq) {
                secondWeightedDistSq = weightedDistSq[i];
                secondBiome = cellBiomes[i];
            }
        }

        if (secondBiome == null) {
            return new CellQueryResult<>(primaryBiome, primaryBiome, primaryBiome, Float.MAX_VALUE, 0.0f, 0.0f);
        }

        int secondId = secondBiome.id();
        TileBiome thirdBiome = null;
        float thirdWeightedDistSq = Float.MAX_VALUE;

        for (int i = 0; i < WINDOW_CELLS; i++) {
            int candidateId = cellBiomes[i].id();
            if (candidateId != primaryId && candidateId != secondId && weightedDistSq[i] < thirdWeightedDistSq) {
                thirdWeightedDistSq = weightedDistSq[i];
                thirdBiome = cellBiomes[i];
            }
        }

        // Convert weighted squared distances to weighted distances w_k = r_k / s_k.
        // At any Voronoi boundary, w_primary == w_neighbor. Using each biome's own intrinsic
        // sizeModifier and defaultTransitionDistance on (w_b - w_min) guarantees that the
        // distance metric and smoothstep weights are 100% invariant to which biome is primary,
        // eliminating sizeModifier border pops and triple-junction cliff seams.
        float w1 = (float) Math.sqrt(minWeightedDistSq);
        float w2 = (float) Math.sqrt(secondWeightedDistSq);

        float secondSizeMod = Math.clamp(secondBiome.sizeModifier(), 0.5f, 1.8f);
        float distToEdge = Math.max(0.0f, (w2 - w1) * 0.5f * secondSizeMod);

        float t2 = Math.clamp(
            1.0f - (distToEdge / Math.max(1.0f, secondBiome.defaultTransitionDistance())),
            0.0f,
            1.0f
        );
        float alpha2 = t2 * t2 * (3.0f - 2.0f * t2);

        float alpha3 = 0.0f;
        if (thirdBiome != null) {
            float w3 = (float) Math.sqrt(thirdWeightedDistSq);
            float thirdSizeMod = Math.clamp(thirdBiome.sizeModifier(), 0.5f, 1.8f);
            float distToThird = Math.max(0.0f, (w3 - w1) * 0.5f * thirdSizeMod);
            float t3 = Math.clamp(
                1.0f - (distToThird / Math.max(1.0f, thirdBiome.defaultTransitionDistance())),
                0.0f,
                1.0f
            );
            alpha3 = t3 * t3 * (3.0f - 2.0f * t3);
        } else {
            thirdBiome = primaryBiome;
        }

        // Normalize smoothstep weights across (primary=1.0, secondary=alpha2, tertiary=alpha3):
        // - Interior of primary biome: alpha2 = 0, alpha3 = 0 -> W1 = 1.0, W2 = 0.0, W3 = 0.0
        // - 2-biome boundary (w1 == w2, alpha3 == 0): alpha2 = 1 -> W1 = 0.5, W2 = 0.5
        // - 3-biome triple junction (w1 == w2 == w3): alpha2 = 1, alpha3 = 1 -> W1 = 1/3, W2 = 1/3, W3 = 1/3
        float weightNorm = 1.0f / (1.0f + alpha2 + alpha3);
        float secondaryWeight = alpha2 * weightNorm;
        float tertiaryWeight = alpha3 * weightNorm;

        return new CellQueryResult<>(
            primaryBiome,
            secondBiome,
            thirdBiome,
            distToEdge,
            secondaryWeight,
            tertiaryWeight
        );
    }

    /**
     * Selects a TileBiome using a continuous 2D macro climate field (plus subtle cell jitter)
     * so adjacent Voronoi cells cluster into cohesive climatic zones and extreme opposite biomes
     * (first vs last in the list, e.g., AridDunes vs AlpinePeaks) are always separated by
     * intermediate temperate buffer biomes.
     */
    /**
     * Per-cell memoization: a cell's biome depends only on its jittered point, so the (comparatively expensive)
     * climate lookup runs once per cell instead of 25 times per column query.
     */
    private TileBiome cachedClimaticBiome(int cx, int cz, float pointX, float pointZ, long cellHash) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        TileBiome cached = cellBiomeCache.get(key);
        if (cached != null) {
            return cached;
        }
        TileBiome selected = selectClimaticBiome(pointX, pointZ, cellHash);
        if (cellBiomeCache.size() > 262_144) {
            cellBiomeCache.clear();
        }
        cellBiomeCache.put(key, selected);
        return selected;
    }

    /** Optional zone restriction: returns the biome ids a cell at (x, z) may use, or null for no restriction. */
    private volatile java.util.function.BiFunction<Float, Float, java.util.Set<Integer>> zoneFilter;

    public void setZoneFilter(java.util.function.BiFunction<Float, Float, java.util.Set<Integer>> filter) {
        this.zoneFilter = filter;
        this.cellBiomeCache.clear();
    }

    private List<TileBiome> poolFor(float x, float z) {
        var f = zoneFilter;
        if (f == null) return tileBiomes;
        java.util.Set<Integer> ids = f.apply(x, z);
        if (ids == null || ids.isEmpty()) return tileBiomes;
        List<TileBiome> out = new java.util.ArrayList<>();
        for (TileBiome b : tileBiomes) if (ids.contains(b.id())) out.add(b);
        return out.isEmpty() ? tileBiomes : out;
    }

    private TileBiome selectClimaticBiome(float pointX, float pointZ, long cellHash) {
        List<TileBiome> pool = poolFor(pointX, pointZ);
        if (climateDriven) {
            return selectByClimateSpace(pointX, pointZ, pool);
        }
        float climate = macroClimateNoise.getNoise(pointX, pointZ);
        float microJitter = (((cellHash >>> 32) & 0xFFFF) / 65535.0f - 0.5f) * 0.03f;
        float u = Math.clamp(0.5f + climate * 0.55f + microJitter, 0.0f, 1.0f);
        float poolWeight = totalWeight;
        if (pool != tileBiomes) {
            poolWeight = 0.0f;
            for (TileBiome b : pool) poolWeight += b.weight();
        }
        float target = u * poolWeight;
        float accumulated = 0.0f;
        for (TileBiome biome : pool) {
            accumulated += biome.weight();
            if (target <= accumulated) {
                return biome;
            }
        }
        return pool.getLast();
    }

    /**
     * Samples the 3-axis macro climate (continentalness, temperature, humidity) at a Voronoi cell point.
     * Continentalness receives a radial land bias around the world origin so the spawn area is always
     * on a continent rather than in the middle of an ocean.
     */
    public ClimateTarget sampleClimate(float x, float z) {
        float c = Math.clamp(continentalNoise.getNoise(x, z) * 1.45f, -1.0f, 1.0f);
        float distSq = x * x + z * z;
        if (distSq < SPAWN_LAND_RADIUS * SPAWN_LAND_RADIUS) {
            float r = (float) Math.sqrt(distSq) / SPAWN_LAND_RADIUS;
            float bias = (1.0f - r) * (1.0f - r) * 1.2f;
            c = Math.clamp(c + bias, -1.0f, 1.0f);
            // keep the spawn plateau lowland rather than mountain interior
            c = Math.min(c, 0.20f + r * 0.7f);
        }
        float t = Math.clamp(temperatureNoise.getNoise(x, z) * 1.5f, -1.0f, 1.0f);
        float h = Math.clamp(humidityNoise.getNoise(x, z) * 1.5f, -1.0f, 1.0f);
        return new ClimateTarget(c, t, h);
    }

    /**
     * Nearest-target lookup in (continentalness, temperature, humidity) space. The weight of each
     * biome acts as a rarity bias (distance is divided by sqrt(weight)).
     */
    private TileBiome selectByClimateSpace(float pointX, float pointZ, List<TileBiome> pool) {
        ClimateTarget sample = sampleClimate(pointX, pointZ);
        TileBiome best = null;
        float bestScore = Float.MAX_VALUE;
        for (TileBiome biome : pool) {
            ClimateTarget target = biome.climate();
            if (target == null) {
                continue;
            }
            float dc = (sample.continentalness() - target.continentalness()) * CONTINENTAL_AXIS_WEIGHT;
            float dt = sample.temperature() - target.temperature();
            float dh = sample.humidity() - target.humidity();
            float score = (dc * dc + dt * dt + dh * dh) / (float) Math.sqrt(biome.weight());
            if (score < bestScore) {
                bestScore = score;
                best = biome;
            }
        }
        return best != null ? best : pool.getFirst();
    }

    private static long hashCoordinates(int x, int z, long seed) {
        long h = seed + x * 374761393L + z * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return h ^ (h >> 16);
    }

    public List<TileBiome> getTileBiomes() {
        return List.copyOf(tileBiomes);
    }

    public TileBiome findTileBiome(int id) {
        for (TileBiome b : tileBiomes) {
            if (b.id() == id) return b;
        }
        return null;
    }

    public TileBiome findTileBiomeByName(String name) {
        if (name == null) return null;
        String clean = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        String norm = clean.replace("_", "").toLowerCase(java.util.Locale.ROOT);
        for (TileBiome b : tileBiomes) {
            String bn = b.name().replace("_", "").toLowerCase(java.util.Locale.ROOT);
            if (bn.equals(norm)) return b;
        }
        return null;
    }

    /**
     * Hot-swaps a TileBiome in-place for live Studio chunk editing and clears the cell biome cache.
     */
    public void updateTileBiome(TileBiome updated) {
        for (int i = 0; i < tileBiomes.size(); i++) {
            if (tileBiomes.get(i).id() == updated.id() || tileBiomes.get(i).name().equalsIgnoreCase(updated.name())) {
                tileBiomes.set(i, updated);
                break;
            }
        }
        float weightSum = 0.0f;
        for (TileBiome b : tileBiomes) {
            weightSum += b.weight();
        }
        this.totalWeight = weightSum;
        this.cellBiomeCache.clear();
    }
}
