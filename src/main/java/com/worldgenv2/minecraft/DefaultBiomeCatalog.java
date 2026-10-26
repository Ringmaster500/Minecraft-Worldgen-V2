package com.worldgenv2.minecraft;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry of every default Worldgen V2 biome id, its display name, its role, and the vanilla
 * Minecraft biome it is presented as (climate colors, mob spawns, vanilla decoration features).
 *
 * <ul>
 *   <li>30 {@link Kind#TILE} cellular surface biomes (land, mountains, oceans)</li>
 *   <li>2 {@link Kind#CUSTOM} overlay biomes</li>
 *   <li>2 {@link Kind#RIVER} channel biomes carved by the {@code RiverCarver}</li>
 *   <li>5 {@link Kind#UNDERGROUND} true-3D biome volumes</li>
 * </ul>
 */
public final class DefaultBiomeCatalog {

    public enum Kind { TILE, CUSTOM, RIVER, UNDERGROUND }

    public record Entry(int id, String name, Kind kind, ResourceKey<Biome> vanilla, boolean land, boolean cold) {}

    // --- Original biomes (ids are stable) ---
    public static final int TEMPERATE_FOREST = 0;
    public static final int ARID_DUNES = 1;
    public static final int ALPINE_PEAKS = 2;
    public static final int OLD_GROWTH_GROVE = 3;
    public static final int SUNLIT_PLAINS = 4;

    // --- Oceans ---
    public static final int DEEP_OCEAN = 5;
    public static final int DEEP_FROZEN_OCEAN = 6;
    public static final int WARM_OCEAN = 7;
    public static final int LUKEWARM_OCEAN = 8;
    public static final int OCEAN = 9;
    public static final int COLD_OCEAN = 10;
    public static final int FROZEN_OCEAN = 11;
    public static final int MUSHROOM_ISLAND = 12;

    // --- Land ---
    public static final int BADLANDS = 13;
    public static final int SAVANNA = 14;
    public static final int JUNGLE = 15;
    public static final int SWAMP = 16;
    public static final int MANGROVE_SWAMP = 17;
    public static final int FLOWER_FOREST = 18;
    public static final int BIRCH_FOREST = 19;
    public static final int DARK_FOREST = 20;
    public static final int MEADOW = 21;
    public static final int CHERRY_GROVE = 22;
    public static final int TAIGA = 23;
    public static final int SNOWY_PLAINS = 24;
    public static final int SNOWY_TAIGA = 25;
    public static final int GROVE = 26;
    public static final int WINDSWEPT_HILLS = 27;
    public static final int STONY_PEAKS = 28;
    public static final int JAGGED_PEAKS = 29;
    public static final int FROZEN_PEAKS = 30;

    // --- Overlays / rivers ---
    public static final int ICE_SPIKES = 31;
    public static final int RIVER = 32;
    public static final int FROZEN_RIVER = 33;

    // --- Underground (3D volumes) ---
    public static final int LUSH_CAVES = 34;
    public static final int DRIPSTONE_CAVES = 35;
    public static final int DEEP_DARK = 36;
    public static final int MUSHROOM_GROTTO = 37;
    public static final int FROZEN_CAVERNS = 38;

    public static final int BIOME_COUNT = 39;

    private static final Entry[] BY_ID = new Entry[BIOME_COUNT];
    private static final List<Entry> ENTRIES;

    private static void reg(int id, String name, Kind kind, ResourceKey<Biome> vanilla, boolean land, boolean cold) {
        BY_ID[id] = new Entry(id, name, kind, vanilla, land, cold);
    }

    static {
        reg(TEMPERATE_FOREST, "TemperateForest", Kind.TILE, Biomes.FOREST, true, false);
        reg(ARID_DUNES, "AridDunes", Kind.TILE, Biomes.DESERT, true, false);
        reg(ALPINE_PEAKS, "AlpinePeaks", Kind.TILE, Biomes.SNOWY_TAIGA, true, true);
        reg(OLD_GROWTH_GROVE, "OldGrowthGrove", Kind.CUSTOM, Biomes.OLD_GROWTH_PINE_TAIGA, true, true);
        reg(SUNLIT_PLAINS, "SunlitPlains", Kind.TILE, Biomes.PLAINS, true, false);

        reg(DEEP_OCEAN, "DeepOcean", Kind.TILE, Biomes.DEEP_OCEAN, false, false);
        reg(DEEP_FROZEN_OCEAN, "DeepFrozenOcean", Kind.TILE, Biomes.DEEP_FROZEN_OCEAN, false, true);
        reg(WARM_OCEAN, "WarmOcean", Kind.TILE, Biomes.WARM_OCEAN, false, false);
        reg(LUKEWARM_OCEAN, "LukewarmOcean", Kind.TILE, Biomes.LUKEWARM_OCEAN, false, false);
        reg(OCEAN, "Ocean", Kind.TILE, Biomes.OCEAN, false, false);
        reg(COLD_OCEAN, "ColdOcean", Kind.TILE, Biomes.COLD_OCEAN, false, true);
        reg(FROZEN_OCEAN, "FrozenOcean", Kind.TILE, Biomes.FROZEN_OCEAN, false, true);
        reg(MUSHROOM_ISLAND, "MushroomIsland", Kind.TILE, Biomes.MUSHROOM_FIELDS, true, false);

        reg(BADLANDS, "Badlands", Kind.TILE, Biomes.BADLANDS, true, false);
        reg(SAVANNA, "Savanna", Kind.TILE, Biomes.SAVANNA, true, false);
        reg(JUNGLE, "Jungle", Kind.TILE, Biomes.JUNGLE, true, false);
        reg(SWAMP, "Swamp", Kind.TILE, Biomes.SWAMP, true, false);
        reg(MANGROVE_SWAMP, "MangroveSwamp", Kind.TILE, Biomes.MANGROVE_SWAMP, true, false);
        reg(FLOWER_FOREST, "FlowerForest", Kind.TILE, Biomes.FLOWER_FOREST, true, false);
        reg(BIRCH_FOREST, "BirchForest", Kind.TILE, Biomes.BIRCH_FOREST, true, false);
        reg(DARK_FOREST, "DarkForest", Kind.TILE, Biomes.DARK_FOREST, true, false);
        reg(MEADOW, "Meadow", Kind.TILE, Biomes.MEADOW, true, false);
        reg(CHERRY_GROVE, "CherryGrove", Kind.TILE, Biomes.CHERRY_GROVE, true, false);
        reg(TAIGA, "Taiga", Kind.TILE, Biomes.TAIGA, true, true);
        reg(SNOWY_PLAINS, "SnowyPlains", Kind.TILE, Biomes.SNOWY_PLAINS, true, true);
        reg(SNOWY_TAIGA, "SnowyTaiga", Kind.TILE, Biomes.SNOWY_TAIGA, true, true);
        reg(GROVE, "Grove", Kind.TILE, Biomes.GROVE, true, true);
        reg(WINDSWEPT_HILLS, "WindsweptHills", Kind.TILE, Biomes.WINDSWEPT_HILLS, true, false);
        reg(STONY_PEAKS, "StonyPeaks", Kind.TILE, Biomes.STONY_PEAKS, true, false);
        reg(JAGGED_PEAKS, "JaggedPeaks", Kind.TILE, Biomes.JAGGED_PEAKS, true, true);
        reg(FROZEN_PEAKS, "FrozenPeaks", Kind.TILE, Biomes.FROZEN_PEAKS, true, true);

        reg(ICE_SPIKES, "IceSpikes", Kind.CUSTOM, Biomes.ICE_SPIKES, true, true);
        reg(RIVER, "River", Kind.RIVER, Biomes.RIVER, false, false);
        reg(FROZEN_RIVER, "FrozenRiver", Kind.RIVER, Biomes.FROZEN_RIVER, false, true);

        reg(LUSH_CAVES, "LushCaves", Kind.UNDERGROUND, Biomes.LUSH_CAVES, false, false);
        reg(DRIPSTONE_CAVES, "DripstoneCaves", Kind.UNDERGROUND, Biomes.DRIPSTONE_CAVES, false, false);
        reg(DEEP_DARK, "DeepDark", Kind.UNDERGROUND, Biomes.DEEP_DARK, false, false);
        reg(MUSHROOM_GROTTO, "MushroomGrotto", Kind.UNDERGROUND, Biomes.MUSHROOM_FIELDS, false, false);
        reg(FROZEN_CAVERNS, "FrozenCaverns", Kind.UNDERGROUND, Biomes.FROZEN_PEAKS, false, true);

        List<Entry> list = new ArrayList<>();
        Collections.addAll(list, BY_ID);
        ENTRIES = Collections.unmodifiableList(list);
    }

    private DefaultBiomeCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry get(int id) {
        return id >= 0 && id < BIOME_COUNT && BY_ID[id] != null ? BY_ID[id] : BY_ID[TEMPERATE_FOREST];
    }

    /**
     * Ids of every cellular tile biome that is land (rivers may cut through, caves may sit beneath).
     */
    public static Set<Integer> landIds() {
        Set<Integer> ids = new HashSet<>();
        for (Entry e : ENTRIES) {
            if (e.kind() == Kind.TILE && e.land()) {
                ids.add(e.id());
            }
        }
        return ids;
    }

    public static Set<Integer> coldLandIds() {
        Set<Integer> ids = new HashSet<>();
        for (Entry e : ENTRIES) {
            if ((e.kind() == Kind.TILE || e.kind() == Kind.CUSTOM) && e.land() && e.cold()) {
                ids.add(e.id());
            }
        }
        return ids;
    }

    /**
     * Land biomes where rivers may cut a channel (excludes oceans, the mushroom island, and mountain interiors).
     */
    public static boolean allowsRiver(int id) {
        Entry e = get(id);
        if (e.kind() != Kind.TILE && e.kind() != Kind.CUSTOM) {
            return false;
        }
        return e.land() && id != MUSHROOM_ISLAND && id != STONY_PEAKS && id != JAGGED_PEAKS
            && id != FROZEN_PEAKS && id != ALPINE_PEAKS;
    }

    public static boolean isCold(int id) {
        return get(id).cold();
    }
}
