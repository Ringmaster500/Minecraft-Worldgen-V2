package com.worldgenv2.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.worldgenv2.pack.ProjectRuntime;
import com.worldgenv2.studio.project.J;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Remembers which world setup each world was made from. The choice is saved in the world's own folder ({@code worldgenv2.json}),
 * so loading a world always generates from the setup it was created with, whatever was picked for the next new world.
 * A world without the file (made before this existed, or just created) takes whatever new worlds start from and saves it.
 */
public final class WorldProjectLink {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "worldgenv2.json";

    private WorldProjectLink() {}

    private static Path file(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve(FILE); }

    /** Writes the link for the open world. */
    public static void save(MinecraftServer server, String project, String preset) {
        try {
            Path f = file(server);
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            if (project != null) o.addProperty("project", project);
            if (preset != null) o.addProperty("preset", preset);
            Files.writeString(f, o.toString());
        } catch (IOException e) {
            LOGGER.warn("[Worldgen V2] Could not save this world's setup link: {}", e.toString());
        }
    }

    /** Points the open world at a setup (from the Studio) and remembers it. */
    public static void link(MinecraftServer server, String project) {
        ProjectRuntime.linkWorld(project, null);
        save(server, project, null);
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        String project = null, preset = null;
        boolean known = false;
        try {
            Path f = file(server);
            if (Files.isRegularFile(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                project = J.str(o, "project", "");
                preset = J.str(o, "preset", "");
                if (project.isBlank()) project = null;
                if (preset.isBlank()) preset = null;
                known = true;
            }
        } catch (Exception e) {
            LOGGER.warn("[Worldgen V2] Could not read this world's setup link: {}", e.toString());
        }
        String builtProject = ProjectRuntime.globalProjectName(), builtPreset = ProjectRuntime.globalPresetId();
        if (!known) {
            // a new world (or an older one): it uses what the generators were just built from, and keeps it from now on
            project = builtProject;
            preset = builtProject == null ? builtPreset : null;
            save(server, project, preset);
        }
        ProjectRuntime.linkWorld(project, preset);
        boolean changed = !Objects.equals(project, builtProject) || (project == null && !Objects.equals(preset, builtPreset));
        if (!changed) return;
        // the generators were built from the global choice before this world was known; rebuild them from the world's own setup
        try {
            for (var stem : server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)) {
                if (stem.generator() instanceof WorldGenV2ChunkGenerator v2) v2.reload();
            }
            LOGGER.info("[Worldgen V2] This world uses its own setup '{}'.", project != null ? project : preset);
        } catch (RuntimeException e) {
            LOGGER.warn("[Worldgen V2] Could not switch to this world's setup: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ProjectRuntime.unlinkWorld();
    }
}
