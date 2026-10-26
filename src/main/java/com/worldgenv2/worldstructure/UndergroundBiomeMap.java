package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * 3D placement of {@link UndergroundBiome} volumes. A low-frequency volumetric zone mask decides whether a
 * point belongs to any special underground biome (the rest stays the plain surface biome), and a second
 * volumetric selector noise picks among the biomes eligible at that Y and under that surface biome.
 */
public final class UndergroundBiomeMap {

    private static final float ZONE_THRESHOLD = -0.05f;

    private final List<UndergroundBiome> biomes;
    private final FastNoiseLite zoneNoise;
    private final FastNoiseLite selectorNoise;

    public UndergroundBiomeMap(List<UndergroundBiome> biomes, long seed) {
        this.biomes = List.copyOf(biomes);
        int s = (int) (seed ^ (seed >>> 32));
        this.zoneNoise = new FastNoiseLite(s + 9101);
        this.zoneNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.zoneNoise.setFrequency(0.0055f);
        this.selectorNoise = new FastNoiseLite(s + 9102);
        this.selectorNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.selectorNoise.setFrequency(0.0030f);
    }

    public boolean isEmpty() {
        return biomes.isEmpty();
    }

    public int maxY() {
        int max = Integer.MIN_VALUE;
        for (UndergroundBiome b : biomes) {
            max = Math.max(max, b.maxY());
        }
        return max;
    }

    /**
     * @param surfaceBiomeId lazily resolves the surface TileBiome id at (x, z); only invoked when a candidate
     *                       has surface restrictions
     * @return the underground biome at (x, y, z) or null for plain (surface-biome) rock
     */
    public UndergroundBiome query(int x, int y, int z, IntSupplier surfaceBiomeId) {
        if (biomes.isEmpty() || y > maxY()) {
            return null;
        }
        if (zoneNoise.getNoise(x, y * 1.5f, z) < ZONE_THRESHOLD) {
            return null;
        }

        float selector = selectorNoise.getNoise(x, y * 1.3f, z);
        float u = Math.clamp(0.5f + selector * 0.85f, 0.0f, 0.9999f);

        int surfaceId = Integer.MIN_VALUE;
        boolean surfaceResolved = false;

        // Weighted pick among eligible biomes, re-evaluating eligibility with surface restrictions
        float total = 0.0f;
        for (UndergroundBiome b : biomes) {
            if (y >= b.minY() && y <= b.maxY()) {
                total += b.weight();
            }
        }
        if (total <= 0.0f) {
            return null;
        }
        float target = u * total;
        float acc = 0.0f;
        UndergroundBiome picked = null;
        for (UndergroundBiome b : biomes) {
            if (y < b.minY() || y > b.maxY()) {
                continue;
            }
            acc += b.weight();
            if (target <= acc) {
                picked = b;
                break;
            }
        }
        if (picked == null) {
            return null;
        }
        if (!picked.allowedSurfaceIds().isEmpty()) {
            surfaceId = surfaceBiomeId.getAsInt();
            surfaceResolved = true;
            if (!picked.allowedSurfaceIds().contains(surfaceId)) {
                return null;
            }
        }
        return picked;
    }
}
