package com.hypixel.hytale.builtin.hytalegenerator.compat.hytale;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Bidirectional registry mapping Hytale block identifiers to Minecraft block states.
 * Part of the Hytale Compatibility Addon module.
 */
public final class BlockMappingRegistry {

    private final Map<String, String> hytaleToMinecraft = new HashMap<>();
    private final Map<String, String> minecraftToHytale = new HashMap<>();
    private String defaultFallback = "minecraft:stone";

    public BlockMappingRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Natural Stone & Minerals
        register("hytale:stone", "minecraft:stone");
        register("hytale:stone_cobble", "minecraft:cobblestone");
        register("hytale:stone_bricks", "minecraft:stone_bricks");
        register("hytale:stone_mossy", "minecraft:mossy_cobblestone");
        register("hytale:stone_andesite", "minecraft:andesite");
        register("hytale:stone_granite", "minecraft:granite");
        register("hytale:stone_diorite", "minecraft:diorite");
        register("hytale:slate_deep", "minecraft:deepslate");
        register("hytale:bedrock", "minecraft:bedrock");

        // Surface Soils & Ground Cover
        register("hytale:grass_block", "minecraft:grass_block");
        register("hytale:dirt", "minecraft:dirt");
        register("hytale:dirt_coarse", "minecraft:coarse_dirt");
        register("hytale:podzol", "minecraft:podzol");
        register("hytale:gravel", "minecraft:gravel");
        register("hytale:sand", "minecraft:sand");
        register("hytale:sand_red", "minecraft:red_sand");
        register("hytale:sandstone", "minecraft:sandstone");

        // Wood, Timber & Prefab Architectural Elements
        register("hytale:wood_oak_log", "minecraft:oak_log");
        register("hytale:wood_oak_planks", "minecraft:oak_planks");
        register("hytale:wood_spruce_log", "minecraft:spruce_log");
        register("hytale:wood_spruce_planks", "minecraft:spruce_planks");
        register("hytale:leaves_oak", "minecraft:oak_leaves");
        register("hytale:leaves_spruce", "minecraft:spruce_leaves");
        register("hytale:thatch_roof_stairs", "minecraft:oak_stairs");
        register("hytale:torch_wall", "minecraft:wall_torch");
        register("hytale:plant_grass_tall", "minecraft:tall_grass");

        // Snow & Ice
        register("hytale:snow_block", "minecraft:snow_block");
        register("hytale:snow_cover", "minecraft:snow");
        register("hytale:ice", "minecraft:ice");
        register("hytale:ice_packed", "minecraft:packed_ice");

        // Fluids
        register("hytale:fluid_water", "minecraft:water");
        register("hytale:fluid_lava", "minecraft:lava");
        register("hytale:empty", "minecraft:air");
    }

    public void register(String hytaleBlock, String minecraftBlock) {
        hytaleToMinecraft.put(hytaleBlock, minecraftBlock);
        minecraftToHytale.put(minecraftBlock, hytaleBlock);
    }

    public String toMinecraft(String hytaleBlock) {
        if (hytaleBlock == null) return defaultFallback;
        if (hytaleBlock.startsWith("minecraft:")) return hytaleBlock;
        String mapped = hytaleToMinecraft.get(hytaleBlock);
        if (mapped != null) return mapped;

        if (hytaleBlock.contains("slate")) return "minecraft:deepslate";
        if (hytaleBlock.contains("stone") || hytaleBlock.contains("rock")) return "minecraft:stone";
        if (hytaleBlock.contains("wood") || hytaleBlock.contains("log") || hytaleBlock.contains("timber")) return "minecraft:oak_log";
        if (hytaleBlock.contains("plank")) return "minecraft:oak_planks";
        if (hytaleBlock.contains("stair")) return "minecraft:oak_stairs";
        if (hytaleBlock.contains("leaf") || hytaleBlock.contains("leaves") || hytaleBlock.contains("foliage")) return "minecraft:oak_leaves";
        if (hytaleBlock.contains("sand")) return "minecraft:sand";

        return defaultFallback;
    }

    public String toMinecraftBlockState(String hytaleBlock, Map<String, String> properties) {
        String base = toMinecraft(hytaleBlock);
        if (properties == null || properties.isEmpty()) {
            return base;
        }
        StringJoiner joiner = new StringJoiner(",", base + "[", "]");
        properties.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> joiner.add(e.getKey() + "=" + e.getValue()));
        return joiner.toString();
    }

    public String toHytale(String minecraftBlock) {
        return minecraftToHytale.getOrDefault(minecraftBlock, "hytale:stone");
    }

    public void setDefaultFallback(String fallback) {
        this.defaultFallback = fallback;
    }
}
