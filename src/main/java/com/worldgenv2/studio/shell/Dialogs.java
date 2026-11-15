package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.ProjectStore;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.ui.Modal;
import com.worldgenv2.studio.ui.PopupMenu;
import com.worldgenv2.studio.ui.UiRoot;

import java.util.ArrayList;
import java.util.List;

/** All the "ask the user, then do it" flows in one place so every entry point behaves identically. */
final class Dialogs {
    private Dialogs() {}

    /**
     * New biome: start blank, copy one from a preset or addon, or import one of Hytale's own biomes (read from the player's
     * installed Hytale assets; the copy goes into this world setup only).
     */
    static void newBiome(UiRoot ui, StudioApp app) {
        List<ListPicker.Item> items = new ArrayList<>();
        items.add(new ListPicker.Item("New", "Blank biome", "start from an empty biome", () -> newAssetPrompt(ui, app, AssetType.BIOME)));
        for (var p : com.worldgenv2.pack.WorldGenV2Addons.presets()) {
            try {
                var assets = com.worldgenv2.studio.project.J.obj(p.bundle().get(), "assets");
                if (assets == null) continue;
                for (var e : assets.entrySet()) {
                    if (!e.getKey().startsWith("biomes/") || !e.getValue().isJsonObject()) continue;
                    String id = e.getKey().substring(7, e.getKey().length() - 5);
                    var json = e.getValue().getAsJsonObject();
                    items.add(new ListPicker.Item("Preset", id, p.name(), () -> app.importBiome(id, json)));
                }
            } catch (RuntimeException ignored) { }
        }
        String hy = hytaleAssetsPath(app);
        if (hy != null) {
            try (var pack = com.worldgenv2.hytale.HytaleAssets.open(hy)) {
                for (String f : pack.biomeFiles()) {
                    String id = f.substring(f.lastIndexOf('/') + 1, f.length() - 5);
                    String folder = f.substring(7, f.lastIndexOf('/') < 7 ? 7 : f.lastIndexOf('/'));
                    items.add(new ListPicker.Item("Hytale", id, folder.isEmpty() ? "Hytale biome" : "Hytale  " + folder, () -> {
                        try (var p2 = com.worldgenv2.hytale.HytaleAssets.open(hy)) {
                            app.importBiome(id, com.google.gson.JsonParser.parseString(p2.generatorText(f)).getAsJsonObject());
                        } catch (Exception ex) { app.say("Could not read that Hytale biome: " + ex.getMessage(), com.worldgenv2.studio.ui.Ui.RED); }
                    }));
                }
            } catch (Exception ex) {
                app.say("Could not read the Hytale assets: " + ex.getMessage(), com.worldgenv2.studio.ui.Ui.RED);
            }
        }
        ListPicker.open(ui, "New biome", hy == null ? "no Hytale install found" : "type to search", "Search biomes, presets, Hytale...", items);
    }

    /** Where this world setup's Hytale assets are: its own setting, else the launcher's install. */
    private static String hytaleAssetsPath(StudioApp app) {
        if (app.project != null) {
            for (Asset a : app.project.assetsOf(AssetType.DIMENSION)) {
                var hy = com.worldgenv2.studio.project.J.obj(a.json, "hytale");
                if (hy != null && !com.worldgenv2.studio.project.J.str(hy, "assets", "").isBlank()) return com.worldgenv2.studio.project.J.str(hy, "assets", "");
            }
        }
        return com.worldgenv2.hytale.HytaleAssets.defaultAssetsPath();
    }

    /** New dimension: any dimension the game knows (vanilla and modded), or a custom id. Vanilla generation stays until switched. */
    static void newDimension(UiRoot ui, StudioApp app) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        try {
            var server = LiveWorld.server();
            if (server != null) {
                for (var k : server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM).keySet()) ids.add(k.toString());
            }
        } catch (RuntimeException ignored) { }
        for (Asset d : app.project.assetsOf(AssetType.DIMENSION)) ids.remove(com.worldgenv2.studio.project.J.str(d.json, "id", ""));
        List<ListPicker.Item> items = new ArrayList<>();
        for (String id : ids) {
            String file = id.substring(id.indexOf(':') + 1);
            boolean mod = !id.startsWith("minecraft:");
            items.add(new ListPicker.Item(mod ? "Modded" : "Vanilla", id.substring(id.indexOf(':') + 1).replace('_', ' '), id, () -> {
                Asset a = app.createAsset(AssetType.DIMENSION, file);
                app.project.edit(a, "Dimension id", j -> {
                    j.addProperty("id", id);
                    j.addProperty("generator", id.equals("minecraft:overworld") ? "worldgenv2" : "vanilla");
                });
            }));
        }
        items.add(new ListPicker.Item("Custom", "Custom dimension...", "type your own id", () -> newAssetPrompt(ui, app, AssetType.DIMENSION)));
        ListPicker.open(ui, "New dimension", ids.isEmpty() ? "all present" : "pick one the game already has", "Search dimensions...", items);
    }

    static void newAsset(UiRoot ui, StudioApp app, AssetType t) {
        if (t == AssetType.BIOME) { newBiome(ui, app); return; }
        if (t == AssetType.DIMENSION) { newDimension(ui, app); return; }
        newAssetPrompt(ui, app, t);
    }

    static void newAssetPrompt(UiRoot ui, StudioApp app, AssetType t) {
        String suggestion = switch (t) {
            case BIOME -> "new_biome";
            case DIMENSION -> "new_dimension";
            case ZONE -> "new_zone";
            case RULE -> "new_rule";
            default -> "new_asset";
        };
        Modal.prompt(ui, "New " + t.label.toLowerCase(), "Name (letters, numbers, underscores)", suggestion, "Create",
                v -> app.project.checkNewId(t, v), name -> app.createAsset(t, name));
    }

    static void duplicate(UiRoot ui, StudioApp app, Asset a) {
        Modal.prompt(ui, "Duplicate " + a.type.label.toLowerCase(), "Name for the copy", a.id() + "_copy", "Duplicate",
                v -> app.project.checkNewId(a.type, v), name -> app.duplicateAsset(a, name));
    }

    static void rename(UiRoot ui, StudioApp app, Asset a) {
        Modal.prompt(ui, "Rename " + a.type.label.toLowerCase(), "Dimension pools that use it are updated automatically.", a.id(), "Rename",
                v -> StudioProject.sanitize(v).equals(a.id()) ? null : app.project.checkNewId(a.type, v),
                name -> { if (!StudioProject.sanitize(name).equals(a.id())) app.renameAsset(a, name); });
    }

    static void delete(UiRoot ui, StudioApp app, Asset a) {
        int users = 0;
        if (a.type == AssetType.BIOME) {
            for (Asset d : app.project.assetsOf(AssetType.DIMENSION)) {
                var arr = com.worldgenv2.studio.project.J.arr(d.json, "biomes");
                if (arr == null) continue;
                for (var e : arr) if (e.isJsonObject() && a.id().equals(com.worldgenv2.studio.project.J.str(e.getAsJsonObject(), "biome", ""))) users++;
            }
        }
        String msg = "'" + a.displayName() + "' will be moved to the project's .trash folder."
                + (users > 0 ? "\nIt is used by " + users + " dimension pool" + (users == 1 ? "" : "s") + " - those will show problems." : "");
        Modal.confirm(ui, "Delete " + a.type.label.toLowerCase() + "?", msg, "Delete", true, () -> app.deleteAsset(a));
    }

    static void newProject(UiRoot ui, StudioApp app) {
        Modal.prompt(ui, "New world setup", "Starts from the Default. Turn features on or off, or customise them, as you go.", "my_world", "Create",
                ProjectStore::checkNewName, app::createProject);
    }

    /** True when an addon mod has installed something beyond the built-in Default. */
    static boolean hasAddons() {
        return com.worldgenv2.pack.WorldGenV2Addons.presets().stream().anyMatch(p -> !p.id().startsWith("worldgenv2:"));
    }

    static void addFromPreset(UiRoot ui, StudioApp app) {
        var presets = com.worldgenv2.pack.WorldGenV2Addons.presets().stream().filter(p -> !p.id().startsWith("worldgenv2:")).toList();
        java.util.List<String> ids = new ArrayList<>();
        java.util.Map<String, String> names = new java.util.HashMap<>();
        for (var p : presets) { ids.add(p.id()); names.put(p.id(), (p.isPack() ? "[pack] " : "[world] ") + p.name()); }
        Modal.choose(ui, "Import from addon", "Adds the addon's content's biomes and prefabs; existing assets are never overwritten.", ids,
                ids.isEmpty() ? "" : ids.get(0), "Add", app::addFromPreset);
    }

    static void guardUnsaved(UiRoot ui, StudioApp app, Runnable then) {
        if (app.project == null || !app.project.isDirty()) { then.run(); return; }
        Modal.confirm(ui, "Unsaved changes", app.project.dirtyCount() + " file(s) have unsaved changes.\nDiscard them and continue?",
                "Discard", true, then);
    }

    static void projectMenu(UiRoot ui, StudioApp app, int x, int y) {
        List<PopupMenu.Item> items = new ArrayList<>();
        for (ProjectStore.Entry e : ProjectStore.list()) {
            boolean cur = app.project != null && app.project.name.equals(e.name());
            items.add(cur ? PopupMenu.Item.colored(e.name() + "  (open)", 0xFFFFAA00, null)
                    : PopupMenu.Item.of(e.name(), () -> guardUnsaved(ui, app, () -> app.openProject(e.name()))));
        }
        if (items.isEmpty()) items.add(PopupMenu.Item.disabled("No projects yet"));
        items.add(PopupMenu.Item.sep());
        items.add(PopupMenu.Item.of("New project...", () -> guardUnsaved(ui, app, () -> newProject(ui, app))));
        if (app.project != null) {
            if (hasAddons()) items.add(PopupMenu.Item.of("Import from addon...", () -> addFromPreset(ui, app)));
            items.add(PopupMenu.Item.of("Import .nbt structures...", () -> importNbt(app)));
            items.add(PopupMenu.Item.of("Export as shareable file", app::exportPreset));
        }
        if (app.project != null) items.add(PopupMenu.Item.of("Close project", () -> guardUnsaved(ui, app, app::closeProject)));
        PopupMenu.open(ui, x, y, 190, items);
    }

    static void importNbt(StudioApp app) {
        if (app.project == null) return;
        java.nio.file.Path importsDir = java.nio.file.Path.of("config", "worldgenv2", "imports");
        java.nio.file.Path prefabsDir = app.project.dir.resolve("prefabs");
        try {
            java.nio.file.Files.createDirectories(importsDir);
            java.util.List<java.nio.file.Path> nbtFiles = new ArrayList<>();
            for (java.nio.file.Path dir : new java.nio.file.Path[]{importsDir, prefabsDir}) {
                if (!java.nio.file.Files.isDirectory(dir)) continue;
                try (var s = java.nio.file.Files.list(dir)) {
                    s.filter(p -> p.toString().toLowerCase().endsWith(".nbt")).forEach(nbtFiles::add);
                }
            }
            if (nbtFiles.isEmpty()) {
                app.say("Drop .nbt structure files into " + importsDir + " and run Import again.", com.worldgenv2.studio.ui.Ui.YELLOW);
                return;
            }
            int imported = 0;
            for (java.nio.file.Path f : nbtFiles) {
                String rawName = f.getFileName().toString();
                String id = StudioProject.sanitize(rawName.substring(0, rawName.length() - 4));
                if (id.isBlank()) continue;
                var json = com.worldgenv2.prefab.NbtPrefabImporter.importFile(f, "worldgenv2:" + id);
                String assetPath = "prefabs/" + id + ".json";
                Asset target = app.project.get(assetPath);
                if (target == null) {
                    target = app.project.create(AssetType.PREFAB, id);
                }
                if (target != null) {
                    app.project.edit(target, "Import NBT " + id, old -> {
                        for (String k : new ArrayList<>(old.keySet())) old.remove(k);
                        for (var e : json.entrySet()) old.add(e.getKey(), e.getValue());
                    });
                }
                imported++;
            }
            app.uiRevision++;
            app.say("Imported " + imported + " .nbt structure(s) into Prefabs. Save to keep them.", com.worldgenv2.studio.ui.Ui.GREEN);
        } catch (Exception e) {
            app.say("NBT import failed: " + e.getMessage(), com.worldgenv2.studio.ui.Ui.RED);
        }
    }
}
