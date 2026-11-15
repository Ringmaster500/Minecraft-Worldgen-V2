package com.worldgenv2.minecraft;

import com.worldgenv2.curve.DistanceExponentialCurve;
import com.worldgenv2.curve.DistanceSCurve;
import com.worldgenv2.density.CurveMapperDensity;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.density.FastNoiseLite;
import com.worldgenv2.density.GradientWarpDensity;
import com.worldgenv2.density.Noise2dDensity;
import com.worldgenv2.density.Noise3dDensity;
import com.worldgenv2.density.SumDensity;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.density.YSampledDensity;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.material.QueueMaterialProvider;
import com.worldgenv2.material.SpaceAndDepthMaterialProvider;
import com.worldgenv2.material.WeightedMaterialProvider;
import com.worldgenv2.material.patterns.AndPattern;
import com.worldgenv2.material.patterns.CeilingPattern;
import com.worldgenv2.material.patterns.SlopePattern;
import com.worldgenv2.material.patterns.SpaceCheckPattern;
import com.worldgenv2.material.patterns.SurfacePattern;
import com.worldgenv2.props.Prop;
import com.worldgenv2.worldstructure.CaveCarver;
import com.worldgenv2.worldstructure.ClimateTarget;
import com.worldgenv2.worldstructure.ConditionalSpawnProvider;
import com.worldgenv2.worldstructure.CustomBiome;
import com.worldgenv2.worldstructure.MacroFeatureGraph;
import com.worldgenv2.worldstructure.RiverCarver;
import com.worldgenv2.worldstructure.SplineRoadEngine;
import com.worldgenv2.worldstructure.TileBiome;
import com.worldgenv2.worldstructure.WorldStructure;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the default standalone Worldgen V2 {@link WorldStructure} for Minecraft 26.1.
 * Configures native volumetric Minecraft biomes (Temperate Forest, Arid Dunes, Alpine Peaks,
 * and the Old Growth Grove CustomBiome overlay) with full 3D overhangs, arches,
 * SpaceAndDepth geological strata, and surface flora props.
 */
public final class DefaultWorldFactory {

    public static final int BIOME_TEMPERATE_FOREST = 0;
    public static final int BIOME_ARID_DUNES = 1;
    public static final int BIOME_ALPINE_PEAKS = 2;
    public static final int BIOME_OLD_GROWTH_GROVE = 3;
    public static final int BIOME_SUNLIT_PLAINS = 4;

    public static final int DEFAULT_VORONOI_CELL_SIZE = 288;
    public static final int SEA_LEVEL = 63;

    /** Gentle, buildable land biomes where watchtowers and waystations may stand. */
    private static final Set<String> LANDMARK_BIOMES = Set.of(
        "TemperateForest", "SunlitPlains", "AridDunes", "AlpinePeaks", "Savanna", "BirchForest", "FlowerForest",
        "Meadow", "Taiga", "SnowyPlains", "SnowyTaiga", "Badlands", "WindsweptHills", "CherryGrove", "DarkForest"
    );
    private static final Set<String> SPAWN_BIOMES = Set.of(
        "TemperateForest", "SunlitPlains", "BirchForest", "FlowerForest", "Meadow", "Savanna", "Taiga"
    );

    private DefaultWorldFactory() {
    }

    public static MaterialProvider<BlockState> createDefaultMaterialProvider() {
        return new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.GRASS_BLOCK.defaultBlockState(), 1),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.DIRT.defaultBlockState(), 3),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            45.0f,
            3
        );
    }

    public static WorldStructure createDefaultWorldStructure(long seed, int cellSize) {
        return createDefaultWorldStructure(seed, cellSize, "default");
    }

    public static WorldStructure createDefaultWorldStructure(long seed, int cellSize, String layoutPreset) {
        int s = (int) (seed ^ (seed >>> 32));

        // =====================================================================
        // 0: Temperate Forest — Rolling green hills, soaring arches, lush canopy
        // =====================================================================
        DensityFunction forestBase = new VerticalGradientDensity(72.0f, 26.0f);
        DensityFunction forestHills = new Noise2dDensity(
            s + 101, FastNoiseLite.NoiseType.OpenSimplex2, 0.0045f, 3, 0.50f
        );
        DensityFunction forest3DRaw = new YSampledDensity(
            new Noise3dDensity(s + 102, FastNoiseLite.NoiseType.OpenSimplex2, 0.011f, 3, 0.45f),
            8
        );
        // CurveMapper ensures smooth Hermite sigmoid transitions across overhang thresholds
        DensityFunction forest3D = new CurveMapperDensity(
            forest3DRaw,
            new DistanceSCurve(1.0, 1.0, 1.0, 0.5, 0.25)
        );
        DensityFunction forestWarpSource = new Noise2dDensity(
            s + 103, FastNoiseLite.NoiseType.OpenSimplex2, 0.008f, 2, 1.0f
        );
        DensityFunction forestTerrain = new GradientWarpDensity(
            new SumDensity(forestBase, forestHills, forest3D),
            forestWarpSource,
            14.0f
        );

        // Multi-tier QueueMaterialProvider with stratified slope cliff rules
        MaterialProvider<BlockState> forestFloor = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.GRASS_BLOCK.defaultBlockState(), 1),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.DIRT.defaultBlockState(), 3),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            45.0f,
            3
        );
        MaterialProvider<BlockState> forestCliffs = new WeightedMaterialProvider<>(
            List.of(
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.STONE.defaultBlockState(), 65.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.ANDESITE.defaultBlockState(), 25.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.GRAVEL.defaultBlockState(), 10.0f)
            ),
            seed ^ 104L
        );
        MaterialProvider<BlockState> forestMaterials = new QueueMaterialProvider<>(
            List.of(
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(0.0f, 45.0f),
                        new SpaceCheckPattern<>(3)
                    )),
                    forestFloor
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(45.0f, 90.0f)
                    )),
                    forestCliffs
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new CeilingPattern<>(),
                    new ConstantMaterialProvider<>(Blocks.STONE.defaultBlockState())
                )
            ),
            new ConstantMaterialProvider<>(Blocks.STONE.defaultBlockState())
        );

        List<Prop<BlockState>> forestProps = List.of(
            createFloraProp(
                Blocks.SHORT_GRASS.defaultBlockState(),
                Blocks.FERN.defaultBlockState(),
                Blocks.DANDELION.defaultBlockState(),
                Blocks.POPPY.defaultBlockState(),
                0.14f
            ),
            createTreeProp(
                Blocks.OAK_LOG.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(),
                5,
                2,
                0.035f
            )
        );

        TileBiome temperateForest = new TileBiome(
            BIOME_TEMPERATE_FOREST,
            "TemperateForest",
            18.0f,
            1.4f,
            88.0f,
            forestTerrain,
            forestMaterials,
            forestProps,
            new ClimateTarget(0.10f, 0.10f, 0.35f)
        );

        // =====================================================================
        // 4: Sunlit Plains — Open grassy meadow & wildflower ecotone buffer
        //    Separates Arid Dunes from dense Temperate Forest so trees thin out naturally
        // =====================================================================
        DensityFunction plainsBase = new VerticalGradientDensity(71.0f, 22.0f);
        DensityFunction plainsRolling = new Noise2dDensity(
            s + 151, FastNoiseLite.NoiseType.OpenSimplex2, 0.0038f, 2, 0.30f
        );
        DensityFunction plains3D = new YSampledDensity(
            new Noise3dDensity(s + 152, FastNoiseLite.NoiseType.OpenSimplex2, 0.010f, 2, 0.22f),
            8
        );
        DensityFunction plainsTerrain = new SumDensity(plainsBase, plainsRolling, plains3D);

        List<Prop<BlockState>> plainsProps = List.of(createFloraProp(
            Blocks.SHORT_GRASS.defaultBlockState(),
            Blocks.SHORT_GRASS.defaultBlockState(),
            Blocks.DANDELION.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(),
            0.16f
        ));

        TileBiome sunlitPlains = new TileBiome(
            BIOME_SUNLIT_PLAINS,
            "SunlitPlains",
            16.0f,
            1.35f,
            88.0f,
            plainsTerrain,
            forestMaterials,
            plainsProps,
            new ClimateTarget(0.05f, 0.20f, -0.30f)
        );

        // =====================================================================
        // 1: Arid Dunes — Sweeping desert dunes & wind-sculpted sandstone canyon bluffs
        // =====================================================================
        DensityFunction dunesBase = new VerticalGradientDensity(79.0f, 26.0f);
        FastNoiseLite duneNoise = new FastNoiseLite(s + 201);
        duneNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        duneNoise.setFrequency(0.0045f);
        duneNoise.setFractalType(FastNoiseLite.FractalType.Ridged);
        duneNoise.setFractalOctaves(3);
        DensityFunction dunesRidges = new Noise2dDensity(duneNoise, 0.40f);
        DensityFunction dunesArches3D = new CurveMapperDensity(
            new YSampledDensity(
                new Noise3dDensity(s + 202, FastNoiseLite.NoiseType.OpenSimplex2, 0.010f, 3, 0.32f),
                8
            ),
            new DistanceSCurve(1.0, 1.0, 1.0, 0.5, 0.20)
        );
        DensityFunction dunesTerrain = new SumDensity(dunesBase, dunesRidges, dunesArches3D);

        MaterialProvider<BlockState> dunesFloor = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.SAND.defaultBlockState(), 4),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.SANDSTONE.defaultBlockState(), 8),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.SANDSTONE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            52.0f,
            2
        );
        MaterialProvider<BlockState> dunesCanyons = new WeightedMaterialProvider<>(
            List.of(
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 55.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.TERRACOTTA.defaultBlockState(), 30.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 15.0f)
            ),
            seed ^ 203L
        );
        MaterialProvider<BlockState> dunesMaterials = new QueueMaterialProvider<>(
            List.of(
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(0.0f, 52.0f),
                        new SpaceCheckPattern<>(2)
                    )),
                    dunesFloor
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(52.0f, 90.0f)
                    )),
                    dunesCanyons
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new CeilingPattern<>(),
                    new ConstantMaterialProvider<>(Blocks.SANDSTONE.defaultBlockState())
                )
            ),
            new ConstantMaterialProvider<>(Blocks.SANDSTONE.defaultBlockState())
        );

        List<Prop<BlockState>> dunesProps = List.of(createDesertProp());

        TileBiome aridDunes = new TileBiome(
            BIOME_ARID_DUNES,
            "AridDunes",
            11.0f,
            1.45f,
            92.0f,
            dunesTerrain,
            dunesMaterials,
            dunesProps,
            new ClimateTarget(0.10f, 0.90f, -0.85f)
        );

        // =====================================================================
        // 2: Alpine Peaks — Razor-sharp mountain spines & elevation-driven snowline
        // =====================================================================
        DensityFunction alpineBase = new VerticalGradientDensity(96.0f, 46.0f);
        FastNoiseLite ridgeNoise = new FastNoiseLite(s + 301);
        ridgeNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        ridgeNoise.setFrequency(0.004f);
        ridgeNoise.setFractalType(FastNoiseLite.FractalType.Ridged);
        ridgeNoise.setFractalOctaves(4);
        // Exponential curve sharpens ridge spines into knife-edge aretes
        DensityFunction alpineSpines = new CurveMapperDensity(
            new Noise2dDensity(ridgeNoise, 0.75f),
            new DistanceExponentialCurve(1.8, 1.0)
        );
        DensityFunction alpine3D = new YSampledDensity(
            new Noise3dDensity(s + 302, FastNoiseLite.NoiseType.OpenSimplex2, 0.013f, 3, 0.58f),
            6
        );
        DensityFunction alpineTerrain = new SumDensity(alpineBase, alpineSpines, alpine3D);

        MaterialProvider<BlockState> alpineMaterials = createAlpineElevationMaterialProvider(s + 305, forestMaterials);

        TileBiome alpinePeaks = new TileBiome(
            BIOME_ALPINE_PEAKS,
            "AlpinePeaks",
            12.0f,
            1.5f,
            96.0f,
            alpineTerrain,
            alpineMaterials,
            List.of(),
            new ClimateTarget(0.80f, -0.45f, 0.10f)
        );

        // =====================================================================
        // 3: Old Growth Grove (CustomBiome Overlay over Temperate Forest)
        // =====================================================================
        DensityFunction grovePlacementNoise = new Noise2dDensity(
            s + 401, FastNoiseLite.NoiseType.OpenSimplex2, 0.003f, 2, 1.0f
        );
        DensityFunction groveBase = new VerticalGradientDensity(74.0f, 30.0f);
        DensityFunction grove3D = new CurveMapperDensity(
            new YSampledDensity(
                new Noise3dDensity(s + 402, FastNoiseLite.NoiseType.OpenSimplex2, 0.013f, 3, 0.56f),
                8
            ),
            new DistanceSCurve(1.0, 1.0, 1.0, 0.5, 0.25)
        );
        DensityFunction groveTerrain = new SumDensity(groveBase, forestHills, grove3D);

        MaterialProvider<BlockState> groveFloor = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.PODZOL.defaultBlockState(), 2),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.COARSE_DIRT.defaultBlockState(), 4),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
            42.0f,
            4
        );
        MaterialProvider<BlockState> groveCliffs = new WeightedMaterialProvider<>(
            List.of(
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 45.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.STONE.defaultBlockState(), 35.0f),
                WeightedMaterialProvider.Entry.ofMaterial(Blocks.ANDESITE.defaultBlockState(), 20.0f)
            ),
            seed ^ 403L
        );
        MaterialProvider<BlockState> groveMaterials = new QueueMaterialProvider<>(
            List.of(
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(0.0f, 42.0f),
                        new SpaceCheckPattern<>(3)
                    )),
                    groveFloor
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new AndPattern<>(List.of(
                        new SurfacePattern<>(),
                        new SlopePattern<>(42.0f, 90.0f)
                    )),
                    groveCliffs
                ),
                new QueueMaterialProvider.ConditionalRule<>(
                    new CeilingPattern<>(),
                    new ConstantMaterialProvider<>(Blocks.STONE.defaultBlockState())
                )
            ),
            new ConstantMaterialProvider<>(Blocks.STONE.defaultBlockState())
        );

        List<Prop<BlockState>> groveProps = List.of(
            createFloraProp(
                Blocks.FERN.defaultBlockState(),
                Blocks.RED_MUSHROOM.defaultBlockState(),
                Blocks.BROWN_MUSHROOM.defaultBlockState(),
                Blocks.SHORT_GRASS.defaultBlockState(),
                0.16f
            ),
            createTreeProp(
                Blocks.DARK_OAK_LOG.defaultBlockState(),
                Blocks.DARK_OAK_LEAVES.defaultBlockState(),
                8,
                3,
                0.030f
            ),
            createFallenLogProp(
                Blocks.OAK_LOG.defaultBlockState(),
                0.015f
            )
        );

        CustomBiome oldGrowthGrove = new CustomBiome(
            BIOME_OLD_GROWTH_GROVE,
            "OldGrowthGrove",
            10,
            Set.of(BIOME_TEMPERATE_FOREST, DefaultBiomeCatalog.TAIGA),
            0.30f,
            1.0f,
            0.16f,
            grovePlacementNoise,
            groveTerrain,
            groveMaterials,
            groveProps
        );

        // Biome selection is driven by a 3-axis macro climate (continentalness, temperature, humidity):
        // each tile biome declares a ClimateTarget and cells pick the nearest target, so oceans, coasts,
        // lowlands, forests, deserts, tundra and mountain interiors form coherent continents.
        List<TileBiome> tileBiomes = new ArrayList<>(List.of(temperateForest, sunlitPlains, aridDunes, alpinePeaks));
        tileBiomes.addAll(DefaultBiomeFactory.createSurfaceTileBiomes(s));

        WorldStructure structure = new WorldStructure(
            tileBiomes,
            List.of(oldGrowthGrove, DefaultBiomeFactory.createIceSpikes(s)),
            cellSize,
            seed
        );

        // Global terrain modifiers (applied after biome blending so they are continuous across borders)
        structure
            .withRiverCarver(new RiverCarver(seed, SEA_LEVEL, 6))
            .withRiverMaterialProvider(DefaultBiomeFactory.createRiverMaterialProvider())
            .withCaveCarver(new CaveCarver(seed))
            .withUndergroundBiomes(DefaultBiomeFactory.createUndergroundBiomes(), seed);

        // Configure Macro Dimension Layout & Outside-Bounds Fallback based on preset
        String normPreset = layoutPreset != null ? layoutPreset.toLowerCase(java.util.Locale.ROOT) : "default";
        if (normPreset.contains("end")) {
            structure.withDimensionLayout(com.worldgenv2.worldstructure.DimensionLayoutConfig.endDimensionLayout(seed));
        } else if (normPreset.contains("island") || normPreset.contains("spawn")) {
            structure.withDimensionLayout(com.worldgenv2.worldstructure.DimensionLayoutConfig.spawnIslandLayout(
                220.0f,
                520.0f,
                640.0f,
                com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback.DEFAULT_BIOMES,
                "TemperateForest",
                seed
            ));
        } else if (normPreset.contains("skyblock") || normPreset.contains("void")) {
            structure.withDimensionLayout(com.worldgenv2.worldstructure.DimensionLayoutConfig.spawnIslandLayout(
                180.0f,
                320.0f,
                380.0f,
                com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback.VOID,
                "TemperateForest",
                seed
            ));
        } else {
            structure.withDimensionLayout(com.worldgenv2.worldstructure.DimensionLayoutConfig.overworldDefault(seed));
        }


        ConditionalSpawnProvider.LandmassType landmass = (normPreset.contains("island") || normPreset.contains("skyblock"))
            ? ConditionalSpawnProvider.LandmassType.ISLAND
            : ConditionalSpawnProvider.LandmassType.CONTINENT;

        ConditionalSpawnProvider spawnProvider = new ConditionalSpawnProvider(
            landmass,
            0.0f,
            0.0f,
            220.0f,
            140.0f,
            SPAWN_BIOMES,
            64,
            110,
            ConditionalSpawnProvider.FallbackStrategy.FORCE_GENERATION
        );

        return structure
            .withConditionalSpawn(spawnProvider);
    }

    private static MaterialProvider<BlockState> createAlpineElevationMaterialProvider(
        int seed,
        MaterialProvider<BlockState> foothillProvider
    ) {
        FastNoiseLite snowlineNoise = new FastNoiseLite(seed);
        snowlineNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        snowlineNoise.setFrequency(0.018f);

        MaterialProvider<BlockState> subAlpineProvider = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.PODZOL.defaultBlockState(), 1),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.COARSE_DIRT.defaultBlockState(), 3),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            42.0f,
            3
        );

        MaterialProvider<BlockState> highAlpineSnowProvider = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.SNOW_BLOCK.defaultBlockState(), 2),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.COARSE_DIRT.defaultBlockState(), 3),
                new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState(),
            38.0f,
            3
        );

        return (x, y, z, depth, space, slopeAngle, voxelSpace) -> {
            float effectiveY = y + snowlineNoise.getNoise(x, z) * 6.0f;
            if (effectiveY >= 96.0f) {
                return highAlpineSnowProvider.getMaterial(x, y, z, depth, space, slopeAngle, voxelSpace);
            }
            if (effectiveY >= 86.0f) {
                return subAlpineProvider.getMaterial(x, y, z, depth, space, slopeAngle, voxelSpace);
            }
            return foothillProvider.getMaterial(x, y, z, depth, space, slopeAngle, voxelSpace);
        };
    }

    static Prop<BlockState> createFloraProp(
        BlockState primary,
        BlockState secondary,
        BlockState flowerA,
        BlockState flowerB,
        float densityChance
    ) {
        return ctx -> {
            if (ctx.normalY() < 0.78f || ctx.worldY() <= 63) {
                return false;
            }
            BlockState below = ctx.voxelSpace().getBlock(ctx.worldX(), ctx.worldY() - 1, ctx.worldZ());
            if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.PODZOL) && !below.is(Blocks.DIRT)) {
                return false;
            }
            float roll = ((ctx.seed() >>> 8) & 0xFFFF) / 65535.0f;
            if (roll > densityChance) {
                return false;
            }
            int variant = (int) ((ctx.seed() >>> 24) & 15);
            BlockState chosen = switch (variant) {
                case 0 -> flowerA;
                case 1 -> flowerB;
                case 2, 3, 4, 5 -> secondary;
                default -> primary;
            };
            ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY(), ctx.worldZ(), chosen);
            return true;
        };
    }

    private static Prop<BlockState> createTreeProp(
        BlockState log,
        BlockState leaves,
        int trunkHeight,
        int canopyRadius,
        float densityChance
    ) {
        return ctx -> {
            if (ctx.normalY() < 0.85f || ctx.worldY() <= 63) {
                return false;
            }
            BlockState below = ctx.voxelSpace().getBlock(ctx.worldX(), ctx.worldY() - 1, ctx.worldZ());
            if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.PODZOL)) {
                return false;
            }
            float roll = ((ctx.seed() >>> 12) & 0xFFFF) / 65535.0f;
            if (roll > densityChance) {
                return false;
            }
            // Vertical trunk
            for (int dy = 0; dy < trunkHeight; dy++) {
                ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY() + dy, ctx.worldZ(), log);
            }
            // Procedural canopy
            int leafBase = ctx.worldY() + trunkHeight - 2;
            int leafTop = ctx.worldY() + trunkHeight + 1;
            for (int ly = leafBase; ly <= leafTop; ly++) {
                int r = (ly == leafTop) ? 1 : canopyRadius;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx == 0 && dz == 0 && ly < ctx.worldY() + trunkHeight) {
                            continue;
                        }
                        if (Math.abs(dx) == r && Math.abs(dz) == r && (ly == leafTop || ly == leafBase)) {
                            continue;
                        }
                        ctx.voxelSpace().setBlock(ctx.worldX() + dx, ly, ctx.worldZ() + dz, leaves);
                    }
                }
            }
            return true;
        };
    }

    private static Prop<BlockState> createFallenLogProp(
        BlockState log,
        float densityChance
    ) {
        return ctx -> {
            if (ctx.normalY() < 0.88f || ctx.worldY() <= 63) {
                return false;
            }
            BlockState below = ctx.voxelSpace().getBlock(ctx.worldX(), ctx.worldY() - 1, ctx.worldZ());
            if (!below.is(Blocks.PODZOL) && !below.is(Blocks.COARSE_DIRT)) {
                return false;
            }
            float roll = ((ctx.seed() >>> 14) & 0xFFFF) / 65535.0f;
            if (roll > densityChance) {
                return false;
            }
            int axis = (int) ((ctx.seed() >>> 28) & 1);
            for (int d = -2; d <= 2; d++) {
                int lx = (axis == 0) ? ctx.worldX() + d : ctx.worldX();
                int lz = (axis == 1) ? ctx.worldZ() + d : ctx.worldZ();
                ctx.voxelSpace().setBlock(lx, ctx.worldY(), lz, log);
            }
            return true;
        };
    }

    private static Prop<BlockState> createDesertProp() {
        BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();
        BlockState cactus = Blocks.CACTUS.defaultBlockState();
        return ctx -> {
            if (ctx.normalY() < 0.85f || ctx.worldY() <= 63) {
                return false;
            }
            BlockState below = ctx.voxelSpace().getBlock(ctx.worldX(), ctx.worldY() - 1, ctx.worldZ());
            if (!below.is(Blocks.SAND)) {
                return false;
            }
            float roll = ((ctx.seed() >>> 8) & 0xFFFF) / 65535.0f;
            if (roll > 0.025f) {
                return false;
            }
            if (roll < 0.007f) {
                int height = 2 + (int) ((ctx.seed() >>> 24) % 2);
                for (int dy = 0; dy < height; dy++) {
                    ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY() + dy, ctx.worldZ(), cactus);
                }
                return true;
            } else {
                ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY(), ctx.worldZ(), deadBush);
                return true;
            }
        };
    }
}
