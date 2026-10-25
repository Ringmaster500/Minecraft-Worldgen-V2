package com.hypixel.hytale.builtin.hytalegenerator.worldstructure;

import java.util.List;

/**
 * High-performance 2D cellular partitioning map (BiCarta).
 * Evaluates Voronoi point generators, cell IDs, and edge distance metrics.
 */
public final class BiCarta<T> {

    public record CellQueryResult<T>(
        T primary,
        T secondary,
        float distanceToEdge,
        float blendFactor
    ) {}

    private final List<TileBiome> tileBiomes;
    private final float totalWeight;
    private final int cellSize;
    private final long seed;

    public BiCarta(List<TileBiome> tileBiomes, int cellSize, long seed) {
        this.tileBiomes = List.copyOf(tileBiomes);
        this.cellSize = Math.max(16, cellSize);
        this.seed = seed;

        float weightSum = 0.0f;
        for (TileBiome biome : this.tileBiomes) {
            weightSum += biome.weight();
        }
        this.totalWeight = weightSum;
    }

    /**
     * Resolves the primary TileBiome and boundary metrics at continuous coordinates.
     */
    public CellQueryResult<TileBiome> query(float x, float z) {
        if (tileBiomes.isEmpty()) {
            throw new IllegalStateException("BiCarta contains no registered TileBiomes.");
        }
        if (tileBiomes.size() == 1) {
            return new CellQueryResult<>(tileBiomes.getFirst(), tileBiomes.getFirst(), Float.MAX_VALUE, 0.0f);
        }

        // Cellular Voronoi evaluation across 3x3 neighboring cell points
        int cellX = (int) Math.floor(x / cellSize);
        int cellZ = (int) Math.floor(z / cellSize);

        TileBiome closestBiome = null;
        float minDistSq = Float.MAX_VALUE;

        TileBiome secondBiome = null;
        float secondDistSq = Float.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;

                long cellHash = hashCoordinates(cx, cz, seed);
                float jitterX = (float) ((cellHash & 0xFFFF) / 65535.0) * 0.8f + 0.1f;
                float jitterZ = (float) (((cellHash >> 16) & 0xFFFF) / 65535.0) * 0.8f + 0.1f;

                float pointX = (cx + jitterX) * cellSize;
                float pointZ = (cz + jitterZ) * cellSize;

                float distSq = (x - pointX) * (x - pointX) + (z - pointZ) * (z - pointZ);

                TileBiome biomeAtPoint = selectWeightedBiome(cellHash);

                if (distSq < minDistSq) {
                    secondDistSq = minDistSq;
                    secondBiome = closestBiome;

                    minDistSq = distSq;
                    closestBiome = biomeAtPoint;
                } else if (distSq < secondDistSq) {
                    secondDistSq = distSq;
                    secondBiome = biomeAtPoint;
                }
            }
        }

        if (secondBiome == null) secondBiome = closestBiome;

        float d1 = (float) Math.sqrt(minDistSq);
        float d2 = (float) Math.sqrt(secondDistSq);
        float distToEdge = (d2 - d1) * 0.5f;

        float transitionDist = closestBiome.defaultTransitionDistance();
        float blendFactor = Math.clamp(1.0f - (distToEdge / transitionDist), 0.0f, 1.0f);
        // Smoothstep curve
        blendFactor = blendFactor * blendFactor * (3.0f - 2.0f * blendFactor);

        return new CellQueryResult<>(closestBiome, secondBiome, distToEdge, blendFactor);
    }

    private TileBiome selectWeightedBiome(long hash) {
        float normalized = (float) (Math.abs(hash % 10000) / 10000.0) * totalWeight;
        float accumulated = 0.0f;
        for (TileBiome biome : tileBiomes) {
            accumulated += biome.weight();
            if (normalized <= accumulated) {
                return biome;
            }
        }
        return tileBiomes.getLast();
    }

    private static long hashCoordinates(int x, int z, long seed) {
        long h = seed + x * 374761393L + z * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return h ^ (h >> 16);
    }
}
