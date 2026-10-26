package com.worldgenv2.studio.projects;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages creator workspaces and projects inside config/worldgenv2/projects/.
 * Also provides a designated "Current World" scratchpad mode for live in-game testing.
 */
public final class StudioProjectManager {

    public record ProjectEntry(String name, Path directory, long lastModified, boolean isCurrentWorld) {}

    private static final Path PROJECTS_DIR = Path.of("config", "worldgenv2", "projects");
    private static ProjectEntry activeProject;

    static {
        try {
            Files.createDirectories(PROJECTS_DIR);
        } catch (Exception ignored) {}
    }

    public static Path getProjectsDir() {
        return PROJECTS_DIR;
    }

    public static ProjectEntry getActiveProject() {
        return activeProject;
    }

    public static void setActiveProject(ProjectEntry project) {
        activeProject = project;
    }

    public static ProjectEntry createCurrentWorldProject() {
        Minecraft mc = Minecraft.getInstance();
        String worldName = "Current World";
        if (mc.getSingleplayerServer() != null) {
            worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return new ProjectEntry(worldName, null, System.currentTimeMillis(), true);
    }

    public static List<ProjectEntry> listProjects() {
        List<ProjectEntry> list = new ArrayList<>();
        File dir = PROJECTS_DIR.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles(File::isDirectory);
            if (files != null) {
                for (File f : files) {
                    list.add(new ProjectEntry(f.getName(), f.toPath(), f.lastModified(), false));
                }
            }
        }
        return list;
    }

    public static ProjectEntry createNewProject(String name) {
        if (name == null || name.isBlank()) name = "New_Project";
        String sanitized = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Path projectPath = PROJECTS_DIR.resolve(sanitized);
        try {
            Files.createDirectories(projectPath);
            Files.createDirectories(projectPath.resolve("dimensions"));
            Files.createDirectories(projectPath.resolve("biomes"));
            Files.createDirectories(projectPath.resolve("prefabs"));
        } catch (Exception ignored) {}
        return new ProjectEntry(sanitized, projectPath, System.currentTimeMillis(), false);
    }
}
