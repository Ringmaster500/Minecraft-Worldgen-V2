package com.hypixel.hytale.builtin.hytalegenerator.density;

import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.BiCarta;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.TileBiome;

/**
 * Distance to Biome Edge density node.
 * Evaluates the Euclidean distance to the nearest adjacent biome border.
 * Returns -1 if the distance exceeds maxBiomeEdgeDistance.
 */
public final class DistanceToBiomeEdgeDensity implements DensityFunction {

    private final BiCarta<TileBiome> biCarta;
    private final float maxBiomeEdgeDistance;

    public DistanceToBiomeEdgeDensity(BiCarta<TileBiome> biCarta, float maxBiomeEdgeDistance) {
        this.biCarta = biCarta;
        this.maxBiomeEdgeDistance = maxBiomeEdgeDistance;
    }

    @Override
    public float compute(float x, float y, float z) {
        BiCarta.CellQueryResult<TileBiome> result = biCarta.query(x, z);
        float dist = result.distanceToEdge();

        if (dist > maxBiomeEdgeDistance) {
            return -1.0f;
        }
        // Normalized between 0.0 and 1.0
        return dist / maxBiomeEdgeDistance;
    }

    public float getMaxBiomeEdgeDistance() {
        return maxBiomeEdgeDistance;
    }
}
