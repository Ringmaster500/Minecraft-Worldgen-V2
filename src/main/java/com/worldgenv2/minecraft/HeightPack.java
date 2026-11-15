package com.worldgenv2.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Dimension types for the heights world setups ask for. A level's height comes from its dimension type, which the server sends to
 * the client by registry id, so each height has to exist as a registered type. Types are written as small data-pack files
 * ({@code config/worldgenv2/heights}), one per height in use, and the folder is offered to Minecraft as an always-on data pack.
 * They are generated at start-up for every world setup and again whenever a setup's height is chosen; a height that is new in a
 * session is picked up the next time the Create World screen opens.
 */
public final class HeightPack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "worldgenv2";
    private static final String PACK_ID = "worldgenv2_heights";

    private HeightPack() {}

    public static Path dir() { return Path.of("config", "worldgenv2", "heights"); }

    /** The type's name: lower case letters, digits and underscores only ("m64" for -64). */
    public static String nameFor(int minY, int depth) {
        return "h_" + (minY < 0 ? "m" + (-minY) : String.valueOf(minY)) + "_" + depth;
    }

    public static ResourceKey<DimensionType> keyFor(int minY, int depth) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(NAMESPACE, nameFor(minY, depth)));
    }

    /** Whether the type is already on disk (it may still need a reload of the pack list to be seen by a running game). */
    public static boolean exists(int minY, int depth) {
        return Files.exists(typeFile(minY, depth));
    }

    private static Path typeFile(int minY, int depth) {
        return dir().resolve("data").resolve(NAMESPACE).resolve("dimension_type").resolve(nameFor(minY, depth) + ".json");
    }

    /** Writes the type for this height (the overworld's, with this Min Y and depth). Returns true if the file is new. */
    public static boolean ensure(int minY, int depth) {
        int y0 = WorldHeightConfig.alignMinY(minY), h = WorldHeightConfig.alignGenDepth(depth);
        try {
            Path file = typeFile(y0, h);
            if (Files.exists(file)) return false;
            Files.createDirectories(file.getParent());
            Path mcmeta = dir().resolve("pack.mcmeta");
            if (!Files.exists(mcmeta)) {
                Files.writeString(mcmeta, "{\"pack\":{\"description\":\"Worldgen V2 world heights\",\"min_format\":[101,1],\"max_format\":101}}", StandardCharsets.UTF_8);
            }
            JsonObject o;
            try (InputStream in = HeightPack.class.getResourceAsStream("/data/worldgenv2/dimension_type/overworld_hytale.json")) {
                if (in == null) throw new IOException("the overworld template is missing from the mod");
                o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            o.addProperty("min_y", y0);
            o.addProperty("height", h);
            o.addProperty("logical_height", h);
            // clouds sit just under the ceiling, like the vanilla overworld's at 192 of 384 above its floor
            JsonObject attrs = o.has("attributes") ? o.getAsJsonObject("attributes") : null;
            if (attrs != null && attrs.has("minecraft:visual/cloud_height")) attrs.addProperty("minecraft:visual/cloud_height", (float) (y0 + h * 0.6));
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
            LOGGER.info("[Worldgen V2] Wrote the dimension type for height {} to {} ({})", y0, y0 + h, file);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Worldgen V2] Could not write the dimension type for height {} / {}: {}", minY, depth, e.toString());
            return false;
        }
    }

    /** One type per world setup, so a world made from any of them finds its height. */
    public static void ensureAll() {
        try {
            for (var p : com.worldgenv2.studio.project.ProjectStore.list()) {
                int[] h = WorldHeightDimensions.heightOf(p.name());
                if (h != null) ensure(h[0], h[1]);
            }
        } catch (Throwable t) {
            LOGGER.warn("[Worldgen V2] Could not prepare the world heights: {}", t.toString());
        }
    }

    /** Offers the folder to Minecraft as an always-on data pack. */
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        event.addRepositorySource(consumer -> {
            try {
                Files.createDirectories(dir().resolve("data"));
                if (!Files.exists(dir().resolve("pack.mcmeta"))) ensure(-64, 384);
                PackLocationInfo info = new PackLocationInfo(PACK_ID, Component.literal("Worldgen V2 world heights"), PackSource.BUILT_IN, Optional.empty());
                Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir()), PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) consumer.accept(pack);
            } catch (Exception e) {
                LOGGER.warn("[Worldgen V2] Could not offer the world heights pack: {}", e.toString());
            }
        });
    }
}
