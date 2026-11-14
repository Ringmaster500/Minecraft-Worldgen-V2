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

    static void newAsset(UiRoot ui, StudioApp app, AssetType t) {
        String suggestion = switch (t) {
            case BIOME -> "new_biome";
            case DIMENSION -> "new_dimension";
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
            items.add(PopupMenu.Item.of("Export as shareable file", app::exportPreset));
        }
        if (app.project != null) items.add(PopupMenu.Item.of("Close project", () -> guardUnsaved(ui, app, app::closeProject)));
        PopupMenu.open(ui, x, y, 190, items);
    }
}
