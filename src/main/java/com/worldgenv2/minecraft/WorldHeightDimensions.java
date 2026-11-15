package com.worldgenv2.minecraft;

import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.ProjectStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import java.util.HashMap;
import java.util.Map;

/**
 * Gives a new world the height its world setup asks for: the overworld's dimension type is swapped for the registered type of that
 * height (see {@link HeightPack}). Heights are whole chunk sections: Min Y a multiple of 16, depth a multiple of 16 up to 2048.
 */
public final class WorldHeightDimensions {
    private WorldHeightDimensions() {}

    /**
     * The same dimensions with the overworld at this height. If no type for that height is known yet (it was only just written to
     * the heights pack), the dimensions are returned unchanged and the world keeps the standard height; the next Create World
     * screen will have it.
     */
    public static WorldDimensions apply(HolderLookup.Provider provider, WorldDimensions dims, int minY, int depth) {
        int y0 = WorldHeightConfig.alignMinY(minY), h = WorldHeightConfig.alignGenDepth(depth);
        HeightPack.ensure(y0, h);
        Map<net.minecraft.resources.ResourceKey<LevelStem>, LevelStem> map = new HashMap<>(dims.dimensions());
        LevelStem overworld = map.get(LevelStem.OVERWORLD);
        if (overworld == null) return dims;
        var holder = provider.lookupOrThrow(Registries.DIMENSION_TYPE).get(HeightPack.keyFor(y0, h));
        if (holder.isEmpty()) return dims;
        map.put(LevelStem.OVERWORLD, new LevelStem(holder.get(), overworld.generator()));
        return new WorldDimensions(map);
    }

    /** Whether the registered type for this height is available to the given registries. */
    public static boolean known(HolderLookup.Provider provider, int minY, int depth) {
        return provider.lookupOrThrow(Registries.DIMENSION_TYPE)
            .get(HeightPack.keyFor(WorldHeightConfig.alignMinY(minY), WorldHeightConfig.alignGenDepth(depth))).isPresent();
    }

    /** The Min Y and depth of a world setup (from its world.json), or null if it cannot be read. */
    public static int[] heightOf(String projectName) {
        try {
            Asset w = ProjectStore.open(projectName).world();
            JsonObject j = w == null ? null : w.json;
            if (j == null) return null;
            int minY = j.has("minY") ? j.get("minY").getAsInt() : -64;
            int depth = j.has("genDepth") ? j.get("genDepth").getAsInt() : 384;
            return new int[]{minY, depth};
        } catch (Exception e) {
            return null;
        }
    }
}
