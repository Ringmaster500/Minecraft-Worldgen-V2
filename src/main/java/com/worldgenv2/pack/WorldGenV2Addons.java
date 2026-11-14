package com.worldgenv2.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.worldgenv2.worldstructure.ZoneLayout;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Public extension surface for companion mods ("addons") of Worldgen V2.
 *
 * <h3>What an addon can add</h3>
 * <ul>
 *   <li><b>Presets</b> - whole worlds or biome packs players pick when creating a project:
 *       {@link #registerPreset} in code, or ship {@code worldgenv2_presets/index.json} (a JSON array of file names)
 *       plus the bundle files next to it. Bundles are exactly what Studio's "Export as preset" writes.</li>
 *   <li><b>Node types</b> (density, curve, prop, pattern, scanner, ...) - register a deserializer in
 *       {@code WorldGenV2Registries.*_TYPE}; ship {@code worldgenv2_node_schemas.json} (same format as the built-in
 *       {@code assets/worldgenv2/node_schema.json}) so the Studio graph palette and inspector know about it.</li>
 *   <li><b>Zone mask kinds</b> - {@link ZoneLayout#registerMaskKind}.</li>
 *   <li><b>Compile hooks</b> - {@link #registerCompileHook} runs after a dimension is compiled (preview and game),
 *       letting an addon add placements, ore rules, etc. to the {@code WorldStructure}.</li>
 * </ul>
 * All of it is plain data plus these registries; addons never patch the generator.
 */
public final class WorldGenV2Addons {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param id          namespaced id, e.g. {@code mypack:wasteland}
     * @param kind        {@code world} (a complete project) or {@code pack} (biomes/prefabs to add to a project)
     * @param bundle      supplies {@code {"assets": {"biomes/x.json": {...}, ...}}}
     */
    public record Preset(String id, String name, String description, String kind, Supplier<JsonObject> bundle) {
        public boolean isPack() { return "pack".equals(kind); }
    }

    /** Runs after each compile of a dimension. Must be fast and must not throw for normal input. */
    public interface CompileHook {
        void afterCompile(com.worldgenv2.studio.project.StudioProject project, ProjectCompiler.Result result);
    }

    private static final Map<String, Preset> REGISTERED = new LinkedHashMap<>();
    private static final List<CompileHook> HOOKS = new ArrayList<>();
    private static Supplier<JsonArray> defaultOres = com.worldgenv2.studio.project.Templates::defaultOres;
    private static List<Preset> discovered;
    private static boolean builtinsDone;

    private WorldGenV2Addons() {}

    public static synchronized void registerPreset(Preset preset) {
        REGISTERED.put(preset.id(), preset);
    }

    /** Replaces what "Default" ore generation means (e.g. a modpack with its own ores). Returns the veins as ore-rule JSON. */
    public static synchronized void setDefaultOres(Supplier<JsonArray> supplier) {
        defaultOres = supplier;
    }

    /** The ore rules used when a dimension\'s ores are set to Default. */
    public static synchronized JsonArray defaultOres() {
        return defaultOres.get();
    }

    public static synchronized void registerCompileHook(CompileHook hook) {
        HOOKS.add(hook);
    }

    /** Built-in presets first, then code-registered, then those discovered on the classpath. */
    public static synchronized List<Preset> presets() {
        if (!builtinsDone) {
            builtinsDone = true;
            BuiltinPresets.register();
        }
        if (discovered == null) discovered = discover();
        Map<String, Preset> all = new LinkedHashMap<>(REGISTERED);
        for (Preset p : discovered) all.putIfAbsent(p.id(), p);
        return new ArrayList<>(all.values());
    }

    public static synchronized Preset preset(String id) {
        for (Preset p : presets()) if (p.id().equals(id)) return p;
        return null;
    }

    /** Re-scans addon files (e.g. after the player installs a mod without restarting; normally not needed). */
    public static synchronized void refresh() {
        discovered = null;
    }

    static void runCompileHooks(com.worldgenv2.studio.project.StudioProject project, ProjectCompiler.Result result) {
        List<CompileHook> copy;
        synchronized (WorldGenV2Addons.class) { copy = new ArrayList<>(HOOKS); }
        for (CompileHook h : copy) {
            try {
                h.afterCompile(project, result);
            } catch (Throwable t) {
                result.warnings.add("Addon compile hook failed: " + t);
            }
        }
    }

    private static List<Preset> discover() {
        List<Preset> out = new ArrayList<>();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = WorldGenV2Addons.class.getClassLoader();
            Enumeration<URL> indexes = cl.getResources("worldgenv2_presets/index.json");
            while (indexes.hasMoreElements()) {
                URL index = indexes.nextElement();
                try (var r = new InputStreamReader(index.openStream(), StandardCharsets.UTF_8)) {
                    JsonArray names = JsonParser.parseReader(r).getAsJsonArray();
                    for (JsonElement n : names) {
                        URL file = new URL(index, n.getAsString());
                        try (var fr = new InputStreamReader(file.openStream(), StandardCharsets.UTF_8)) {
                            JsonObject bundle = JsonParser.parseReader(fr).getAsJsonObject();
                            String id = bundle.has("id") ? bundle.get("id").getAsString() : n.getAsString().replace(".json", "");
                            String name = bundle.has("name") ? bundle.get("name").getAsString() : id;
                            String desc = bundle.has("description") ? bundle.get("description").getAsString() : "";
                            String kind = bundle.has("kind") ? bundle.get("kind").getAsString() : "world";
                            out.add(new Preset(id, name, desc, kind, () -> bundle));
                        } catch (Exception e) {
                            LOGGER.warn("Could not read preset {} from {}: {}", n, index, e.toString());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not read preset index {}: {}", index, e.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Preset discovery failed: {}", e.toString());
        }
        return out;
    }
}
