package com.worldgenv2.minecraft;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.density.FastNoiseLite;
import com.worldgenv2.density.GradientWarpDensity;
import com.worldgenv2.density.Noise2dDensity;
import com.worldgenv2.density.Noise3dDensity;
import com.worldgenv2.density.SumDensity;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.density.YSampledDensity;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.material.SpaceAndDepthMaterialProvider;
import com.worldgenv2.material.SpaceAndDepthMaterialProvider.Layer;
import com.worldgenv2.props.Prop;
import com.worldgenv2.worldstructure.ClimateTarget;
import com.worldgenv2.worldstructure.CustomBiome;
import com.worldgenv2.worldstructure.TileBiome;
import com.worldgenv2.worldstructure.UndergroundBiome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Data-driven factory for the expanded default biome set: 26 additional surface tile biomes
 * (oceans, wetlands, forests, highlands, peaks), the Ice Spikes overlay, and the five
 * underground 3D biome volumes. Each tile biome is declared as a compact {@link Spec}
 * (terrain shape + strata + flora + macro climate target) and compiled into a density DAG,
 * a stratified material provider, and flora props.
 */
public final class DefaultBiomeFactory {

    public record WrappedMaterialProvider<T>(MaterialProvider<T> inner, MaterialProvider<T> evaluator) implements MaterialProvider<T> {
        @Override
        public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, com.worldgenv2.api.VoxelSpace<T> spaceRef) {
            return evaluator.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
        }
    }

    private static final int SEA_LEVEL = 63;

    private DefaultBiomeFactory() {
    }

    /** Compact flora description (single-block plants scattered over soil). */
    private record Flora(Block primary, Block secondary, Block flowerA, Block flowerB, float chance) {}

    /**
     * Declarative description of a tile biome.
     *
     * @param snowLine elevation above which a snow cap covers gentle slopes (0 = none)
     * @param shoreSand paint sand across the sea-level shoreline band (beaches) when true
     */
    private record Spec(
        int id,
        float weight,
        float size,
        float transition,
        float baseHeight,
        float heightScale,
        float hillFreq,
        float hillAmp,
        float ridgeAmp,
        float d3Amp,
        float warpAmp,
        Block cliff,
        float snowLine,
        boolean shoreSand,
        List<Layer<BlockState>> layers,
        Flora flora,
        ClimateTarget climate
    ) {}

    private static Layer<BlockState> L(Block block, int thickness) {
        return new Layer<>(block.defaultBlockState(), thickness);
    }

    @SafeVarargs
    private static List<Layer<BlockState>> layers(Layer<BlockState>... layers) {
        return List.of(layers);
    }

    private static Flora flora(Block a, Block b, Block fa, Block fb, float chance) {
        return new Flora(a, b, fa, fb, chance);
    }

    private static ClimateTarget climate(float c, float t, float h) {
        return new ClimateTarget(c, t, h);
    }

    private static List<Spec> specs() {
        List<Spec> s = new ArrayList<>();
        Block STONE = Blocks.STONE;

        // ---------------- Oceans & islands ----------------
        s.add(new Spec(DefaultBiomeCatalog.DEEP_OCEAN, 10, 1.8f, 110, 31, 18, 0.004f, 0.35f, 0, 0.05f, 0, STONE, 0, false,
            layers(L(Blocks.GRAVEL, 4), L(STONE, 999)), null, climate(-0.90f, 0.00f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.DEEP_FROZEN_OCEAN, 10, 1.8f, 110, 31, 18, 0.004f, 0.35f, 0, 0.05f, 0, STONE, 0, false,
            layers(L(Blocks.GRAVEL, 3), L(STONE, 999)), null, climate(-0.90f, -0.80f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.WARM_OCEAN, 10, 1.6f, 100, 47, 16, 0.005f, 0.30f, 0, 0.04f, 0, STONE, 0, false,
            layers(L(Blocks.SAND, 4), L(STONE, 999)), null, climate(-0.50f, 0.80f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.LUKEWARM_OCEAN, 10, 1.6f, 100, 44, 16, 0.005f, 0.30f, 0, 0.04f, 0, STONE, 0, false,
            layers(L(Blocks.SAND, 3), L(Blocks.GRAVEL, 1), L(STONE, 999)), null, climate(-0.50f, 0.35f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.OCEAN, 12, 1.6f, 100, 42, 16, 0.005f, 0.30f, 0, 0.04f, 0, STONE, 0, false,
            layers(L(Blocks.SAND, 2), L(Blocks.GRAVEL, 2), L(STONE, 999)), null, climate(-0.50f, -0.10f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.COLD_OCEAN, 10, 1.6f, 100, 40, 16, 0.005f, 0.30f, 0, 0.04f, 0, STONE, 0, false,
            layers(L(Blocks.GRAVEL, 3), L(STONE, 999)), null, climate(-0.50f, -0.50f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.FROZEN_OCEAN, 10, 1.6f, 100, 40, 16, 0.005f, 0.30f, 0, 0.04f, 0, STONE, 0, false,
            layers(L(Blocks.GRAVEL, 2), L(STONE, 999)), null, climate(-0.50f, -0.85f, 0.0f)));
        s.add(new Spec(DefaultBiomeCatalog.MUSHROOM_ISLAND, 3, 0.9f, 70, 67, 14, 0.006f, 0.55f, 0, 0.12f, 0, STONE, 0, true,
            layers(L(Blocks.MYCELIUM, 1), L(Blocks.DIRT, 3), L(STONE, 999)), null, climate(-0.70f, 0.10f, 0.95f)));

        // ---------------- Hot & arid ----------------
        s.add(new Spec(DefaultBiomeCatalog.BADLANDS, 10, 1.2f, 70, 76, 24, 0.005f, 0.55f, 0.30f, 0.30f, 10, Blocks.TERRACOTTA, 0, false,
            layers(L(Blocks.RED_SAND, 1), L(Blocks.ORANGE_TERRACOTTA, 2), L(Blocks.TERRACOTTA, 1), L(Blocks.YELLOW_TERRACOTTA, 1),
                L(Blocks.RED_TERRACOTTA, 1), L(Blocks.TERRACOTTA, 2), L(Blocks.BROWN_TERRACOTTA, 1), L(Blocks.ORANGE_TERRACOTTA, 2),
                L(STONE, 999)),
            null, climate(0.30f, 0.85f, -0.60f)));
        s.add(new Spec(DefaultBiomeCatalog.SAVANNA, 10, 1.4f, 80, 73, 24, 0.004f, 0.35f, 0, 0.20f, 6, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.ORANGE_TULIP, 0.10f),
            climate(0.10f, 0.70f, -0.20f)));

        // ---------------- Wet & tropical ----------------
        s.add(new Spec(DefaultBiomeCatalog.JUNGLE, 10, 1.4f, 80, 74, 24, 0.0055f, 0.55f, 0, 0.30f, 8, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.BLUE_ORCHID, Blocks.ALLIUM, 0.22f),
            climate(0.10f, 0.80f, 0.80f)));
        s.add(new Spec(DefaultBiomeCatalog.SWAMP, 10, 1.4f, 70, 62, 20, 0.006f, 0.12f, 0, 0.08f, 6, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.BLUE_ORCHID, Blocks.DANDELION, 0.10f),
            climate(0.00f, 0.40f, 0.90f)));
        s.add(new Spec(DefaultBiomeCatalog.MANGROVE_SWAMP, 8, 1.3f, 70, 62, 20, 0.006f, 0.10f, 0, 0.06f, 6, STONE, 0, false,
            layers(L(Blocks.MUD, 4), L(STONE, 999)), null, climate(-0.05f, 0.70f, 0.98f)));

        // ---------------- Temperate woods & meadows ----------------
        s.add(new Spec(DefaultBiomeCatalog.FLOWER_FOREST, 8, 1.2f, 80, 73, 26, 0.0045f, 0.50f, 0, 0.45f, 12, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.ALLIUM, Blocks.AZURE_BLUET, 0.30f),
            climate(0.10f, 0.25f, 0.50f)));
        s.add(new Spec(DefaultBiomeCatalog.BIRCH_FOREST, 10, 1.3f, 80, 72, 26, 0.0045f, 0.45f, 0, 0.40f, 12, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.LILY_OF_THE_VALLEY, 0.12f),
            climate(0.12f, 0.00f, 0.15f)));
        s.add(new Spec(DefaultBiomeCatalog.DARK_FOREST, 10, 1.3f, 80, 71, 26, 0.0048f, 0.50f, 0, 0.50f, 12, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, 0.12f),
            climate(0.10f, 0.15f, 0.85f)));
        s.add(new Spec(DefaultBiomeCatalog.MEADOW, 9, 1.3f, 90, 82, 28, 0.0040f, 0.45f, 0, 0.15f, 8, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, 0.22f),
            climate(0.45f, 0.00f, 0.00f)));
        s.add(new Spec(DefaultBiomeCatalog.CHERRY_GROVE, 8, 1.2f, 90, 85, 28, 0.0045f, 0.50f, 0, 0.20f, 8, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.PINK_PETALS, Blocks.PINK_TULIP, Blocks.ALLIUM, 0.20f),
            climate(0.50f, 0.10f, 0.30f)));

        // ---------------- Cold ----------------
        s.add(new Spec(DefaultBiomeCatalog.TAIGA, 10, 1.4f, 80, 74, 26, 0.0045f, 0.50f, 0, 0.35f, 10, STONE, 0, false,
            layers(L(Blocks.PODZOL, 1), L(Blocks.DIRT, 3), L(STONE, 999)),
            flora(Blocks.FERN, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.SHORT_GRASS, 0.15f),
            climate(0.10f, -0.35f, 0.40f)));
        s.add(new Spec(DefaultBiomeCatalog.SNOWY_PLAINS, 10, 1.4f, 80, 71, 22, 0.0038f, 0.30f, 0, 0.22f, 0, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)), null, climate(0.05f, -0.75f, -0.25f)));
        s.add(new Spec(DefaultBiomeCatalog.SNOWY_TAIGA, 10, 1.3f, 80, 76, 26, 0.0045f, 0.50f, 0, 0.35f, 8, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)), null, climate(0.15f, -0.60f, 0.35f)));
        s.add(new Spec(DefaultBiomeCatalog.GROVE, 8, 1.2f, 90, 90, 30, 0.0045f, 0.50f, 0.20f, 0.35f, 8, STONE, 0, false,
            layers(L(Blocks.SNOW_BLOCK, 1), L(Blocks.DIRT, 3), L(STONE, 999)), null, climate(0.55f, -0.50f, 0.20f)));

        // ---------------- Highlands & peaks ----------------
        s.add(new Spec(DefaultBiomeCatalog.WINDSWEPT_HILLS, 9, 1.3f, 90, 88, 30, 0.0060f, 0.60f, 0.55f, 0.55f, 14, STONE, 0, false,
            layers(L(Blocks.GRASS_BLOCK, 1), L(Blocks.DIRT, 2), L(STONE, 999)),
            flora(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.FERN, 0.06f),
            climate(0.65f, -0.05f, 0.00f)));
        s.add(new Spec(DefaultBiomeCatalog.STONY_PEAKS, 8, 1.4f, 100, 100, 40, 0.0040f, 0.45f, 0.85f, 0.40f, 0, Blocks.ANDESITE, 0, false,
            layers(L(STONE, 999)), null, climate(0.92f, 0.50f, -0.20f)));
        s.add(new Spec(DefaultBiomeCatalog.JAGGED_PEAKS, 8, 1.4f, 100, 104, 42, 0.0040f, 0.45f, 1.00f, 0.45f, 0, STONE, 108, false,
            layers(L(STONE, 999)), null, climate(0.92f, -0.20f, 0.00f)));
        s.add(new Spec(DefaultBiomeCatalog.FROZEN_PEAKS, 8, 1.4f, 100, 100, 40, 0.0040f, 0.45f, 0.85f, 0.40f, 0, Blocks.PACKED_ICE, 0, false,
            layers(L(Blocks.SNOW_BLOCK, 2), L(Blocks.PACKED_ICE, 2), L(STONE, 999)), null, climate(0.92f, -0.75f, 0.20f)));
        return s;
    }

    /**
     * Builds the 26 additional surface tile biomes (the original four tile biomes are declared in
     * {@link DefaultWorldFactory}).
     */
    public static List<TileBiome> createSurfaceTileBiomes(int s) {
        List<TileBiome> out = new ArrayList<>();
        for (Spec spec : specs()) {
            out.add(buildTile(s, spec));
        }
        return out;
    }

    private static TileBiome buildTile(int s, Spec sp) {
        int salt = sp.id() * 97;
        List<DensityFunction> parts = new ArrayList<>();
        parts.add(new VerticalGradientDensity(sp.baseHeight(), sp.heightScale()));
        if (sp.hillAmp() > 0.0f) {
            parts.add(new Noise2dDensity(s + 1000 + salt, FastNoiseLite.NoiseType.OpenSimplex2, sp.hillFreq(), 3, sp.hillAmp()));
        }
        if (sp.ridgeAmp() > 0.0f) {
            FastNoiseLite ridge = new FastNoiseLite(s + 2000 + salt);
            ridge.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
            ridge.setFrequency(sp.hillFreq() * 0.9f);
            ridge.setFractalType(FastNoiseLite.FractalType.Ridged);
            ridge.setFractalOctaves(4);
            parts.add(new Noise2dDensity(ridge, sp.ridgeAmp()));
        }
        if (sp.d3Amp() > 0.0f) {
            parts.add(new YSampledDensity(
                new Noise3dDensity(s + 3000 + salt, FastNoiseLite.NoiseType.OpenSimplex2, 0.012f, 3, sp.d3Amp()),
                8
            ));
        }
        DensityFunction terrain = new SumDensity(parts);
        if (sp.warpAmp() > 0.0f) {
            DensityFunction warp = new Noise2dDensity(
                s + 4000 + salt, FastNoiseLite.NoiseType.OpenSimplex2, 0.008f, 2, 1.0f
            );
            terrain = new GradientWarpDensity(terrain, warp, sp.warpAmp());
        }

        MaterialProvider<BlockState> materials = buildMaterials(s + 5000 + salt, sp);
        List<Prop<BlockState>> props = sp.flora() == null
            ? List.of()
            : List.of(DefaultWorldFactory.createFloraProp(
                sp.flora().primary().defaultBlockState(),
                sp.flora().secondary().defaultBlockState(),
                sp.flora().flowerA().defaultBlockState(),
                sp.flora().flowerB().defaultBlockState(),
                sp.flora().chance()
            ));

        return new TileBiome(
            sp.id(),
            DefaultBiomeCatalog.get(sp.id()).name(),
            sp.weight(),
            sp.size(),
            sp.transition(),
            terrain,
            materials,
            props,
            sp.climate()
        );
    }

    private static MaterialProvider<BlockState> buildMaterials(int seed, Spec sp) {
        MaterialProvider<BlockState> base = new SpaceAndDepthMaterialProvider<>(
            sp.layers(),
            Blocks.STONE.defaultBlockState(),
            sp.cliff().defaultBlockState(),
            45.0f,
            3
        );
        if (sp.snowLine() > 0.0f) {
            base = snowCap(seed, base, sp.snowLine());
        }
        if (sp.shoreSand()) {
            base = shoreSand(base);
        }
        return deepslateStrata(base);
    }

    /**
     * Dedicated riverbed sediment material provider: produces gravel, sand, and clay on submerged
     * riverbeds (Y <= 63), and lush grass/soil on gentle riparian banks, never bare gray stone.
     */
    public static MaterialProvider<BlockState> createRiverMaterialProvider() {
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState clay = Blocks.CLAY.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();

        return (x, y, z, depth, space, slope, voxel) -> {
            if (y <= SEA_LEVEL) {
                // Submerged riverbed: 50% gravel, 25% sand, 25% clay
                if (depth == 0) {
                    long h = (x * 73856093L) ^ (z * 83492791L);
                    int variant = (int) ((h >>> 32) & 7);
                    if (variant <= 3) return gravel;
                    if (variant <= 5) return sand;
                    return clay;
                }
                if (depth < 4) {
                    return gravel;
                }
                return stone;
            }
            // Bank above water (y >= 64): lush green grass and soil, not stone cliffs
            if (depth == 0) {
                return (slope < 34.0f) ? grass : dirt;
            }
            if (depth < 4) {
                return dirt;
            }
            return stone;
        };
    }

    /**
     * Surface sand across the sea-level shoreline band, producing beaches wherever land meets water.
     */
    public static MaterialProvider<BlockState> shoreSand(MaterialProvider<BlockState> inner) {
        BlockState sand = Blocks.SAND.defaultBlockState();
        return new WrappedMaterialProvider<>(inner, (x, y, z, depth, space, slope, voxel) -> {
            BlockState m = inner.getMaterial(x, y, z, depth, space, slope, voxel);
            if (depth < 3 && y >= SEA_LEVEL - 3 && y <= SEA_LEVEL + 1 && slope < 32.0f
                && (m.is(Blocks.GRASS_BLOCK) || m.is(Blocks.DIRT) || m.is(Blocks.PODZOL) || m.is(Blocks.COARSE_DIRT))) {
                return sand;
            }
            return m;
        });
    }

    private static MaterialProvider<BlockState> snowCap(int seed, MaterialProvider<BlockState> inner, float snowLine) {
        FastNoiseLite n = new FastNoiseLite(seed);
        n.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        n.setFrequency(0.02f);
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        return new WrappedMaterialProvider<>(inner, (x, y, z, depth, space, slope, voxel) -> {
            if (depth < 2 && slope < 60.0f && y + n.getNoise(x, z) * 6.0f >= snowLine) {
                return snow;
            }
            return inner.getMaterial(x, y, z, depth, space, slope, voxel);
        });
    }

    /**
     * Converts bulk stone into deepslate below Y=0 with a noisy 8-block transition band.
     */
    public static MaterialProvider<BlockState> deepslateStrata(MaterialProvider<BlockState> inner) {
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        return new WrappedMaterialProvider<>(inner, (x, y, z, depth, space, slope, voxel) -> {
            BlockState m = inner.getMaterial(x, y, z, depth, space, slope, voxel);
            if (y < 4 && m.is(Blocks.STONE)) {
                if (y <= -4) {
                    return deepslate;
                }
                long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
                h *= 0x9E3779B97F4A7C15L;
                if (((h >>> 40) & 0xFF) < (4 - y) * 32) {
                    return deepslate;
                }
            }
            return m;
        });
    }

    /**
     * Ice Spikes overlay (parent: Snowy Plains).
     */
    static CustomBiome createIceSpikes(int s) {
        DensityFunction placement = new Noise2dDensity(
            s + 6401, FastNoiseLite.NoiseType.OpenSimplex2, 0.0032f, 2, 1.0f
        );
        FastNoiseLite spikes = new FastNoiseLite(s + 6402);
        spikes.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        spikes.setFrequency(0.03f);
        spikes.setFractalType(FastNoiseLite.FractalType.Ridged);
        spikes.setFractalOctaves(2);
        DensityFunction terrain = new SumDensity(
            new VerticalGradientDensity(72.0f, 22.0f),
            new Noise2dDensity(s + 6403, FastNoiseLite.NoiseType.OpenSimplex2, 0.004f, 2, 0.25f),
            new Noise2dDensity(spikes, 1.1f)
        );
        MaterialProvider<BlockState> materials = deepslateStrata(new SpaceAndDepthMaterialProvider<>(
            List.of(
                new Layer<>(Blocks.SNOW_BLOCK.defaultBlockState(), 2),
                new Layer<>(Blocks.PACKED_ICE.defaultBlockState(), 4),
                new Layer<>(Blocks.STONE.defaultBlockState(), 999)
            ),
            Blocks.STONE.defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState(),
            50.0f,
            3
        ));
        return new CustomBiome(
            DefaultBiomeCatalog.ICE_SPIKES,
            "IceSpikes",
            12,
            Set.of(DefaultBiomeCatalog.SNOWY_PLAINS),
            0.55f,
            1.0f,
            0.12f,
            placement,
            terrain,
            materials,
            List.of()
        );
    }

    /**
     * The five underground 3D biome volumes. Lush and Dripstone caves require a land surface above,
     * Frozen Caverns require a cold land surface, Deep Dark lives beneath everything.
     */
    public static List<UndergroundBiome> createUndergroundBiomes() {
        Set<Integer> land = DefaultBiomeCatalog.landIds();
        Set<Integer> humidLand = Set.of(
            DefaultBiomeCatalog.TEMPERATE_FOREST, DefaultBiomeCatalog.FLOWER_FOREST, DefaultBiomeCatalog.BIRCH_FOREST,
            DefaultBiomeCatalog.DARK_FOREST, DefaultBiomeCatalog.JUNGLE, DefaultBiomeCatalog.SWAMP,
            DefaultBiomeCatalog.MANGROVE_SWAMP, DefaultBiomeCatalog.CHERRY_GROVE, DefaultBiomeCatalog.MEADOW,
            DefaultBiomeCatalog.SUNLIT_PLAINS, DefaultBiomeCatalog.SAVANNA
        );
        Set<Integer> coldLand = DefaultBiomeCatalog.coldLandIds();

        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState dripstone = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        BlockState sculk = Blocks.SCULK.defaultBlockState();
        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState packedIce = Blocks.PACKED_ICE.defaultBlockState();

        return List.of(
            new UndergroundBiome(DefaultBiomeCatalog.LUSH_CAVES, "LushCaves", -24, 50, 3.0f, humidLand, moss, 0.60f, moss, 0.25f),
            new UndergroundBiome(DefaultBiomeCatalog.DRIPSTONE_CAVES, "DripstoneCaves", -30, 50, 3.0f, land, dripstone, 0.50f, dripstone, 0.50f),
            new UndergroundBiome(DefaultBiomeCatalog.DEEP_DARK, "DeepDark", -60, -26, 2.0f, Set.of(), sculk, 0.55f, sculk, 0.35f),
            new UndergroundBiome(DefaultBiomeCatalog.MUSHROOM_GROTTO, "MushroomGrotto", -40, 18, 1.5f, Set.of(), mycelium, 0.80f, null, 0.0f),
            new UndergroundBiome(DefaultBiomeCatalog.FROZEN_CAVERNS, "FrozenCaverns", -10, 50, 2.0f, coldLand, snow, 0.50f, packedIce, 0.50f)
        );
    }
}
