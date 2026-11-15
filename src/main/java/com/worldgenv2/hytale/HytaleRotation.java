package com.worldgenv2.hytale;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hytale block rotations as Minecraft block state properties. A prefab block's {@code rotation} is a RotationTuple index
 * ({@code roll*16 + pitch*4 + yaw}, each a count of quarter turns). The images of the block's local axes under that rotation decide
 * the Minecraft shape: log axis, stair facing and half, slab half, wall-attached plants.
 *
 * <p>Convention (verified against Hytale's own RotationTuple): a vector is rolled, then pitched, then yawed. Roll maps +X to +Y,
 * pitch maps +Y to +Z, yaw maps +X to -Z. World axes follow Minecraft: +X east, +Z south, -Z north.
 */
public final class HytaleRotation {
    private HytaleRotation() {}

    /** Image of a local vector under rotation index {@code idx}. */
    public static int[] image(int idx, int x, int y, int z) {
        int yaw = idx & 3, pitch = (idx >> 2) & 3, roll = (idx >> 4) & 3;
        int[] v = {x, y, z};
        for (int i = 0; i < roll; i++) v = new int[]{-v[1], v[0], v[2]};
        for (int i = 0; i < pitch; i++) v = new int[]{v[0], -v[2], v[1]};
        for (int i = 0; i < yaw; i++) v = new int[]{v[2], v[1], -v[0]};
        return v;
    }

    private static String horizontal(int[] v) {
        if (v[0] > 0) return "east";
        if (v[0] < 0) return "west";
        if (v[2] > 0) return "south";
        if (v[2] < 0) return "north";
        return null;
    }

    private static final Set<String> SHAPE_BASES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak",
            "stone", "cobblestone", "mossy_cobblestone", "stone_brick", "mossy_stone_brick", "sandstone", "smooth_sandstone", "red_sandstone",
            "smooth_red_sandstone", "cut_sandstone", "cut_red_sandstone", "brick", "nether_brick", "red_nether_brick", "quartz", "smooth_quartz", "purpur",
            "prismarine", "prismarine_brick", "dark_prismarine", "granite", "andesite", "diorite", "polished_granite", "polished_andesite", "polished_diorite",
            "blackstone", "polished_blackstone", "polished_blackstone_brick", "cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile",
            "tuff", "polished_tuff", "tuff_brick", "mud_brick", "resin_brick", "end_stone_brick", "copper", "cut_copper");

    /** The stair or slab block for a full block id ({@code minecraft:stone_bricks} gives {@code minecraft:stone_brick_stairs}), or null. */
    public static String shaped(String mc, String suffix) {
        if (mc == null || !mc.startsWith("minecraft:")) return null;
        String b = mc.substring(10);
        String[] cands = {b, b.endsWith("s") ? b.substring(0, b.length() - 1) : b, b.replace("_planks", ""), b.replace("_block", ""),
                b.replace("_planks", "").replace("_log", "")};
        for (String c : cands) if (SHAPE_BASES.contains(c)) return "minecraft:" + c + suffix;
        return null;
    }

    /** Minecraft block state properties for a block rotated by {@code idx}; empty when the rotation changes nothing representable. */
    public static Map<String, String> properties(String mcId, int idx) {
        Map<String, String> p = new LinkedHashMap<>();
        if (mcId == null) return p;
        String id = mcId.startsWith("minecraft:") ? mcId.substring(10) : mcId;
        int[] up = image(idx, 0, 1, 0);
        if (id.endsWith("_stairs")) {
            int[] high = image(idx, 0, 0, 1);
            String f = horizontal(high);
            if (f == null) f = horizontal(image(idx, 1, 0, 0));
            if (f != null) p.put("facing", f);
            if (up[1] < 0) p.put("half", "top");
        } else if (id.endsWith("_slab")) {
            if (up[1] < 0) p.put("type", "top");
        } else if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || id.endsWith("_pillar")
                || id.equals("bone_block") || id.equals("hay_block") || id.equals("basalt") || id.equals("polished_basalt") || id.equals("chain")
                || id.equals("quartz_pillar") || id.equals("purpur_pillar") || id.equals("bamboo_block") || id.equals("stripped_bamboo_block")) {
            if (up[0] != 0) p.put("axis", "x");
            else if (up[2] != 0) p.put("axis", "z");
        } else if (id.equals("pointed_dripstone")) {
            // the block hangs at rotation 0; turned upside down it grows up from the floor
            p.put("vertical_direction", up[1] < 0 ? "up" : "down");
        } else if (id.equals("vine")) {
            int[] attach = image(idx, 0, 0, -1);
            if (attach[1] > 0) p.put("up", "true");
            else { String f = horizontal(attach); if (f != null) p.put(f, "true"); }
        }
        return p;
    }
}
