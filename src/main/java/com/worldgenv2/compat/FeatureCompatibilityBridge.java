package com.worldgenv2.compat;

import com.worldgenv2.api.VoxelSpace;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges Minecraft's PlacedFeatures (modded trees, flowers, ore veins)
 * so they can execute safely inside Hytale's StagedChunkGenerator pipeline.
 */
public final class FeatureCompatibilityBridge<T> {

    @FunctionalInterface
    public interface ExternalFeatureGenerator<T> {
        /**
         * Executes an external mod feature (ore pass, tree decorator, etc.) on the target chunk.
         */
        void place(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, long seed);
    }

    private final List<ExternalFeatureGenerator<T>> undergroundOrePasses = new ArrayList<>();
    private final List<ExternalFeatureGenerator<T>> surfaceFoliagePasses = new ArrayList<>();

    public void registerOrePass(ExternalFeatureGenerator<T> pass) {
        undergroundOrePasses.add(pass);
    }

    public void registerFoliagePass(ExternalFeatureGenerator<T> pass) {
        surfaceFoliagePasses.add(pass);
    }

    public void executeOrePasses(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, long seed) {
        for (ExternalFeatureGenerator<T> pass : undergroundOrePasses) {
            pass.place(chunkX, chunkZ, voxelSpace, seed);
        }
    }

    public void executeFoliagePasses(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, long seed) {
        for (ExternalFeatureGenerator<T> pass : surfaceFoliagePasses) {
            pass.place(chunkX, chunkZ, voxelSpace, seed);
        }
    }
}
