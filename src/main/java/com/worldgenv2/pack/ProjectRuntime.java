package com.worldgenv2.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.worldgenv2.minecraft.DefaultBiomeCatalog;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.worldstructure.WorldStructure;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the <em>active project</em> at game runtime. A modpack ships a project folder under
 * {@code config/worldgenv2/projects/<name>/} and names it in {@code config/worldgenv2/active.json}
 * ({@code {"project": "name"}}); worlds created with the "Worldgen V2: Project" world type then generate from it.
 * Safe to call on a dedicated server: it touches no client-only classes.
 */
public final class ProjectRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String REF_PREFIX = "project:";

    private ProjectRuntime() {}

    public record Runtime(WorldStructure structure, Holder<Biome>[] holders, List<Holder<Biome>> distinct,
                          ProjectCompiler.Result compiled, String projectName) {}

    public static Path activeFile() { return Path.of("config", "worldgenv2", "active.json"); }

    // The world currently open links to its own setup (saved in the world's folder); while linked it wins over active.json,
    // which only says what NEW worlds start from.
    private static volatile boolean worldLinked;
    private static volatile String worldProject, worldPreset;

    public static void linkWorld(String project, String preset) { worldProject = project; worldPreset = preset; worldLinked = true; }
    public static void unlinkWorld() { worldLinked = false; worldProject = null; worldPreset = null; }
    public static boolean isWorldLinked() { return worldLinked; }

    /** The setup the open world generates from, or the one new worlds will start from when no world is open. */
    public static String activeProjectName() {
        if (worldLinked) return worldProject == null || worldProject.isBlank() ? null : worldProject;
        return globalProjectName();
    }

    /** The setup new worlds start from (active.json), whatever world is open. */
    public static String globalProjectName() {
        try {
            Path f = activeFile();
            if (!Files.exists(f)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            String n = J.str(o, "project", "");
            return n.isBlank() ? null : n;
        } catch (Exception e) {
            LOGGER.warn("Could not read {}: {}", activeFile(), e.toString());
            return null;
        }
    }

    /** {@code {"preset": "namespace:id"}} makes worlds generate straight from an installed preset. */
    public static String activePresetId() {
        if (worldLinked) return worldPreset == null || worldPreset.isBlank() ? null : worldPreset;
        return globalPresetId();
    }

    public static String globalPresetId() {
        try {
            Path f = activeFile();
            if (!Files.exists(f)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            String n = J.str(o, "preset", "");
            return n.isBlank() ? null : n;
        } catch (Exception e) {
            return null;
        }
    }

    public static void setActiveProject(String name) throws IOException {
        Files.createDirectories(activeFile().getParent());
        JsonObject o = new JsonObject();
        o.addProperty("project", name);
        Files.writeString(activeFile(), o.toString());
    }

    /**
     * Compiles the active project's dimension named by {@code ref} ("project:overworld") for a world.
     * Returns null when there is no usable project, so callers fall back to the built-in generator.
     */
    @SuppressWarnings("unchecked")
    public static Runtime build(HolderGetter<Biome> lookup, long worldSeed, String ref) {
        if (ref == null || !ref.startsWith(REF_PREFIX)) return null;
        String dimAssetId = ref.substring(REF_PREFIX.length());
        String name = activeProjectName();
        String presetId = activePresetId();
        boolean useDefault = name == null && presetId == null;
        try {
            StudioProject project;
            if (useDefault) {
                // nothing chosen: the built-in Default, so the mod works with zero configuration
                name = "Default";
                project = StudioProject.fromBundle(name, BuiltinPresets.defaultBundle());
            } else if (presetId != null) {
                // an addon (or built-in) preset used directly, without a project folder
                WorldGenV2Addons.Preset pr = WorldGenV2Addons.preset(presetId);
                if (pr == null) {
                    LOGGER.warn("active.json names preset '{}', which is not installed; using built-in generator.", presetId);
                    return null;
                }
                name = pr.name();
                project = StudioProject.fromBundle(name, pr.bundle().get());
            } else {
                project = StudioProject.load(name, Path.of("config", "worldgenv2", "projects", name));
            }
            Asset dim = project.get("dimensions/" + dimAssetId + ".json");
            if (dim == null) {
                LOGGER.warn("Project '{}' has no dimension '{}'; using built-in generator.", name, dimAssetId);
                return null;
            }
            boolean fixed = "fixed".equals(J.str(dim.json, "seedMode", "world"));
            ProjectCompiler.Result res = ProjectCompiler.compile(project, dimAssetId, fixed ? Long.MIN_VALUE : worldSeed);
            for (String w : res.warnings) LOGGER.warn("[project {}] {}", name, w);
            if (res.structure == null) return null;

            Holder<Biome>[] holders = (Holder<Biome>[]) new Holder[256];
            Holder<Biome> fallback = resolve(lookup, "minecraft:plains", null);
            Set<Holder<Biome>> distinct = new LinkedHashSet<>();
            // built-in catalog ids stay valid for rivers / underground biomes / anything the project does not override
            for (DefaultBiomeCatalog.Entry e : DefaultBiomeCatalog.entries()) {
                Holder<Biome> h = lookup.getOrThrow(e.vanilla());
                holders[e.id()] = h;
            }
            for (ProjectCompiler.BiomeBinding b : res.biomes) {
                Holder<Biome> h = resolve(lookup, b.minecraftBiome(), fallback);
                if (h == null) h = fallback;
                holders[b.id()] = h;
                if (h != null) distinct.add(h);
            }
            for (int i = 0; i < holders.length; i++) {
                if (holders[i] == null) holders[i] = fallback != null ? fallback : holders[DefaultBiomeCatalog.TEMPERATE_FOREST];
                distinct.add(holders[i]);
            }
            return new Runtime(res.structure, holders, new ArrayList<>(distinct), res, name);
        } catch (Throwable t) {
            LOGGER.error("Failed to build project world '{}': {}", ref, t.toString());
            return null;
        }
    }

    private static Holder<Biome> resolve(HolderGetter<Biome> lookup, String id, Holder<Biome> fallback) {
        try {
            Identifier ident = Identifier.tryParse(id);
            if (ident == null) return fallback;
            Optional<? extends Holder<Biome>> h = lookup.get(ResourceKey.create(Registries.BIOME, ident));
            if (h.isPresent()) return h.get();
            LOGGER.warn("Minecraft biome '{}' is not registered (is the mod installed?); using fallback.", id);
        } catch (Throwable ignored) {}
        return fallback;
    }
}
