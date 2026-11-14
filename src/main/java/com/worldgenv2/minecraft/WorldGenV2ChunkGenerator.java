package com.worldgenv2.minecraft;

import com.google.common.collect.Sets;
import com.worldgenv2.engine.chunkgenerator.StagedChunkGenerator;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.worldstructure.TileBiome;
import com.worldgenv2.worldstructure.WorldStructure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Native Minecraft 26.1 {@link ChunkGenerator} powered by Worldgen V2's {@link StagedChunkGenerator}.
 * Executes 18x18 halo column density batching, 3D volumetric overhangs, central-difference surface normals,
 * {@code SpaceAndDepth} geological layering, and multi-surface prop placement.
 */
public final class WorldGenV2ChunkGenerator extends ChunkGenerator {

    public static final MapCodec<WorldGenV2ChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(WorldGenV2ChunkGenerator::getBiomeSource),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(WorldGenV2ChunkGenerator::getSeaLevel),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(WorldGenV2ChunkGenerator::getMinY),
            Codec.INT.optionalFieldOf("gen_depth", 384).forGetter(WorldGenV2ChunkGenerator::getGenDepth),
            Codec.STRING.optionalFieldOf("preset", "custom").forGetter(g -> g.heightConfig.preset().getId()),
            Codec.STRING.optionalFieldOf("default_block", "minecraft:stone").forGetter(WorldGenV2ChunkGenerator::getDefaultBlockId),
            Codec.STRING.optionalFieldOf("default_fluid", "minecraft:water").forGetter(WorldGenV2ChunkGenerator::getDefaultFluidId),
            Codec.BOOL.optionalFieldOf("has_ceiling", false).forGetter(WorldGenV2ChunkGenerator::hasCeiling),
            Codec.INT.optionalFieldOf("ceiling_y", 127).forGetter(WorldGenV2ChunkGenerator::getCeilingY),
            Codec.INT.optionalFieldOf("ceiling_thickness", 5).forGetter(WorldGenV2ChunkGenerator::getCeilingThickness),
            Codec.BOOL.optionalFieldOf("deep_lava_flooding", true).forGetter(WorldGenV2ChunkGenerator::hasDeepLavaFlooding)
        ).apply(instance, (biomeSource, seaLevel, minY, genDepth, preset, defaultBlock, defaultFluid, hasCeiling, ceilingY, ceilingThickness, deepLava) ->
            new WorldGenV2ChunkGenerator(
                biomeSource,
                WorldHeightConfig.of(preset, minY, genDepth, seaLevel),
                defaultBlock,
                defaultFluid,
                hasCeiling,
                ceilingY,
                ceilingThickness,
                deepLava
            )
        )
    );

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final int LAVA_LAYERS = 8;
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState ROAD_DECK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState ROAD_SHOULDER = Blocks.COBBLESTONE.defaultBlockState();

    private final WorldHeightConfig heightConfig;
    private final int seaLevel;
    private final int minY;
    private final int genDepth;
    private final String defaultBlockId;
    private final String defaultFluidId;
    private final BlockState defaultBlockState;
    private final BlockState defaultFluidState;
    private final boolean hasCeiling;
    private final int ceilingY;
    private final int ceilingThickness;
    private final boolean deepLavaFlooding;
    private final MaterialProvider<BlockState> defaultMaterialProvider;

    private volatile WorldStructure fallbackWorldStructure;
    private volatile StagedChunkGenerator<BlockState> stagedGenerator;

    public WorldGenV2ChunkGenerator(
        BiomeSource biomeSource,
        WorldHeightConfig heightConfig,
        String defaultBlockId,
        String defaultFluidId,
        boolean hasCeiling,
        int ceilingY,
        int ceilingThickness,
        boolean deepLavaFlooding
    ) {
        super(biomeSource);
        this.heightConfig = heightConfig != null ? heightConfig : WorldHeightConfig.custom(-64, 384, 63);
        this.seaLevel = this.heightConfig.seaLevel();
        this.minY = this.heightConfig.minY();
        this.genDepth = this.heightConfig.genDepth();
        this.defaultBlockId = defaultBlockId != null ? defaultBlockId : "minecraft:stone";
        this.defaultFluidId = defaultFluidId != null ? defaultFluidId : "minecraft:water";
        this.defaultBlockState = resolveBlockState(this.defaultBlockId, STONE);
        this.defaultFluidState = resolveBlockState(this.defaultFluidId, WATER);
        this.hasCeiling = hasCeiling;
        this.ceilingY = ceilingY;
        this.ceilingThickness = Math.max(1, ceilingThickness);
        this.deepLavaFlooding = deepLavaFlooding;
        this.defaultMaterialProvider = DefaultWorldFactory.createDefaultMaterialProvider();
        this.stagedGenerator = createStagedGenerator(resolveWorldStructure());
    }

    public WorldGenV2ChunkGenerator(BiomeSource biomeSource, WorldHeightConfig heightConfig) {
        this(biomeSource, heightConfig, "minecraft:stone", "minecraft:water", false, 127, 5, true);
    }

    public WorldGenV2ChunkGenerator(BiomeSource biomeSource, int seaLevel, int minY, int genDepth) {
        this(biomeSource, WorldHeightConfig.custom(minY, genDepth, seaLevel));
    }

    public static BlockState resolveBlockState(String blockId, BlockState fallback) {
        if (blockId == null || blockId.isBlank()) {
            return fallback;
        }
        try {
            int br = blockId.indexOf('[');
            String base = br < 0 ? blockId : blockId.substring(0, br);
            Identifier loc = Identifier.tryParse(base);
            if (loc != null && BuiltInRegistries.BLOCK.containsKey(loc)) {
                BlockState st = BuiltInRegistries.BLOCK.getValue(loc).defaultBlockState();
                if (br >= 0 && blockId.endsWith("]")) {
                    for (String kv : blockId.substring(br + 1, blockId.length() - 1).split(",")) {
                        int eq = kv.indexOf('=');
                        if (eq <= 0) continue;
                        net.minecraft.world.level.block.state.properties.Property<?> p =
                                st.getBlock().getStateDefinition().getProperty(kv.substring(0, eq).trim());
                        if (p != null) st = withProperty(st, p, kv.substring(eq + 1).trim());
                    }
                }
                return st;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static <V extends Comparable<V>> BlockState withProperty(BlockState s, net.minecraft.world.level.block.state.properties.Property<V> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    public WorldHeightConfig getHeightConfig() {
        return this.heightConfig;
    }

    public String getDefaultBlockId() {
        return this.defaultBlockId;
    }

    public String getDefaultFluidId() {
        return this.defaultFluidId;
    }

    public BlockState getDefaultBlockState() {
        return this.defaultBlockState;
    }

    public BlockState getDefaultFluidState() {
        return this.defaultFluidState;
    }

    public boolean hasCeiling() {
        return this.hasCeiling;
    }

    public int getCeilingY() {
        return this.ceilingY;
    }

    public int getCeilingThickness() {
        return this.ceilingThickness;
    }

    public boolean hasDeepLavaFlooding() {
        return this.deepLavaFlooding;
    }

    private WorldStructure resolveWorldStructure() {
        if (this.biomeSource instanceof WorldGenV2BiomeSource v2BiomeSource) {
            return v2BiomeSource.getWorldStructure();
        }
        if (this.fallbackWorldStructure == null) {
            this.fallbackWorldStructure = DefaultWorldFactory.createDefaultWorldStructure(
                1337L,
                DefaultWorldFactory.DEFAULT_VORONOI_CELL_SIZE
            );
        }
        return this.fallbackWorldStructure;
    }

    // ---- Worldgen V2 owns all generation: nothing from vanilla's feature or structure pipeline may run ----

    /** Vanilla trees, ores, flowers, lakes and springs come from the mapped Minecraft biome; they must not generate. */
    @Override
    public void applyBiomeDecoration(
        net.minecraft.world.level.WorldGenLevel level,
        net.minecraft.world.level.chunk.ChunkAccess chunk,
        net.minecraft.world.level.StructureManager structureManager
    ) {
        // intentionally empty: props, ores and structures are produced by StagedChunkGenerator
    }

    /** Vanilla villages, strongholds, mineshafts etc. would otherwise be placed by biome tags. */
    @Override
    public void createStructures(
        net.minecraft.core.RegistryAccess registryAccess,
        net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState,
        net.minecraft.world.level.StructureManager structureManager,
        net.minecraft.world.level.chunk.ChunkAccess chunk,
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension
    ) {
        // intentionally empty
    }

    @Override
    public void createReferences(
        net.minecraft.world.level.WorldGenLevel level,
        net.minecraft.world.level.StructureManager structureManager,
        net.minecraft.world.level.chunk.ChunkAccess chunk
    ) {
        // intentionally empty: there are no vanilla structure starts to reference
    }

    private StagedChunkGenerator<BlockState> createStagedGenerator(WorldStructure structure) {
        return new StagedChunkGenerator<>(
            structure,
            this.defaultMaterialProvider,
            List.of(),
            AIR,
            this.defaultBlockState,
            ROAD_DECK,
            ROAD_SHOULDER
        ).withPrefabResolver(id -> resolveBlockState(id, null));
    }

    /**
     * Rebuilds the underlying {@link WorldStructure} and {@link StagedChunkGenerator} (invoked by {@code /worldgenv2 reload}).
     */
    public synchronized void reload() {
        if (this.biomeSource instanceof WorldGenV2BiomeSource v2BiomeSource) {
            v2BiomeSource.reload();
        } else {
            this.fallbackWorldStructure = DefaultWorldFactory.createDefaultWorldStructure(
                1337L,
                DefaultWorldFactory.DEFAULT_VORONOI_CELL_SIZE
            );
        }
        this.stagedGenerator = createStagedGenerator(resolveWorldStructure());
    }

    public WorldStructure getActiveWorldStructure() {
        return resolveWorldStructure();
    }

    /**
     * Live-regenerates a loaded chunk in-place on the server thread and broadcasts the updated
     * chunk sections and heightmaps to all players in the level so Studio parameter edits appear immediately.
     */
    public void regenerateLiveChunk(net.minecraft.server.level.ServerLevel level, ChunkPos chunkPos) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        int chunkMinY = heightAccessor.getMinY();
        int chunkMaxY = chunkMinY + heightAccessor.getHeight();
        int bottomSectionIndex = chunk.getSectionIndex(chunkMinY);
        int topSectionIndex = chunk.getSectionIndex(chunkMaxY - 1);

        Set<LevelChunkSection> lockedSections = Sets.newHashSet();
        for (int sectionIdx = topSectionIndex; sectionIdx >= bottomSectionIndex; sectionIdx--) {
            LevelChunkSection section = chunk.getSection(sectionIdx);
            section.acquire();
            lockedSections.add(section);
        }

        try {
            MinecraftVoxelSpace voxelSpace = new MinecraftVoxelSpace(chunk);
            voxelSpace.clearAllToAir();

            this.stagedGenerator.generateChunk(chunkPos.x(), chunkPos.z(), voxelSpace);
            applyBedrockAndSeaWater(chunkPos, voxelSpace);

            Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.WORLD_SURFACE,
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.WORLD_SURFACE_WG
            ));
            chunk.setLightCorrect(false);
            for (LevelChunkSection section : chunk.getSections()) {
                if (section != null && !section.hasOnlyAir()) {
                    section.recalcBlockCounts();
                }
            }
            chunk.markUnsaved();
        } finally {
            for (LevelChunkSection section : lockedSections) {
                section.release();
            }
        }

        net.minecraft.server.level.ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        
        // 1. Immediately send chunk block updates so the viewport renders block modifications without delay
        net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket initialPacket =
            new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                chunk,
                lightEngine,
                null,
                null
            );
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            player.connection.send(initialPacket);
        }

        // 2. Queue asynchronous chunk relighting so sky light & block light are fully recalculated
        lightEngine.lightChunk(chunk, false).thenRun(() -> {
            level.getServer().execute(() -> {
                net.minecraft.network.protocol.game.ClientboundLightUpdatePacket lightPacket =
                    new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(
                        chunkPos,
                        lightEngine,
                        null,
                        null
                    );
                for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                    player.connection.send(lightPacket);
                }
            });
        });
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(
        HolderLookup<StructureSet> structureSets,
        RandomState randomState,
        long levelSeed
    ) {
        if (this.biomeSource instanceof WorldGenV2BiomeSource v2BiomeSource) {
            v2BiomeSource.ensureWorldSeed(levelSeed);
            this.stagedGenerator = createStagedGenerator(v2BiomeSource.getWorldStructure());
        } else {
            this.fallbackWorldStructure = DefaultWorldFactory.createDefaultWorldStructure(
                levelSeed,
                DefaultWorldFactory.DEFAULT_VORONOI_CELL_SIZE
            );
            this.stagedGenerator = createStagedGenerator(this.fallbackWorldStructure);
        }
        return super.createState(structureSets, randomState, levelSeed);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        ChunkAccess centerChunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            LevelHeightAccessor heightAccessor = centerChunk.getHeightAccessorForGeneration();
            int chunkMinY = heightAccessor.getMinY();
            int chunkMaxY = chunkMinY + heightAccessor.getHeight();
            int bottomSectionIndex = centerChunk.getSectionIndex(chunkMinY);
            int topSectionIndex = centerChunk.getSectionIndex(chunkMaxY - 1);

            Set<LevelChunkSection> lockedSections = Sets.newHashSet();
            for (int sectionIdx = topSectionIndex; sectionIdx >= bottomSectionIndex; sectionIdx--) {
                LevelChunkSection section = centerChunk.getSection(sectionIdx);
                section.acquire();
                lockedSections.add(section);
            }

            try {
                MinecraftVoxelSpace voxelSpace = new MinecraftVoxelSpace(centerChunk);
                ChunkPos chunkPos = centerChunk.getPos();

                // Evaluate Minecraft structure Beardifier so village houses on slopes receive natural terrain
                // foundations beneath them and hillsides are excavated around structure interiors
                Beardifier beardifier = Beardifier.forStructuresInChunk(structureManager, chunkPos);
                com.worldgenv2.density.DensityFunction beardifierDensity = new com.worldgenv2.density.DensityFunction() {
                    @Override
                    public float compute(float x, float y, float z) {
                        double b = beardifier.compute(new SinglePointContext(Math.round(x), Math.round(y), Math.round(z)));
                        if (b < -0.002) {
                            // Aggressive excavation: clears rock from ancient cities, strongholds & rooms
                            return (float) (b * 28.0);
                        } else if (b > 0.002) {
                            // Foundation beard: supports structure floors across dips
                            return (float) (b * 16.0);
                        }
                        return 0.0f;
                    }

                    @Override
                    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                        int ix = Math.round(x);
                        int iz = Math.round(z);
                        int height = maxY - minY;
                        for (int i = 0; i < height; i++) {
                            double b = beardifier.compute(new SinglePointContext(ix, minY + i, iz));
                            if (b < -0.002) {
                                out[i] = (float) (b * 28.0);
                            } else if (b > 0.002) {
                                out[i] = (float) (b * 16.0);
                            } else {
                                out[i] = 0.0f;
                            }
                        }
                    }
                };

                // Execute Worldgen V2 StagedChunkGenerator (Biome Halo -> 3D Density + Beardifier -> SpaceAndDepth Materials -> Props -> Landmarks)
                this.stagedGenerator.generateChunk(chunkPos.x(), chunkPos.z(), voxelSpace, beardifierDensity);

                // Apply bedrock floor and fill sea-level water basins
                applyBedrockAndSeaWater(chunkPos, voxelSpace);
            } finally {
                for (LevelChunkSection section : lockedSections) {
                    section.release();
                }
            }

            return centerChunk;
        }, Util.backgroundExecutor().forName("worldgen_v2_staged_gen"));
    }

    private void applyBedrockAndSeaWater(ChunkPos chunkPos, MinecraftVoxelSpace voxelSpace) {
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        int floorY = voxelSpace.getMinY();
        int clampedDimensionSeaLevel = Math.min(this.seaLevel, voxelSpace.getMaxY() - 1);
        WorldStructure structure = resolveWorldStructure();
        var dimLayout = structure.getDimensionLayout();
        if (dimLayout != null) {
            clampedDimensionSeaLevel = Math.min(dimLayout.seaLevelOverride(), voxelSpace.getMaxY() - 1);
        }

        for (int lx = 0; lx < 16; lx++) {
            int worldX = minBlockX + lx;
            for (int lz = 0; lz < 16; lz++) {
                int worldZ = minBlockZ + lz;

                boolean isVoidCol = structure.isVoidColumn(worldX, worldZ);
                if (!isVoidCol) {
                    // 1. Seal bottom world boundary with bedrock (skipped in Void zones / End islands)
                    voxelSpace.setBlock(worldX, floorY, worldZ, BEDROCK);

                    // 2. Query Biome-specific environment overrides (fluid, sea level)
                    WorldStructure.BiomeQueryResult biomeQuery = structure.queryBiome(worldX, worldZ);
                    TileBiome tileBiome = biomeQuery.primaryTileBiome();

                    int effectiveSeaLevel = clampedDimensionSeaLevel;
                    if (tileBiome != null && tileBiome.seaLevel() != null) {
                        effectiveSeaLevel = Math.min(tileBiome.seaLevel(), voxelSpace.getMaxY() - 1);
                    }

                    BlockState effectiveFluid = this.defaultFluidState;
                    if (dimLayout != null && dimLayout.defaultFluidId() != null) {
                        effectiveFluid = resolveBlockState(dimLayout.defaultFluidId(), this.defaultFluidState);
                    }
                    if (tileBiome != null && tileBiome.fluidBlock() != null) {
                        effectiveFluid = resolveBlockState(tileBiome.fluidBlock(), effectiveFluid);
                    }

                    // Rivers carry their own water level (flush with the land they run through), which can sit above sea level
                    int riverLevel = structure.riverWaterLevel(worldX, worldZ);
                    if (riverLevel != Integer.MIN_VALUE) {
                        effectiveSeaLevel = Math.min(Math.max(effectiveSeaLevel, riverLevel), voxelSpace.getMaxY() - 1);
                    }

                    // 3. Fill the air at and below sea level downward until the first solid block. Starting from the sea
                    // surface (rather than above the topmost solid) keeps water under anything that stands above it:
                    // a fallen-log bridge, an arch, a pier.
                    if (!effectiveFluid.isAir()) {
                        for (int y = effectiveSeaLevel; y > floorY && voxelSpace.isAir(worldX, y, worldZ); y--) {
                            voxelSpace.setBlock(worldX, y, worldZ, effectiveFluid);
                        }
                    }

                    // 4. Molten lava / fluid pooling in the deepest cave floors
                    if (this.deepLavaFlooding) {
                        for (int y = floorY + 1; y <= floorY + LAVA_LAYERS; y++) {
                            if (voxelSpace.isAir(worldX, y, worldZ)) {
                                voxelSpace.setBlock(worldX, y, worldZ, LAVA);
                            }
                        }
                    }
                }

                // 5. Enclosed Bedrock Ceiling & Buildable Space Above Roof
                if (this.hasCeiling) {
                    int roofTop = Math.min(this.ceilingY, voxelSpace.getMaxY() - 1);
                    int roofBottom = Math.max(floorY + 1, roofTop - this.ceilingThickness + 1);
                    for (int y = roofBottom; y <= roofTop; y++) {
                        voxelSpace.setBlock(worldX, y, worldZ, BEDROCK);
                    }
                    // All airspace above ceilingY up to maxY remains open air for player building (like vanilla Nether roof)
                    for (int y = roofTop + 1; y < voxelSpace.getMaxY(); y++) {
                        voxelSpace.setBlock(worldX, y, worldZ, AIR);
                    }
                }
            }
        }

        // 6. Stamp Circular Ring Landmarks (e.g. 10 End Obsidian Pillars) if any intersect this chunk
        if (dimLayout != null && !dimLayout.resolvedPillars().isEmpty()) {
            stampRingPillarsInChunk(chunkPos, voxelSpace, dimLayout.resolvedPillars());
        }
    }

    private void stampRingPillarsInChunk(
        ChunkPos chunkPos,
        MinecraftVoxelSpace voxelSpace,
        List<com.worldgenv2.worldstructure.DimensionLayoutConfig.PlacedRingPillar> pillars
    ) {
        int minBlockX = chunkPos.getMinBlockX();
        int maxBlockX = minBlockX + 15;
        int minBlockZ = chunkPos.getMinBlockZ();
        int maxBlockZ = minBlockZ + 15;
        int minY = Math.max(0, voxelSpace.getMinY());
        int maxY = voxelSpace.getMaxY();

        for (var pillar : pillars) {
            int r = pillar.radius();
            if (pillar.centerX() + r < minBlockX || pillar.centerX() - r > maxBlockX
                || pillar.centerZ() + r < minBlockZ || pillar.centerZ() - r > maxBlockZ) {
                continue;
            }
            BlockState pillarBlock = resolveBlockState(pillar.pillarBlockId(), Blocks.OBSIDIAN.defaultBlockState());
            BlockState capBlock = resolveBlockState(pillar.capBlockId(), BEDROCK);
            int topY = Math.min(maxY - 2, pillar.topY());
            float rSq = (r + 0.35f) * (r + 0.35f);

            for (int wx = Math.max(minBlockX, pillar.centerX() - r); wx <= Math.min(maxBlockX, pillar.centerX() + r); wx++) {
                int dx = wx - pillar.centerX();
                for (int wz = Math.max(minBlockZ, pillar.centerZ() - r); wz <= Math.min(maxBlockZ, pillar.centerZ() + r); wz++) {
                    int dz = wz - pillar.centerZ();
                    if (dx * dx + dz * dz <= rSq) {
                        for (int y = minY; y <= topY; y++) {
                            voxelSpace.setBlock(wx, y, wz, pillarBlock);
                        }
                        if (dx == 0 && dz == 0 && topY + 1 < maxY) {
                            voxelSpace.setBlock(wx, topY + 1, wz, capBlock);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void buildSurface(
        WorldGenRegion level,
        StructureManager structureManager,
        RandomState randomState,
        ChunkAccess protoChunk
    ) {
        // Bypassed: Worldgen V2 resolves SpaceAndDepth materials and 3D slope normals inside StagedChunkGenerator
    }

    @Override
    public void applyCarvers(
        WorldGenRegion region,
        long seed,
        RandomState randomState,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk
    ) {
        // Bypassed: Volumetric 3D caverns and arches are evaluated natively in the density graph
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        ChunkPos chunkPos = worldGenRegion.getCenter();
        var biome = worldGenRegion.getBiome(chunkPos.getWorldPosition().atY(worldGenRegion.getMaxY() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupportSeed(chunkPos)));
        NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, biome, chunkPos, random);
    }

    private static long RandomSupportSeed(ChunkPos chunkPos) {
        return ((long) chunkPos.x() * 341873128712L) ^ ((long) chunkPos.z() * 132897987541L);
    }

    @Override
    public int getBaseHeight(
        int x,
        int z,
        Heightmap.Types type,
        LevelHeightAccessor heightAccessor,
        RandomState randomState
    ) {
        int colMinY = heightAccessor.getMinY();
        int colMaxY = colMinY + heightAccessor.getHeight();
        int height = colMaxY - colMinY;
        if (height <= 0) {
            return colMinY;
        }

        WorldStructure structure = resolveWorldStructure();
        WorldStructure.BiomeQueryResult biomeQuery = structure.queryBiome(x, z);
        float[] colBuffer = new float[height];
        float[] scratch = new float[height];
        biomeQuery.computeBlendedColumn(x, z, colMinY, colMaxY, colBuffer, scratch);
        structure.applyTerrainModifiers(x, z, colMinY, colMaxY, colBuffer);
        if (structure.getSplineRoadEngine() != null) {
            structure.getSplineRoadEngine().applyRoadToColumn(x, z, colMinY, colMaxY, colBuffer);
        }

        TileBiome tileBiome = biomeQuery.primaryTileBiome();
        int effectiveSeaLevel = (tileBiome != null && tileBiome.seaLevel() != null) ? tileBiome.seaLevel() : this.seaLevel;
        BlockState effectiveFluid = (tileBiome != null && tileBiome.fluidBlock() != null)
            ? resolveBlockState(tileBiome.fluidBlock(), this.defaultFluidState)
            : this.defaultFluidState;

        boolean isVoidCol = structure.isVoidColumn(x, z);
        boolean stopOnFluid = !isVoidCol && type.isOpaque().test(effectiveFluid);
        for (int y = colMaxY - 1; y >= colMinY; y--) {
            boolean isSolid = (!isVoidCol && y == colMinY) || (colBuffer[y - colMinY] > 0.0f);
            if (isSolid) {
                return y + 1;
            }
            if (stopOnFluid && y <= effectiveSeaLevel) {
                return y + 1;
            }
        }
        return colMinY;
    }

    @Override
    public NoiseColumn getBaseColumn(
        int x,
        int z,
        LevelHeightAccessor heightAccessor,
        RandomState randomState
    ) {
        int colMinY = heightAccessor.getMinY();
        int colMaxY = colMinY + heightAccessor.getHeight();
        int height = colMaxY - colMinY;
        if (height <= 0) {
            return new NoiseColumn(colMinY, new BlockState[0]);
        }

        WorldStructure structure = resolveWorldStructure();
        WorldStructure.BiomeQueryResult biomeQuery = structure.queryBiome(x, z);
        float[] colBuffer = new float[height];
        float[] scratch = new float[height];
        biomeQuery.computeBlendedColumn(x, z, colMinY, colMaxY, colBuffer, scratch);
        structure.applyTerrainModifiers(x, z, colMinY, colMaxY, colBuffer);
        if (structure.getSplineRoadEngine() != null) {
            structure.getSplineRoadEngine().applyRoadToColumn(x, z, colMinY, colMaxY, colBuffer);
        }

        TileBiome tileBiome = biomeQuery.primaryTileBiome();
        int effectiveSeaLevel = (tileBiome != null && tileBiome.seaLevel() != null) ? tileBiome.seaLevel() : this.seaLevel;
        BlockState effectiveFluid = (tileBiome != null && tileBiome.fluidBlock() != null)
            ? resolveBlockState(tileBiome.fluidBlock(), this.defaultFluidState)
            : this.defaultFluidState;

        boolean isVoidCol = structure.isVoidColumn(x, z);
        BlockState[] states = new BlockState[height];
        states[0] = isVoidCol ? AIR : BEDROCK;
        for (int i = 1; i < height; i++) {
            int y = colMinY + i;
            if (this.hasCeiling && y <= this.ceilingY && y > this.ceilingY - this.ceilingThickness) {
                states[i] = BEDROCK;
            } else if (this.hasCeiling && y > this.ceilingY) {
                states[i] = AIR;
            } else if (colBuffer[i] > 0.0f) {
                states[i] = this.defaultBlockState;
            } else if (!isVoidCol && y <= effectiveSeaLevel) {
                states[i] = effectiveFluid;
            } else {
                states[i] = AIR;
            }
        }
        return new NoiseColumn(colMinY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        DecimalFormat fmt = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
        WorldStructure.BiomeQueryResult query = resolveWorldStructure().queryBiome(feetPos.getX(), feetPos.getZ());
        float density = query.computeBlendedPoint(feetPos.getX(), feetPos.getY(), feetPos.getZ());
        result.add(
            "[Worldgen V2 Engine] D(x,y,z): " + fmt.format(density)
                + " | Biome: " + query.primaryTileBiome().name()
                + " | EdgeDist: " + fmt.format(query.distanceToEdge())
        );
    }

    @Override
    public int getGenDepth() {
        return this.genDepth;
    }

    @Override
    public int getSeaLevel() {
        return this.seaLevel;
    }

    @Override
    public int getMinY() {
        return this.minY;
    }
}
