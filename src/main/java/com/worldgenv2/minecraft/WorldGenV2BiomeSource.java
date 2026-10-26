package com.worldgenv2.minecraft;

import com.worldgenv2.worldstructure.UndergroundBiome;
import com.worldgenv2.worldstructure.WorldStructure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

/**
 * Minecraft 26.1 {@link BiomeSource} backed by Worldgen V2's {@link WorldStructure} (BiCarta Voronoi + CustomBiome overlays).
 * Evaluates cellular Voronoi biomes and smooth noise overlays directly inside {@link #getNoiseBiome(int, int, int, Climate.Sampler)}.
 */
public final class WorldGenV2BiomeSource extends BiomeSource {

    public static final MapCodec<WorldGenV2BiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            Codec.LONG.optionalFieldOf("seed", 1337L).forGetter(WorldGenV2BiomeSource::getSeed),
            Codec.INT.optionalFieldOf("cell_size", DefaultWorldFactory.DEFAULT_VORONOI_CELL_SIZE).forGetter(WorldGenV2BiomeSource::getCellSize),
            Codec.STRING.optionalFieldOf("layout_preset", "default").forGetter(WorldGenV2BiomeSource::getLayoutPreset)
        ).apply(instance, instance.stable(WorldGenV2BiomeSource::new))
    );

    private final HolderGetter<Biome> biomeLookup;
    private final Holder<Biome>[] holdersById;
    private final List<Holder<Biome>> distinctHolders;

    private final int cellSize;
    private final String layoutPreset;
    private volatile long seed;
    private volatile WorldStructure worldStructure;

    public WorldGenV2BiomeSource(HolderGetter<Biome> biomeLookup, long seed, int cellSize) {
        this(biomeLookup, seed, cellSize, "default");
    }

    public WorldGenV2BiomeSource(HolderGetter<Biome> biomeLookup, long seed, int cellSize, String layoutPreset) {
        this.biomeLookup = biomeLookup;
        this.cellSize = Math.max(32, cellSize);
        this.layoutPreset = layoutPreset != null ? layoutPreset : "default";
        this.seed = seed;

        @SuppressWarnings("unchecked")
        Holder<Biome>[] byId = (Holder<Biome>[]) new Holder[DefaultBiomeCatalog.BIOME_COUNT];
        java.util.LinkedHashSet<Holder<Biome>> distinct = new java.util.LinkedHashSet<>();
        for (DefaultBiomeCatalog.Entry entry : DefaultBiomeCatalog.entries()) {
            Holder<Biome> holder = biomeLookup.getOrThrow(entry.vanilla());
            byId[entry.id()] = holder;
            distinct.add(holder);
        }
        this.holdersById = byId;
        this.distinctHolders = List.copyOf(distinct);

        this.worldStructure = DefaultWorldFactory.createDefaultWorldStructure(seed, this.cellSize, this.layoutPreset);
    }

    /**
     * Synchronizes the WorldStructure seed with the active Minecraft server level seed.
     */
    public synchronized void ensureWorldSeed(long levelSeed) {
        if (this.seed != levelSeed || this.worldStructure == null) {
            this.seed = levelSeed;
            this.worldStructure = DefaultWorldFactory.createDefaultWorldStructure(levelSeed, this.cellSize, this.layoutPreset);
        }
    }

    /**
     * Rebuilds the underlying WorldStructure (used by {@code /worldgenv2 reload}).
     */
    public synchronized void reload() {
        this.worldStructure = DefaultWorldFactory.createDefaultWorldStructure(this.seed, this.cellSize, this.layoutPreset);
    }

    public String getLayoutPreset() {
        return this.layoutPreset;
    }

    public WorldStructure getWorldStructure() {
        return this.worldStructure;
    }

    public long getSeed() {
        return this.seed;
    }

    public int getCellSize() {
        return this.cellSize;
    }

    public HolderGetter<Biome> getBiomeLookup() {
        return this.biomeLookup;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private static final int RIVER_FLAG = 0x100;

    /** Direct-mapped per-thread cache of resolved surface columns (vanilla queries every quart-Y of a column). */
    private static final class ColumnCache {
        final WorldStructure[] owner = new WorldStructure[512];
        final long[] keys = new long[512];
        final int[] values = new int[512];
    }

    private static final ThreadLocal<ColumnCache> COLUMN_CACHE = ThreadLocal.withInitial(ColumnCache::new);

    private static int surfaceColumn(WorldStructure structure, int quartX, int quartZ, int blockX, int blockZ) {
        ColumnCache cache = COLUMN_CACHE.get();
        long key = ((long) quartX << 32) ^ (quartZ & 0xFFFFFFFFL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 55);
        if (cache.owner[slot] == structure && cache.keys[slot] == key) {
            return cache.values[slot];
        }
        int biomeId = structure.getBiomeId(blockX, blockZ);
        int packed = biomeId & 0xFF;
        if (DefaultBiomeCatalog.allowsRiver(biomeId) && structure.isRiverChannel(blockX, blockZ)) {
            packed |= RIVER_FLAG;
        }
        cache.owner[slot] = structure;
        cache.keys[slot] = key;
        cache.values[slot] = packed;
        return packed;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.distinctHolders.stream();
    }

    private Holder<Biome> holder(int id) {
        Holder<Biome> h = id >= 0 && id < holdersById.length ? holdersById[id] : null;
        return h != null ? h : holdersById[DefaultBiomeCatalog.TEMPERATE_FOREST];
    }

    /**
     * Resolves biomes in true 3D: underground volumes (Lush / Dripstone / Deep Dark / ...) first, then river
     * channels, then the cellular surface biome. Called per 4x4x4 quart cell.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(quartX);
        int blockY = QuartPos.toBlock(quartY);
        int blockZ = QuartPos.toBlock(quartZ);
        WorldStructure structure = this.worldStructure;

        if (blockY <= structure.undergroundBiomeMaxY()) {
            UndergroundBiome underground = structure.getUndergroundBiome(blockX, blockY, blockZ);
            if (underground != null) {
                return holder(underground.id());
            }
        }

        int packed = surfaceColumn(structure, quartX, quartZ, blockX, blockZ);
        int biomeId = packed & 0xFF;
        if ((packed & RIVER_FLAG) != 0) {
            return holder(DefaultBiomeCatalog.isCold(biomeId) ? DefaultBiomeCatalog.FROZEN_RIVER : DefaultBiomeCatalog.RIVER);
        }
        if (biomeId == DefaultBiomeCatalog.ALPINE_PEAKS) {
            // Only apply freezing Snowy Taiga biome at high alpine elevations (Y >= 94);
            // lower mountain foothills use non-freezing conifer forest so snow never forms polygon borders in valleys
            return (blockY >= 94) ? holder(DefaultBiomeCatalog.ALPINE_PEAKS) : holder(DefaultBiomeCatalog.OLD_GROWTH_GROVE);
        }
        return holder(biomeId);
    }
    @Override
    public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
        WorldStructure.BiomeQueryResult query = this.worldStructure.queryBiome(feetPos.getX(), feetPos.getZ());
        String customInfo = query.customBiome() != null
            ? String.format(" | Custom: %s (%.2f)", query.customBiome().name(), query.customBlendWeight())
            : "";
        result.add(String.format(
            "[Worldgen V2 BiCarta] Primary: %s | Secondary: %s | EdgeDist: %.1f | Blend: %.2f%s",
            query.primaryTileBiome().name(),
            query.secondaryTileBiome().name(),
            query.distanceToEdge(),
            query.tileBlendFactor(),
            customInfo
        ));
    }
}
