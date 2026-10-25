package com.hypixel.hytale.builtin.hytalegenerator.worldstructure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Root world structure manager.
 * Replaced BiomeMap and SimpleBiomeMap in Hytale WorldGen V2.
 */
public final class WorldStructure {

    private final BiCarta<TileBiome> biCarta;
    private final List<CustomBiome> customBiomes;

    public WorldStructure(List<TileBiome> tileBiomes, List<CustomBiome> customBiomes, int cellSize, long seed) {
        this.biCarta = new BiCarta<>(tileBiomes, cellSize, seed);
        this.customBiomes = new ArrayList<>(customBiomes);
        // Sort custom biomes by descending priority
        this.customBiomes.sort(Comparator.comparingInt(CustomBiome::priority).reversed());
    }

    /**
     * Resolves the effective biome ID for world coordinate (x, z).
     */
    public int getBiomeId(float x, float z) {
        BiCarta.CellQueryResult<TileBiome> cell = biCarta.query(x, z);
        int primaryId = cell.primary().id();

        for (CustomBiome custom : customBiomes) {
            if (custom.matches(primaryId, x, z)) {
                return custom.id();
            }
        }
        return primaryId;
    }

    public BiCarta<TileBiome> getBiCarta() {
        return biCarta;
    }
}
