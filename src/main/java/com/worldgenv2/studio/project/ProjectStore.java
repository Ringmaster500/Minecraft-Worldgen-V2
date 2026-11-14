package com.worldgenv2.studio.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Locates, creates and opens projects under {@code config/worldgenv2/projects/}. */
public final class ProjectStore {
    private ProjectStore() {}

    public record Entry(String name, Path dir, long modified) {}

    public static Path root() {
        return Path.of("config", "worldgenv2", "projects");
    }

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        try {
            Files.createDirectories(root());
            try (Stream<Path> s = Files.list(root())) {
                s.filter(Files::isDirectory).forEach(d -> {
                    long m = 0;
                    try { m = Files.getLastModifiedTime(d).toMillis(); } catch (IOException ignored) {}
                    out.add(new Entry(d.getFileName().toString(), d, m));
                });
            }
        } catch (IOException ignored) {}
        out.sort(Comparator.comparingLong(Entry::modified).reversed());
        return out;
    }

    public static String checkNewName(String raw) {
        String id = StudioProject.sanitize(raw);
        if (id.isEmpty()) return "Enter a name (letters, numbers, underscores).";
        if (Files.exists(root().resolve(id))) return "A project named '" + id + "' already exists.";
        return null;
    }

    /** Creates a populated starter project and returns it opened. */
    public static StudioProject create(String rawName) throws IOException {
        return create(rawName, "worldgenv2:starter");
    }

    /** Creates a project from a preset (built-in or from an addon). */
    public static StudioProject create(String rawName, String presetId) throws IOException {
        String id = StudioProject.sanitize(rawName);
        StudioProject p = StudioProject.load(id, root().resolve(id));
        com.worldgenv2.pack.WorldGenV2Addons.Preset preset = com.worldgenv2.pack.WorldGenV2Addons.preset(presetId);
        if (preset != null) p.importBundle(preset.bundle().get(), false, false);
        else Templates.populateStarter(p);
        p.validateAll();
        p.saveAll();
        return p;
    }

    public static StudioProject open(String name) throws IOException {
        return StudioProject.load(name, root().resolve(name));
    }
}
