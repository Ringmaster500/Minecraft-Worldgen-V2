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
    private java.util.function.Function<String, T> prefabBlockResolver;
    private TerrainFinisher<T> terrainFinisher;
    private java.util.function.Predicate<T> fluidTest = b -> false;
    private final PropContextPass<T> propPass;
    /** True when any biome has props that work per surface column (not distributions); those need the density halo of the real chunk. */
    private final boolean columnarPropsPresent;
    private final ThreadLocal<ColumnScratch> tileScratch = ThreadLocal.withInitial(ColumnScratch::new);

    /** Finishes a chunk's terrain (bedrock, water) as part of the terrain stage, so every consumer of terrain sees the same world. */
    @FunctionalInterface
    public interface TerrainFinisher<T> {
        void finish(int chunkX, int chunkZ, VoxelSpace<T> space);
    }

    /** Tells the generator which blocks are fluids, so props treat water as something to build under, not on. */
    public StagedChunkGenerator<T> withFluidTest(java.util.function.Predicate<T> fluid) {
        this.fluidTest = fluid == null ? b -> false : fluid;
        return this;
    }

    public StagedChunkGenerator<T> withTerrainFinisher(TerrainFinisher<T> finisher) {
        this.terrainFinisher = finisher;
        return this;
    }

    /** Enables stamping of {@link com.worldgenv2.worldstructure.StructurePlacement}s (spawn structure, landmarks, POIs). */
    public StagedChunkGenerator<T> withPrefabResolver(java.util.function.Function<String, T> resolver) {
        this.prefabBlockResolver = resolver;
        return this;
    }

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

        this.propPass = new PropContextPass<>(worldStructure, this.surfaceProps, airBlock);
        boolean columnar = false;
        for (Prop<T> p : this.surfaceProps) if (!PropContextPass.isDistribution(p)) columnar = true;
        for (WorldStructure.BiomeProp<T> bp : worldStructure.<T>allBiomeProps()) if (!PropContextPass.isDistribution(bp.prop())) columnar = true;
        this.columnarPropsPresent = columnar;
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
     * The terrain stage: biomes, the density halo, volumetric sculpting, materials, underground biomes, then the terrain finisher
     * (bedrock and water). It depends only on the chunk's coordinates, so it can be run for any chunk, including a neighbour that
     * has not been generated, and always gives the same blocks.
     */
    private void terrainStage(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, DensityFunction structureAdaptationDensity, ColumnScratch scratch) {
        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();
        int height = maxY - minY;
        if (height <= 0) {
            return;
        }

        float[] densityVolume = scratch.densityVolume;
        float[] colBuffer = scratch.colBuffer;
        float[] blendScratch = scratch.blendScratch;
        WorldStructure.BiomeQueryResult[] chunkBiomes = scratch.chunkBiomes;

        // =====================================================================
        // Stage 1 & 2: Biome Resolution & 18x18 Halo Column Density Evaluation
        // =====================================================================
        // The ring of neighbouring columns (the halo) is only there to measure slopes. When no material here reads the slope, and no
        // per-column prop needs surface normals, the ring is skipped: the blocks come out the same, a fifth of the density work less.
        boolean needHalo = columnarPropsPresent
            || (worldStructure.getRiverMaterialProvider() != null && worldStructure.getRiverMaterialProvider().usesSlope());
        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int worldX = originX + lx, worldZ = originZ + lz;
                WorldStructure.BiomeQueryResult q = worldStructure.queryBiome(worldX, worldZ);
                chunkBiomes[(lx << 4) | lz] = q;
                if (!needHalo) {
                    MaterialProvider<T> mp = q.resolveMaterialProvider(worldX, worldZ, defaultMaterialProvider);
                    if (mp != null && mp.usesSlope()) needHalo = true;
                }
            }
        }

        // Only the sign of the density decides blocks when nothing reads exact values (no slope, no structure shaping, no roads or
        // terrain modifiers); then stretches of a column whose sign the density bounds already prove are not evaluated.
        boolean prune = !PRUNE_OFF && !needHalo && structureAdaptationDensity == null && worldStructure.getSplineRoadEngine() == null
            && !worldStructure.terrainModifiersPresent();
        float[] pruneTmp = prune ? new float[height] : null;
        float[] pruneBounds = new float[2];
        for (int hx = 0; hx < HALO_SIZE; hx++) {
            int lx = hx - 1;
            int worldX = originX + lx;
            for (int hz = 0; hz < HALO_SIZE; hz++) {
                int lz = hz - 1;
                int worldZ = originZ + lz;

                boolean inner = lx >= 0 && lx < CHUNK_SIZE_X && lz >= 0 && lz < CHUNK_SIZE_Z;
                if (!inner && !needHalo) continue;
                WorldStructure.BiomeQueryResult biomeQuery = inner ? chunkBiomes[(lx << 4) | lz] : worldStructure.queryBiome(worldX, worldZ);

                if (prune) prunedColumn(biomeQuery, worldX, worldZ, minY, maxY, colBuffer, blendScratch, pruneTmp, pruneBounds);
                else biomeQuery.computeBlendedColumn(worldX, worldZ, minY, maxY, colBuffer, blendScratch);
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
        int[] runOut = new int[1];
        int[] ceilingDepth = new int[height];
        int[] ceilingSpace = new int[height];
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

                int cDepth = -1;
                int airBelow = 0;
                int currentCeilingSpace = 0;
                for (int yIdx = 0; yIdx < height; yIdx++) {
                    if (densityVolume[colOffset + yIdx] > 0.0f) {
                        if (airBelow > 0) {
                            cDepth = 0;
                            currentCeilingSpace = airBelow;
                            airBelow = 0;
                        } else if (cDepth >= 0) {
                            cDepth++;
                        }
                        ceilingDepth[yIdx] = cDepth;
                        ceilingSpace[yIdx] = currentCeilingSpace;
                    } else {
                        cDepth = -1;
                        airBelow++;
                        ceilingDepth[yIdx] = -1;
                        ceilingSpace[yIdx] = 0;
                    }
                }

                int depth = 0;
                int airSpaceAbove = INITIAL_SKY_CLEARANCE;
                int currentSurfaceSpace = INITIAL_SKY_CLEARANCE;
                // Becomes true once a thick solid mass has been passed: any surface below that point is a
                // cave floor, which must receive plain rock instead of sky-exposed grass/sand cover
                boolean underground = false;

                // A provider that knows its answer holds for a stretch of the column says how long; those blocks are resolved in one go.
                // Road surfaces, spawn scorching and the river's own material depend on the exact block, so they use the slow path.
                boolean riverColumn = worldStructure.isRiverChannel(worldX, worldZ) && worldStructure.getRiverMaterialProvider() != null;
                boolean runsAllowed = !riverColumn && worldStructure.getSplineRoadEngine() == null
                    && !(prefabBlockResolver != null && worldStructure.getSpawnAnchorManager() != null && worldStructure.getSpawnAnchorManager().hasActiveRule());
                boolean hasEmptySide = materialProvider != null && materialProvider.providesEmpty();

                for (int worldY = maxY - 1; worldY >= minY; ) {
                    int yIdx = worldY - minY;
                    float compositeDensity = densityVolume[colOffset + yIdx];
                    int span = 1;

                    if (compositeDensity > 0.0f) {
                        // Voxel is solid: latch accumulated air clearance when entering a new surface stratum
                        if (depth == 0) {
                            currentSurfaceSpace = airSpaceAbove;
                        }

                        float slopeAngle = 0.0f;
                        if (needHalo && depth < MAX_SLOPE_EVAL_DEPTH) {
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
                        if (riverColumn && worldY <= 64) {
                            activeProvider = worldStructure.getRiverMaterialProvider();
                        }
                        int run = 1;
                        T material;
                        if (activeProvider == null) {
                            material = baseSolidBlock;
                        } else if (runsAllowed) {
                            material = activeProvider.getMaterialRun(worldX, worldY, worldZ, materialDepth, currentSurfaceSpace,
                                ceilingDepth[yIdx], ceilingSpace[yIdx], slopeAngle, voxelSpace, runOut);
                            run = runOut[0];
                        } else {
                            material = activeProvider.getMaterial(worldX, worldY, worldZ, materialDepth, currentSurfaceSpace,
                                ceilingDepth[yIdx], ceilingSpace[yIdx], slopeAngle, voxelSpace);
                        }
                        if (worldStructure.getSplineRoadEngine() != null && (roadDeckBlock != null || roadShoulderBlock != null)) {
                            material = worldStructure.getSplineRoadEngine().resolveRoadMaterial(
                                worldX, worldY, worldZ, depth, material, roadDeckBlock, roadShoulderBlock
                            );
                        }
                        if (!underground && depth <= 2 && prefabBlockResolver != null
                            && worldStructure.getSpawnAnchorManager() != null
                            && worldStructure.getSpawnAnchorManager().hasActiveRule()) {
                            String scorchId = worldStructure.getSpawnAnchorManager().getScorchBlockId(worldX, worldZ, depth);
                            if (scorchId != null && !scorchId.isBlank()) {
                                T scorch = prefabBlockResolver.apply(scorchId);
                                if (scorch != null) material = scorch;
                            }
                        }
                        if (run > 1) {
                            int limit = Math.min(run, yIdx + 1);
                            while (span < limit && densityVolume[colOffset + yIdx - span] > 0.0f) span++;
                        }
                        voxelSpace.setBlockRun(worldX, worldZ, worldY - span + 1, worldY + 1, material != null ? material : baseSolidBlock);

                        depth += span;
                        airSpaceAbove = 0;
                    } else {
                        T filler = hasEmptySide ? materialProvider.getEmptyMaterial(worldX, worldY, worldZ, voxelSpace) : null;   // Solidity's Empty side: fluids
                        int limit = yIdx + 1;
                        if (hasEmptySide) {
                            int run = materialProvider.emptyRunLength(worldX, worldY, worldZ, voxelSpace);
                            limit = Math.min(run, limit);
                        }
                        while (span < limit && densityVolume[colOffset + yIdx - span] <= 0.0f) span++;
                        voxelSpace.setBlockRun(worldX, worldZ, worldY - span + 1, worldY + 1, filler != null ? filler : airBlock);
                        if (depth >= UNDERGROUND_MIN_MASS) {
                            underground = true;
                        }
                        depth = 0;
                        airSpaceAbove += span;
                    }
                    worldY -= span;
                }
            }
        }
        // =====================================================================
        // Stage 3b: UndergroundBiomeStage (3D biome volumes paint cave floors & ceilings)
        // =====================================================================
        if (worldStructure.hasUndergroundBiomes()) {
            applyUndergroundBiomes(originX, originZ, minY, maxY, voxelSpace);
        }

        if (terrainFinisher != null) {
            terrainFinisher.finish(chunkX, chunkZ, voxelSpace);
        }
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
        if (!COUNTERS) { generateChunkTimed(chunkX, chunkZ, voxelSpace, structureAdaptationDensity); return; }
        long t0 = System.nanoTime();
        generateChunkTimed(chunkX, chunkZ, voxelSpace, structureAdaptationDensity);
        GEN_NANOS.addAndGet(System.nanoTime() - t0);
    }

    private void generateChunkTimed(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, DensityFunction structureAdaptationDensity) {
        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();
        int height = maxY - minY;
        if (height <= 0) {
            return;
        }

        noteCounters(true);
        if (structureAdaptationDensity == null && !columnarPropsPresent) {
            // Nothing needs the real chunk's density halo, so its terrain is the cached tile (computed once, shared with every
            // neighbour whose props read it): copy it in, then run the prop pass.
            ImmutableChunkSnapshot<T> tile = getTerrainTile(chunkX, chunkZ, minY, maxY);
            long c0 = COUNTERS ? System.nanoTime() : 0;
            voxelSpace.beginBulk();
            try { tile.copyTo(voxelSpace); } finally { voxelSpace.endBulk(); }
            if (COUNTERS) COPY_NANOS.addAndGet(System.nanoTime() - c0);
            long p0 = COUNTERS ? System.nanoTime() : 0;
            runDistributionPass(chunkX, chunkZ, voxelSpace, minY, maxY);
            if (COUNTERS) PROPS_NANOS.addAndGet(System.nanoTime() - p0);
            runChunkTail(chunkX, chunkZ, voxelSpace);
            return;
        }

        ColumnScratch scratch = threadScratch.get();
        scratch.ensureCapacity(height);
        terrainStage(chunkX, chunkZ, voxelSpace, structureAdaptationDensity, scratch);
        float[] densityVolume = scratch.densityVolume;
        WorldStructure.BiomeQueryResult[] chunkBiomes = scratch.chunkBiomes;

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
                List<Prop<T>> activeProps = columnarProps(biomeQuery.resolveProps(surfaceProps));
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

        runDistributionPass(chunkX, chunkZ, voxelSpace, minY, maxY);

        // Capture immutable snapshot at stage 1
        ImmutableChunkSnapshot<T> stage1Snapshot = ImmutableChunkSnapshot.capture(chunkX, chunkZ, voxelSpace, airBlock);
        contextCache.putSnapshot(chunkX, chunkZ, 1, stage1Snapshot);

        runChunkTail(chunkX, chunkZ, voxelSpace);
    }

    /**
     * Distribution props (positions -> scanner -> assignments) run as Prop Runtime stages over a context region:
     * every anchor that can reach this chunk is evaluated against deterministic neighbour terrain, so nothing clips at a border.
     */
    private void runDistributionPass(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace, int minY, int maxY) {
        final int ctxMinY = minY, ctxMaxY = maxY;
        contextCache.ensureCapacity(Math.max(propPass.tilesPerChunk() * 16, 20000));   // the memory budget, not the count, is the real limit
        propPass.run(chunkX, chunkZ, voxelSpace, b -> b != null && !b.equals(airBlock) && !fluidTest.test(b), new PropContextPass.TileSource<T>() {
            @Override public ImmutableChunkSnapshot<T> tile(int cx, int cz) { return getTerrainTile(cx, cz, ctxMinY, ctxMaxY); }
            @Override public Object tileOrPending(int cx, int cz) { return terrainTileOrPending(cx, cz, ctxMinY, ctxMaxY); }
            @Override public ImmutableChunkSnapshot<T> await(Object pending) { return awaitTile(pending); }
        });
    }

    /** Ore veins, project structures and macro landmarks: what happens to a chunk after its props. */
    private void runChunkTail(int chunkX, int chunkZ, VoxelSpace<T> voxelSpace) {
        long chunkSeed = ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        // =====================================================================
        // Stage 4a: Ore veins (declarative OreRules; vanilla feature generation is never used)
        // =====================================================================
        if (prefabBlockResolver != null && !worldStructure.getOreRules().isEmpty()) {
            generateOres(chunkX, chunkZ, voxelSpace);
        }

        // =====================================================================
        // Stage 4b: Project structures (spawn structure, landmark and POI prefabs)
        // =====================================================================
        if (prefabBlockResolver != null) {
            for (com.worldgenv2.worldstructure.StructurePlacement placement : worldStructure.placementsForChunk(chunkX, chunkZ)) {
                if (!placement.touchesChunk(chunkX, chunkZ)) continue;
                long seed = chunkSeed ^ placement.id().hashCode();
                new com.worldgenv2.prefab.PrefabProp<T>(placement.prefab(), prefabBlockResolver, false, true, List.of(), 0, null, placement.lootTableOverride())
                    .generate(new Prop.Context<>(placement.x(), placement.y(), placement.z(), 0.0f, 1.0f, 0.0f, voxelSpace, seed));
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

    private final java.util.concurrent.ConcurrentHashMap<List<Prop<T>>, List<Prop<T>>> columnarCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** The part of a biome's prop list that works per surface column (everything except distribution props). */
    private List<Prop<T>> columnarProps(List<Prop<T>> all) {
        if (all.isEmpty()) return all;
        return columnarCache.computeIfAbsent(all, list -> {
            List<Prop<T>> out = new ArrayList<>();
            for (Prop<T> p : list) if (!PropContextPass.isDistribution(p)) out.add(p);
            return out;
        });
    }

    /**
     * Terrain for any chunk, computed on demand and cached. The chunk being generated stores its own terrain here right after its
     * terrain stage; a neighbour that has not been generated is computed the same way, so context never depends on order.
     */
    /** Dev counters: how many terrain tiles were computed, and how many chunks were generated. Written to a temp file now and then. */
    private static final java.util.concurrent.atomic.AtomicLong TILES_COMPUTED = new java.util.concurrent.atomic.AtomicLong(), CHUNKS_GENERATED = new java.util.concurrent.atomic.AtomicLong(), TILE_HITS = new java.util.concurrent.atomic.AtomicLong(),
        GEN_NANOS = new java.util.concurrent.atomic.AtomicLong(), TILE_NANOS = new java.util.concurrent.atomic.AtomicLong(),
        WAIT_NANOS = new java.util.concurrent.atomic.AtomicLong(), PROPS_NANOS = new java.util.concurrent.atomic.AtomicLong(), COPY_NANOS = new java.util.concurrent.atomic.AtomicLong(),
        PRUNED = new java.util.concurrent.atomic.AtomicLong(), EXACT = new java.util.concurrent.atomic.AtomicLong(), BOUND_MISSES = new java.util.concurrent.atomic.AtomicLong();

    private static final boolean COUNTERS = System.getenv("WG2_COUNTERS") != null;

    private static void noteCounters(boolean chunk) {
        if (!COUNTERS) return;
        long c = chunk ? CHUNKS_GENERATED.incrementAndGet() : CHUNKS_GENERATED.get();
        if (chunk && c % 64 == 0) {
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "wg2_counts.txt"), "chunks " + c + " tilesComputed " + TILES_COMPUTED.get() + " tileCacheHits " + TILE_HITS.get()
                + String.format(" | %.1f ms per chunk (all work), %.1f ms per tile (terrain)", GEN_NANOS.get() / 1e6 / Math.max(1, c - 1), TILE_NANOS.get() / 1e6 / Math.max(1, TILES_COMPUTED.get()))
                + String.format(" | per chunk: props %.1f ms (of which waiting for tiles %.1f, computing tiles %.1f), copying terrain in %.1f ms", PROPS_NANOS.get() / 1e6 / Math.max(1, c - 1), WAIT_NANOS.get() / 1e6 / Math.max(1, c - 1), TILE_NANOS.get() / 1e6 / Math.max(1, c - 1), COPY_NANOS.get() / 1e6 / Math.max(1, c - 1))
                + String.format(" | pruned %d blocks, exact %d, exact runs %d", PRUNED.get(), EXACT.get(), BOUND_MISSES.get())
                + " | shared columns hit " + com.worldgenv2.density.SharedColumnDensity.HITS.get() + " missed " + com.worldgenv2.density.SharedColumnDensity.MISSES.get()); } catch (Exception ignored) { }
        }
    }

    /**
     * Bounds pruning is exact but currently slower on Hytale's assets (their densities are mostly per-column 2D noise, which the bounds
     * walk evaluates a second time), so it is off unless WG2_PRUNE=1. It may pay off for densities built on 3D noise.
     */
    private static final boolean PRUNE_OFF = System.getenv("WG2_PRUNE") == null;

    /** How far from zero a bound must be before its sign is trusted (well above any rounding difference between paths). */
    private static final float PRUNE_MARGIN = 0.01f;

    private static final int SLAB = 16;

    /**
     * The blended density of a column, exactly where its sign could go either way, and only a placeholder of the right sign (+1 or -1)
     * where the bounds prove it: solid stays solid, air stays air, and the blocks come out the same. One walk of the density tree gives
     * bounds for every 16-block slab; neighbouring slabs that need exact values are evaluated together.
     */
    private void prunedColumn(WorldStructure.BiomeQueryResult q, float x, float z, int minY, int maxY, float[] out, float[] scratch,
                              float[] tmp, float[] bnd) {
        int height = maxY - minY;
        int nb = (height + SLAB - 1) / SLAB;
        float[] lo = com.worldgenv2.density.ColumnPool.take(nb), hi = com.worldgenv2.density.ColumnPool.take(nb);
        float[] tl = com.worldgenv2.density.ColumnPool.take(nb), th = com.worldgenv2.density.ColumnPool.take(nb);
        q.boundsBlendedSlabs(x, z, minY, maxY, SLAB, lo, hi, tl, th);
        int k = 0;
        while (k < nb) {
            int a = k * SLAB, b = Math.min(height, a + SLAB);
            if (lo[k] > PRUNE_MARGIN) { java.util.Arrays.fill(out, a, b, 1.0f); if (COUNTERS) PRUNED.addAndGet(b - a); k++; continue; }
            if (hi[k] < -PRUNE_MARGIN) { java.util.Arrays.fill(out, a, b, -1.0f); if (COUNTERS) PRUNED.addAndGet(b - a); k++; continue; }
            int start = k;
            while (k < nb && !(lo[k] > PRUNE_MARGIN) && !(hi[k] < -PRUNE_MARGIN)) k++;
            int ra = start * SLAB, rb = Math.min(height, k * SLAB);
            q.computeBlendedColumn(x, z, minY + ra, minY + rb, tmp, scratch);
            System.arraycopy(tmp, 0, out, ra, rb - ra);
            if (COUNTERS) { EXACT.addAndGet(rb - ra); BOUND_MISSES.incrementAndGet(); }
        }
        com.worldgenv2.density.ColumnPool.give(th); com.worldgenv2.density.ColumnPool.give(tl);
        com.worldgenv2.density.ColumnPool.give(hi); com.worldgenv2.density.ColumnPool.give(lo);
    }
    /** Tiles being computed right now, by whichever thread claimed them first. */
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<ImmutableChunkSnapshot<T>>> tilesInFlight =
        new java.util.concurrent.ConcurrentHashMap<>();

    private ImmutableChunkSnapshot<T> cachedTile(int cx, int cz, int minY, int maxY) {
        ImmutableChunkSnapshot<T> snap = contextCache.getSnapshot(cx, cz, 0);
        return snap != null && snap.getMinY() == minY && snap.getMaxY() == maxY ? snap : null;
    }

    /**
     * The tile if it is ready (cached, or computed right here because nobody else is on it), otherwise the future of the thread that
     * is computing it. Callers that need many tiles take everything they can compute first and wait only at the end.
     * (Computing a tile never asks for another tile, so waiting cannot deadlock.)
     */
    @SuppressWarnings("unchecked")
    private Object tileOrPending(int cx, int cz, int minY, int maxY) {
        ImmutableChunkSnapshot<T> snap = cachedTile(cx, cz, minY, maxY);
        if (snap != null) { TILE_HITS.incrementAndGet(); return snap; }
        long key = StageContextCache.key(cx, cz, 0);
        java.util.concurrent.CompletableFuture<ImmutableChunkSnapshot<T>> mine = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<ImmutableChunkSnapshot<T>> other = tilesInFlight.putIfAbsent(key, mine);
        if (other != null) return other;
        try {
            snap = cachedTile(cx, cz, minY, maxY);       // finished between our first look and the claim
            if (snap == null) {
                TILES_COMPUTED.incrementAndGet();
                long t0 = COUNTERS ? System.nanoTime() : 0;
                int height = maxY - minY;
                ColumnScratch scratch = tileScratch.get();
                scratch.ensureCapacity(height);
                TerrainTileSpace<T> space = new TerrainTileSpace<>(cx, cz, minY, maxY, airBlock);
                terrainStage(cx, cz, space, null, scratch);
                snap = space.toSnapshot(cx, cz);
                if (COUNTERS) TILE_NANOS.addAndGet(System.nanoTime() - t0);
                contextCache.putSnapshot(cx, cz, 0, snap);
            }
            mine.complete(snap);
            return snap;
        } catch (Throwable ex) {
            mine.completeExceptionally(ex);
            throw ex;
        } finally {
            tilesInFlight.remove(key, mine);
        }
    }

    @SuppressWarnings("unchecked")
    private ImmutableChunkSnapshot<T> getTerrainTile(int cx, int cz, int minY, int maxY) {
        Object o = tileOrPending(cx, cz, minY, maxY);
        if (!(o instanceof java.util.concurrent.CompletableFuture<?> f)) return (ImmutableChunkSnapshot<T>) o;
        long w0 = COUNTERS ? System.nanoTime() : 0;
        ImmutableChunkSnapshot<T> snap = (ImmutableChunkSnapshot<T>) f.join();
        if (COUNTERS) WAIT_NANOS.addAndGet(System.nanoTime() - w0);
        return snap;
    }

    /**
     * The blocks of one chunk for a level-of-detail renderer (Distant Horizons), without going through Minecraft's chunk pipeline.
     * Terrain only comes straight from the shared terrain tile (the same one real chunks copy); with props, the chunk is generated
     * into a scratch column space exactly as a real chunk is.
     */
    public ImmutableChunkSnapshot<T> lodSnapshot(int chunkX, int chunkZ, int minY, int maxY, boolean withProps) {
        if (!withProps) return getTerrainTile(chunkX, chunkZ, minY, maxY);
        TerrainTileSpace<T> space = new TerrainTileSpace<>(chunkX, chunkZ, minY, maxY, airBlock);
        generateChunk(chunkX, chunkZ, space, null);
        return space.toSnapshot(chunkX, chunkZ);
    }

    /** For the prop pass: a tile, or the future of a tile someone else is computing. */
    Object terrainTileOrPending(int cx, int cz, int minY, int maxY) { return tileOrPending(cx, cz, minY, maxY); }

    /** Waits for a tile someone else is computing. */
    @SuppressWarnings("unchecked")
    ImmutableChunkSnapshot<T> awaitTile(Object pending) {
        if (!(pending instanceof java.util.concurrent.CompletableFuture<?> f)) return (ImmutableChunkSnapshot<T>) pending;
        long w0 = COUNTERS ? System.nanoTime() : 0;
        ImmutableChunkSnapshot<T> snap = (ImmutableChunkSnapshot<T>) f.join();
        if (COUNTERS) WAIT_NANOS.addAndGet(System.nanoTime() - w0);
        return snap;
    }
    private record ResolvedOre<B>(B ore, java.util.Set<B> replaceable) {}

    private final java.util.concurrent.ConcurrentHashMap<com.worldgenv2.worldstructure.OreRule, ResolvedOre<T>> oreCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final String[] DEFAULT_REPLACEABLE = {
        "minecraft:stone", "minecraft:deepslate", "minecraft:andesite", "minecraft:granite", "minecraft:diorite", "minecraft:tuff"
    };

    private ResolvedOre<T> resolveOre(com.worldgenv2.worldstructure.OreRule rule) {
        return oreCache.computeIfAbsent(rule, r -> {
            T ore = prefabBlockResolver.apply(r.block());
            java.util.Set<T> rep = new java.util.HashSet<>();
            java.util.List<String> ids = r.replaces().isEmpty() ? java.util.List.of(DEFAULT_REPLACEABLE) : r.replaces();
            for (String id : ids) {
                T b = prefabBlockResolver.apply(id);
                if (b != null) rep.add(b);
            }
            if (r.replaces().isEmpty() && baseSolidBlock != null) rep.add(baseSolidBlock);
            return new ResolvedOre<>(ore, rep);
        });
    }

    private static long mix(long a, long b, int x, int z) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + x * 0x94D049BB133111EBL + z * 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Veins are anchored to the chunk they originate in and may spill at most ~6 blocks, so each chunk evaluates the
     * veins of itself and its 8 neighbours (same seed each time) and keeps only the blocks that land inside it.
     * That keeps veins identical regardless of generation order.
     */
    private void generateOres(int chunkX, int chunkZ, VoxelSpace<T> space) {
        java.util.List<com.worldgenv2.worldstructure.OreRule> rules = worldStructure.getOreRules();
        int minWorldY = space.getMinY(), maxWorldY = space.getMaxY();
        for (com.worldgenv2.worldstructure.OreRule rule : rules) {
            ResolvedOre<T> ro = resolveOre(rule);
            if (ro.ore() == null) continue;
            boolean biomeFilter = !rule.allowedBiomes().isEmpty();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ox = chunkX + dx, oz = chunkZ + dz;
                    java.util.SplittableRandom rnd = new java.util.SplittableRandom(mix(worldStructure.seed(), rule.id().hashCode(), ox, oz));
                    for (int n = 0; n < rule.count(); n++) {
                        int vx = (ox << 4) + rnd.nextInt(16), vz = (oz << 4) + rnd.nextInt(16);
                        int range = Math.max(1, rule.maxY() - rule.minY());
                        int vy = rule.triangle()
                            ? rule.minY() + (rnd.nextInt(range / 2 + 1) + rnd.nextInt(range - range / 2 + 1))
                            : rule.minY() + rnd.nextInt(range + 1);
                        int sx = rnd.nextInt(3) - 1, sy = rnd.nextInt(3) - 1, sz = rnd.nextInt(3) - 1;
                        long walkSeed = rnd.nextLong(); // always consumed so neighbouring chunks stay in sync
                        if (vx < (chunkX << 4) - 8 || vx > (chunkX << 4) + 23 || vz < (chunkZ << 4) - 8 || vz > (chunkZ << 4) + 23) continue;
                        if (biomeFilter) {
                            var q = worldStructure.queryBiome(vx, vz);
                            String name = q.primaryTileBiome() == null ? "" : q.primaryTileBiome().name();
                            boolean ok = false;
                            for (String a : rule.allowedBiomes()) {
                                if (name.equalsIgnoreCase(a) || name.endsWith(":" + a)) { ok = true; break; }
                            }
                            if (!ok) continue;
                        }
                        java.util.SplittableRandom walk = new java.util.SplittableRandom(walkSeed);
                        int px = vx, py = vy, pz = vz;
                        for (int k = 0; k < rule.veinSize(); k++) {
                            if ((px >> 4) == chunkX && (pz >> 4) == chunkZ && py >= minWorldY && py < maxWorldY) {
                                T cur = space.getBlock(px, py, pz);
                                if (ro.replaceable().contains(cur)) space.setBlock(px, py, pz, ro.ore());
                            }
                            px += walk.nextInt(3) - 1 + (walk.nextInt(4) == 0 ? sx : 0);
                            py += walk.nextInt(3) - 1 + (walk.nextInt(4) == 0 ? sy : 0);
                            pz += walk.nextInt(3) - 1 + (walk.nextInt(4) == 0 ? sz : 0);
                            px = Math.max(vx - 6, Math.min(vx + 6, px));
                            py = Math.max(vy - 6, Math.min(vy + 6, py));
                            pz = Math.max(vz - 6, Math.min(vz + 6, pz));
                        }
                    }
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
