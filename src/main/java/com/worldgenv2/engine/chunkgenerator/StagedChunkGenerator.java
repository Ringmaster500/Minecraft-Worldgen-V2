package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.ContextPrecision;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.props.runtime.PropRuntimePipeline;
import com.worldgenv2.worldstructure.MacroFeatureGraph;
import com.worldgenv2.worldstructure.UndergroundBiome;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipelined chunk generator execution engine.
 * Sequentially executes:
 * 1. BiomeStage & BiomeDistanceStage (via WorldStructure across an 18x18 halo)
 * 2. Volumetric Column Batching (18 x H x 18 density volume pre-computation)
 * 3. TerrainStage (Volumetric sculpting, overhang air-space tracking, & zero-cost 3D normals)
 * 4. PropStage (Multi-surface normal-aligned prop placement across tops, arches, and caves)
 */
public final class StagedChunkGenerator<T> {

    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Z = 16;
    private static final int HALO_SIZE = 18; // [-1 .. 16] in X and Z for central-difference 3D normals
    private static final int INITIAL_SKY_CLEARANCE = 16;
    private static final int MAX_SLOPE_EVAL_DEPTH = 5;
    private static final int UNDERGROUND_MIN_MASS = 5;
    private static final int UNDERGROUND_DEPTH_OFFSET = 64;

    /**
     * Thread-local reusable buffers to eliminate heap allocations during concurrent chunk generation.
     */
    private static final class ColumnScratch {
        float[] densityVolume = new float[0];
        float[] colBuffer = new float[0];
        float[] blendScratch = new float[0];
        final WorldStructure.BiomeQueryResult[] chunkBiomes =
            new WorldStructure.BiomeQueryResult[CHUNK_SIZE_X * CHUNK_SIZE_Z];

        void ensureCapacity(int height) {
            int requiredVolume = HALO_SIZE * HALO_SIZE * height;
            if (densityVolume.length < requiredVolume) {
                densityVolume = new float[requiredVolume];
            }
            if (colBuffer.length < height) {
                colBuffer = new float[height];
                blendScratch = new float[height];
            }
        }
    }

    private final ThreadLocal<ColumnScratch> threadScratch = ThreadLocal.withInitial(ColumnScratch::new);

    private final WorldStructure worldStructure;
    private final MaterialProvider<T> defaultMaterialProvider;
    private final List<Prop<T>> surfaceProps;
    private final T airBlock;
    private final T baseSolidBlock;
    private final T roadDeckBlock;
    private final T roadShoulderBlock;
    private final StageContextCache<T> contextCache;
    private final PropRuntimePipeline<T> propRuntimePipeline;

    public StagedChunkGenerator(
        WorldStructure worldStructure,
        MaterialProvider<T> defaultMaterialProvider,
        List<Prop<T>> surfaceProps,
        T airBlock,
        T baseSolidBlock
    ) {
        this(worldStructure, defaultMaterialProvider, surfaceProps, airBlock, baseSolidBlock, null, null);
    }

    public StagedChunkGenerator(
        WorldStructure worldStructure,
        MaterialProvider<T> defaultMaterialProvider,
        List<Prop<T>> surfaceProps,
        T airBlock,
        T baseSolidBlock,
        T roadDeckBlock,
        T roadShoulderBlock
    ) {
        this(worldStructure, defaultMaterialProvider, surfaceProps, null, airBlock, baseSolidBlock, roadDeckBlock, roadShoulderBlock);
    }

    public StagedChunkGenerator(
        WorldStructure worldStructure,
        MaterialProvider<T> defaultMaterialProvider,
        List<Prop<T>> surfaceProps,
        PropRuntimePipeline<T> propRuntimePipeline,
        T airBlock,
        T baseSolidBlock,
        T roadDeckBlock,
        T roadShoulderBlock
    ) {
        this.worldStructure = worldStructure;
        this.defaultMaterialProvider = defaultMaterialProvider;
        this.surfaceProps = surfaceProps != null ? List.copyOf(surfaceProps) : List.of();
        this.airBlock = airBlock;
        this.baseSolidBlock = baseSolidBlock;
        this.roadDeckBlock = roadDeckBlock;
        this.roadShoulderBlock = roadShoulderBlock;

        this.contextCache = propRuntimePipeline != null && propRuntimePipeline.getContextCache() != null
            ? propRuntimePipeline.getContextCache()
            : new StageContextCache<>();

        if (propRuntimePipeline != null) {
            this.propRuntimePipeline = propRuntimePipeline;
        } else {
            List<PropRuntime<T>> defaultRuntimes = new ArrayList<>();
            for (Prop<T> p : this.surfaceProps) {
                defaultRuntimes.add(PropRuntime.of(1, p));
            }
            this.propRuntimePipeline = new PropRuntimePipeline<>(defaultRuntimes, this.contextCache, airBlock);
        }
    }

    public StageContextCache<T> getContextCache() {
        return contextCache;
    }

    public PropRuntimePipeline<T> getPropRuntimePipeline() {
        return propRuntimePipeline;
    }

    public ChunkDependencyEnvelope computeChunkDependencyEnvelope(int chunkX, int chunkZ, int stageIndex, long worldSeed) {
        return propRuntimePipeline.computeStageEnvelope(chunkX, chunkZ, stageIndex, worldSeed);
    }

    /**
     * Executes the complete staged generation pipeline on a chunk volume.
     */
    public void generateChunk(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace) {
        generateChunk(chunkX, chunkZ, voxelSpace, null);
    }

    /**
     * Executes the complete staged generation pipeline on a chunk volume, with an optional
     * structure terrain-adaptation density field (e.g., Minecraft's Beardifier) applied before TerrainStage.
     */
    public void generateChunk(
        int chunkX,
        int chunkZ,
        VoxelSpace<T> voxelSpace,
        DensityFunction structureAdaptationDensity
    ) {
        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();
        int height = maxY - minY;
        if (height <= 0) {
            return;
        }

        ColumnScratch scratch = threadScratch.get();
        scratch.ensureCapacity(height);
        float[] densityVolume = scratch.densityVolume;
        float[] colBuffer = scratch.colBuffer;
        float[] blendScratch = scratch.blendScratch;
        WorldStructure.BiomeQueryResult[] chunkBiomes = scratch.chunkBiomes;

        // =====================================================================
        // Stage 1 & 2: Biome Resolution & 18x18 Halo Column Density Evaluation
        // =====================================================================
        for (int hx = 0; hx < HALO_SIZE; hx++) {
            int lx = hx - 1;
            int worldX = originX + lx;
            for (int hz = 0; hz < HALO_SIZE; hz++) {
                int lz = hz - 1;
                int worldZ = originZ + lz;

                WorldStructure.BiomeQueryResult biomeQuery = worldStructure.queryBiome(worldX, worldZ);
                if (lx >= 0 && lx < CHUNK_SIZE_X && lz >= 0 && lz < CHUNK_SIZE_Z) {
                    chunkBiomes[(lx << 4) | lz] = biomeQuery;
                }

                biomeQuery.computeBlendedColumn(worldX, worldZ, minY, maxY, colBuffer, blendScratch);
                worldStructure.applyTerrainModifiers(worldX, worldZ, minY, maxY, colBuffer);
                if (worldStructure.getSplineRoadEngine() != null) {
                    worldStructure.getSplineRoadEngine().applyRoadToColumn(worldX, worldZ, minY, maxY, colBuffer);
                }
                if (structureAdaptationDensity != null) {
                    structureAdaptationDensity.computeColumn(worldX, worldZ, minY, maxY, blendScratch);
                    for (int i = 0; i < height; i++) {
                        float adapt = blendScratch[i];
                        if (adapt < 0.0f) {
                            colBuffer[i] += adapt;
                            // Aggressively hollow out stone inside structure rooms & corridors (Ancient Cities, Strongholds)
                            if (adapt < -2.0f && colBuffer[i] > -0.5f) {
                                colBuffer[i] = -0.5f;
                            }
                        } else if (adapt > 0.0f) {
                            colBuffer[i] += adapt;
                        }
                    }
                }
                int destOffset = (hx * HALO_SIZE + hz) * height;
                System.arraycopy(colBuffer, 0, densityVolume, destOffset, height);
            }
        }

        // =====================================================================
        // Stage 3: TerrainStage (Volumetric Density Sculpting & Material Rules)
        // =====================================================================
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int worldX = originX + lx;
            int hx = lx + 1;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldZ = originZ + lz;
                int hz = lz + 1;

                WorldStructure.BiomeQueryResult biomeQuery = chunkBiomes[(lx << 4) | lz];
                MaterialProvider<T> materialProvider =
                    biomeQuery.resolveMaterialProvider(worldX, worldZ, defaultMaterialProvider);

                int colOffset = (hx * HALO_SIZE + hz) * height;
                int xPlusOffset = ((hx + 1) * HALO_SIZE + hz) * height;
                int xMinusOffset = ((hx - 1) * HALO_SIZE + hz) * height;
                int zPlusOffset = (hx * HALO_SIZE + (hz + 1)) * height;
                int zMinusOffset = (hx * HALO_SIZE + (hz - 1)) * height;

                int depth = 0;
                int airSpaceAbove = INITIAL_SKY_CLEARANCE;
                int currentSurfaceSpace = INITIAL_SKY_CLEARANCE;
                // Becomes true once a thick solid mass has been passed: any surface below that point is a
                // cave floor, which must receive plain rock instead of sky-exposed grass/sand cover
                boolean underground = false;

                for (int worldY = maxY - 1; worldY >= minY; worldY--) {
                    int yIdx = worldY - minY;
                    float compositeDensity = densityVolume[colOffset + yIdx];

                    if (compositeDensity > 0.0f) {
                        // Voxel is solid: latch accumulated air clearance when entering a new surface stratum
                        if (depth == 0) {
                            currentSurfaceSpace = airSpaceAbove;
                        }

                        float slopeAngle = 0.0f;
                        if (depth < MAX_SLOPE_EVAL_DEPTH) {
                            float dx = (densityVolume[xPlusOffset + yIdx] - densityVolume[xMinusOffset + yIdx]) * 0.5f;
                            int yUp = Math.min(height - 1, yIdx + 1);
                            int yDown = Math.max(0, yIdx - 1);
                            float dy = (densityVolume[colOffset + yUp] - densityVolume[colOffset + yDown]) * 0.5f;
                            float dz = (densityVolume[zPlusOffset + yIdx] - densityVolume[zMinusOffset + yIdx]) * 0.5f;

                            float mag = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            float ny = mag > 1e-5f ? (-dy / mag) : 1.0f;
                            slopeAngle = (float) Math.toDegrees(Math.acos(Math.clamp(ny, -1.0f, 1.0f)));
                        }

                        int materialDepth = underground ? depth + UNDERGROUND_DEPTH_OFFSET : depth;
                        MaterialProvider<T> activeProvider = materialProvider;
                        if (worldStructure.isRiverChannel(worldX, worldZ)
                            && worldStructure.getRiverMaterialProvider() != null
                            && worldY <= 64) {
                            activeProvider = worldStructure.getRiverMaterialProvider();
                        }
                        T material = activeProvider != null
                            ? activeProvider.getMaterial(
                                worldX, worldY, worldZ, materialDepth, currentSurfaceSpace, slopeAngle, voxelSpace
                            )
                            : baseSolidBlock;
                        if (worldStructure.getSplineRoadEngine() != null && (roadDeckBlock != null || roadShoulderBlock != null)) {
                            material = worldStructure.getSplineRoadEngine().resolveRoadMaterial(
                                worldX, worldY, worldZ, depth, material, roadDeckBlock, roadShoulderBlock
                            );
                        }
                        voxelSpace.setBlock(worldX, worldY, worldZ, material != null ? material : baseSolidBlock);

                        depth++;
                        airSpaceAbove = 0;
                    } else {
                        voxelSpace.setBlock(worldX, worldY, worldZ, airBlock);
                        if (depth >= UNDERGROUND_MIN_MASS) {
                            underground = true;
                        }
                        depth = 0;
                        airSpaceAbove++;
                    }
                }
            }
        }

        // =====================================================================
        // Stage 3b: UndergroundBiomeStage (3D biome volumes paint cave floors & ceilings)
        // =====================================================================
        if (worldStructure.hasUndergroundBiomes()) {
            applyUndergroundBiomes(originX, originZ, minY, maxY, voxelSpace);
        }

        // Capture immutable baseline terrain snapshot at stage 0 into contextCache
        ImmutableChunkSnapshot<T> stage0Snapshot = ImmutableChunkSnapshot.capture(chunkX, chunkZ, voxelSpace, airBlock);
        contextCache.putSnapshot(chunkX, chunkZ, 0, stage0Snapshot);

        // =====================================================================
        // Stage 4: PropStage (Multi-Surface Flora, Overhangs & Cavern Scatter)
        // =====================================================================
        MultiStageVoxelSpace<T> stage1Space = new MultiStageVoxelSpace<>(
            chunkX, chunkZ, 1, voxelSpace, contextCache, airBlock
        );

        long chunkSeed = ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int worldX = originX + lx;
            int hx = lx + 1;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldZ = originZ + lz;
                int hz = lz + 1;

                WorldStructure.BiomeQueryResult biomeQuery = chunkBiomes[(lx << 4) | lz];
                List<Prop<T>> activeProps = biomeQuery.resolveProps(surfaceProps);
                if (activeProps.isEmpty()) {
                    continue;
                }

                int colOffset = (hx * HALO_SIZE + hz) * height;
                int xPlusOffset = ((hx + 1) * HALO_SIZE + hz) * height;
                int xMinusOffset = ((hx - 1) * HALO_SIZE + hz) * height;
                int zPlusOffset = (hx * HALO_SIZE + (hz + 1)) * height;
                int zMinusOffset = (hx * HALO_SIZE + (hz - 1)) * height;

                for (int worldY = maxY - 2; worldY >= minY + 1; worldY--) {
                    if (!voxelSpace.isAir(worldX, worldY, worldZ) && voxelSpace.isAir(worldX, worldY + 1, worldZ)) {
                        // Compute 3D surface normal vector N = -grad(D) from the pre-computed halo volume
                        int yIdx = worldY - minY;
                        float dx = (densityVolume[xPlusOffset + yIdx] - densityVolume[xMinusOffset + yIdx]) * 0.5f;
                        float dy = (densityVolume[colOffset + yIdx + 1] - densityVolume[colOffset + yIdx - 1]) * 0.5f;
                        float dz = (densityVolume[zPlusOffset + yIdx] - densityVolume[zMinusOffset + yIdx]) * 0.5f;

                        float mag = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        float nx = 0.0f;
                        float ny = 1.0f;
                        float nz = 0.0f;
                        if (mag > 1e-5f) {
                            float invMag = 1.0f / mag;
                            nx = -dx * invMag;
                            ny = -dy * invMag;
                            nz = -dz * invMag;
                        }

                        long surfaceSeed = chunkSeed ^ ((long) worldX * 3129871L) ^ ((long) worldZ * 116129781L) ^ worldY;
                        Prop.Context<T> propContext = new Prop.Context<>(
                            worldX, worldY + 1, worldZ,
                            nx, ny, nz,
                            stage1Space,
                            surfaceSeed
                        );

                        for (Prop<T> prop : activeProps) {
                            if (prop.generate(propContext)) {
                                break; // Only one prop claims this specific (x, y+1, z) anchor
                            }
                        }
                        // Continue scanning downward so arches, overhangs, and cave floors also receive props
                    }
                }
            }
        }

        // Capture immutable snapshot at stage 1
        ImmutableChunkSnapshot<T> stage1Snapshot = ImmutableChunkSnapshot.capture(chunkX, chunkZ, voxelSpace, airBlock);
        contextCache.putSnapshot(chunkX, chunkZ, 1, stage1Snapshot);

        // Execute any higher-order stages in propRuntimePipeline (stages >= 2)
        for (int stageIdx : propRuntimePipeline.getStageIndices()) {
            if (stageIdx >= 2) {
                propRuntimePipeline.executeStage(stageIdx, chunkX, chunkZ, voxelSpace, chunkSeed, null);
            }
        }

        // =====================================================================
        // Stage 5: Macro Landmark Stamping (Watchtowers & Road Termini)
        // =====================================================================
        if (worldStructure.getMacroFeatureGraph() != null && roadDeckBlock != null) {
            T plinthBlock = roadShoulderBlock != null ? roadShoulderBlock : baseSolidBlock;
            for (MacroFeatureGraph.PlacedLandmark landmark : worldStructure.getMacroFeatureGraph().getPlacedLandmarks().values()) {
                if ((landmark.x() >> 4) == chunkX && (landmark.z() >> 4) == chunkZ) {
                    stampMacroLandmark(landmark, voxelSpace, roadDeckBlock, plinthBlock);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyUndergroundBiomes(int originX, int originZ, int minY, int maxY, VoxelSpace<T> voxelSpace) {
        int decorMaxY = worldStructure.undergroundBiomeMaxY();
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int worldX = originX + lx;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldZ = originZ + lz;

                int topSolid = minY;
                for (int y = maxY - 1; y > minY; y--) {
                    if (!voxelSpace.isAir(worldX, y, worldZ)) {
                        topSolid = y;
                        break;
                    }
                }
                int yLimit = Math.min(topSolid - 2, decorMaxY);
                for (int y = minY + 2; y <= yLimit; y++) {
                    if (voxelSpace.isAir(worldX, y, worldZ)) {
                        continue;
                    }
                    boolean isFloor = voxelSpace.isAir(worldX, y + 1, worldZ);
                    boolean isCeiling = voxelSpace.isAir(worldX, y - 1, worldZ);
                    if (!isFloor && !isCeiling) {
                        continue;
                    }
                    UndergroundBiome biome = worldStructure.getUndergroundBiome(worldX, y, worldZ);
                    if (biome == null) {
                        continue;
                    }
                    long h = (worldX * 73856093L) ^ (y * 19349663L) ^ (worldZ * 83492791L) ^ (biome.id() * 0x9E3779B1L);
                    h *= 0x9E3779B97F4A7C15L;
                    float roll = ((h >>> 40) & 0xFFFFFF) / (float) 0x1000000;
                    if (isFloor && biome.floorBlock() != null && roll < biome.floorChance()) {
                        voxelSpace.setBlock(worldX, y, worldZ, (T) biome.floorBlock());
                    } else if (isCeiling && biome.ceilingBlock() != null && roll < biome.ceilingChance()) {
                        voxelSpace.setBlock(worldX, y, worldZ, (T) biome.ceilingBlock());
                    }
                }
            }
        }
    }

    private void stampMacroLandmark(
        MacroFeatureGraph.PlacedLandmark landmark,
        VoxelSpace<T> voxelSpace,
        T wallBlock,
        T plinthBlock
    ) {
        int cx = landmark.x();
        int cz = landmark.z();
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();

        // Find the actual surface Y at the landmark center after road grading
        int baseY = landmark.y();
        for (int y = maxY - 2; y >= minY + 1; y--) {
            if (!voxelSpace.isAir(cx, y, cz)) {
                baseY = y + 1;
                break;
            }
        }

        // 1. Adaptive foundation plinth: ray-march downward under the 5x5 footprint
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;
                int y = baseY - 1;
                int depth = 0;
                while (y > minY && voxelSpace.isAir(wx, y, wz) && depth < 24) {
                    voxelSpace.setBlock(wx, y, wz, plinthBlock);
                    y--;
                    depth++;
                }
                voxelSpace.setBlock(wx, baseY, wz, wallBlock);
            }
        }

        // 2. Watchtower / Waystation walls, doorways aligned with road axes, & crenellated battlements
        for (int dy = 1; dy <= 6; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int wx = cx + dx;
                    int wy = baseY + dy;
                    int wz = cz + dz;
                    if (wy >= maxY) {
                        continue;
                    }
                    boolean isPerimeter = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    boolean isDoorway = (dy <= 2) && ((dx == 0 && Math.abs(dz) == 2) || (dz == 0 && Math.abs(dx) == 2));
                    if (isDoorway) {
                        voxelSpace.setBlock(wx, wy, wz, airBlock);
                    } else if (dy == 5 && !isPerimeter) {
                        voxelSpace.setBlock(wx, wy, wz, plinthBlock);
                    } else if (isPerimeter) {
                        if (dy == 6 && ((dx + dz) & 1) != 0) {
                            voxelSpace.setBlock(wx, wy, wz, airBlock);
                        } else {
                            voxelSpace.setBlock(wx, wy, wz, wallBlock);
                        }
                    } else {
                        voxelSpace.setBlock(wx, wy, wz, airBlock);
                    }
                }
            }
        }
    }
}
