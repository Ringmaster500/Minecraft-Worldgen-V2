package com.hypixel.hytale.builtin.hytalegenerator.engine.chunkgenerator;

import com.hypixel.hytale.builtin.hytalegenerator.api.VoxelSpace;
import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;
import com.hypixel.hytale.builtin.hytalegenerator.material.MaterialProvider;
import com.hypixel.hytale.builtin.hytalegenerator.props.Prop;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.BiCarta;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.TileBiome;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.WorldStructure;

import java.util.List;

/**
 * Pipelined chunk generator execution engine.
 * Sequentially executes:
 * 1. BiomeStage
 * 2. BiomeDistanceStage
 * 3. TerrainStage
 * 4. PropStage
 */
public final class StagedChunkGenerator<T> {

    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Z = 16;

    private final WorldStructure worldStructure;
    private final MaterialProvider<T> defaultMaterialProvider;
    private final List<Prop<T>> surfaceProps;
    private final T airBlock;
    private final T baseSolidBlock;

    public StagedChunkGenerator(
        WorldStructure worldStructure,
        MaterialProvider<T> defaultMaterialProvider,
        List<Prop<T>> surfaceProps,
        T airBlock,
        T baseSolidBlock
    ) {
        this.worldStructure = worldStructure;
        this.defaultMaterialProvider = defaultMaterialProvider;
        this.surfaceProps = List.copyOf(surfaceProps);
        this.airBlock = airBlock;
        this.baseSolidBlock = baseSolidBlock;
    }

    /**
     * Executes the complete staged generation pipeline on a chunk volume.
     */
    public void generateChunk(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace) {
        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();

        // Stage 1 & 2: Biome & Distance Resolution
        BiCarta<TileBiome> biCarta = worldStructure.getBiCarta();

        // Stage 3: TerrainStage (Volumetric Density Sculpting)
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int worldX = originX + lx;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldZ = originZ + lz;

                BiCarta.CellQueryResult<TileBiome> cell = biCarta.query(worldX, worldZ);
                DensityFunction primaryDensity = cell.primary().terrainDensity();
                DensityFunction secondaryDensity = cell.secondary().terrainDensity();
                float blend = cell.blendFactor();

                int depth = 0;
                boolean foundSurface = false;

                for (int worldY = maxY - 1; worldY >= minY; worldY--) {
                    float d1 = primaryDensity.compute(worldX, worldY, worldZ);
                    float compositeDensity = d1;
                    if (blend > 0.001f) {
                        float d2 = secondaryDensity.compute(worldX, worldY, worldZ);
                        compositeDensity = d1 * (1.0f - blend) + d2 * blend;
                    }

                    if (compositeDensity > 0.0f) {
                        // Voxel is solid!
                        float slopeAngle = 0.0f; // Can be enhanced with central difference normal
                        int space = (foundSurface ? 0 : 10);
                        T material = defaultMaterialProvider.getMaterial(
                            worldX, worldY, worldZ, depth, space, slopeAngle, voxelSpace
                        );
                        voxelSpace.setBlock(worldX, worldY, worldZ, material != null ? material : baseSolidBlock);
                        depth++;
                        foundSurface = true;
                    } else {
                        voxelSpace.setBlock(worldX, worldY, worldZ, airBlock);
                        depth = 0;
                    }
                }
            }
        }

        // Stage 4: PropStage (Surface Flora & Scatter)
        long chunkSeed = ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int worldX = originX + lx;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldZ = originZ + lz;

                for (int worldY = maxY - 2; worldY >= minY + 1; worldY--) {
                    if (!voxelSpace.isAir(worldX, worldY, worldZ) && voxelSpace.isAir(worldX, worldY + 1, worldZ)) {
                        // Found a valid surface block
                        Prop.Context<T> propContext = new Prop.Context<>(
                            worldX, worldY + 1, worldZ,
                            0.0f, 1.0f, 0.0f,
                            voxelSpace,
                            chunkSeed
                        );

                        for (Prop<T> prop : surfaceProps) {
                            if (prop.generate(propContext)) {
                                break;
                            }
                        }
                        break; // Move to next column
                    }
                }
            }
        }
    }
}
