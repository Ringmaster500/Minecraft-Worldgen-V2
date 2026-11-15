package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.ui.*;

final class TopBar extends Widget {
    private final StudioApp app;
    private Button project, undo, redo, save, close, apply, regen, liveBtn, pinBtn, radBtn; // not final: the project button's lambda refers to itself
    private final Runnable onClose;

    TopBar(StudioApp app, Runnable onClose) {
        this.app = app;
        this.onClose = onClose;
        project = add(new Button("", () -> { if (root != null) Dialogs.projectMenu(root, app, this.project.x, this.project.y + this.project.h + 2); }));
        undo = add(new Button("Undo", app::undo));
        redo = add(new Button("Redo", app::redo));
        save = add(new Button("Save", app::save).style(Button.Style.PRIMARY));
        close = add(new Button("x", onClose).style(Button.Style.DANGER));
        apply = add(new Button("Apply", app::applyToWorld));
        apply.tooltip = "Save, make this the active project and re-compile it into the open world  (Ctrl+Enter)";
        regen = add(new Button("Regen", app::regenAroundPlayer));
        regen.tooltip = "Rebuild the whole world generated so far (saved chunks and everything around you) with the current setup. Player builds in those chunks are replaced.";
        liveBtn = add(new Button("Live", () -> app.setLive(!app.live)));
        liveBtn.tooltip = "Live preview: after each edit, rebuild only a square of chunks around you so you see the change in the world";
        pinBtn = add(new Button("Pin", app::repinLive));
        pinBtn.tooltip = "Move the live preview square to where you are standing now";
        radBtn = add(new Button("", app::cycleLiveRadius));
        radBtn.tooltip = "Size of the live preview square (chunks out from the centre). Click to change.";
        undo.tooltip = "Ctrl+Z";
        redo.tooltip = "Ctrl+Y";
        save.tooltip = "Ctrl+S";
        close.tooltip = "Close Studio (Esc)";
    }

    @Override
    public void layout() {
        int by = y + (h - 18) / 2;
        Gfx g = Probe2.g();
        int tx = x + 10 + g.width("WORLDGEN STUDIO") + 14;
        project.label = app.project == null ? "No setup open" : app.project.name;
        project.dot = app.project == null ? 0 : 0xFFFFAA00;
        project.setBounds(tx, by, Math.max(110, Math.min(220, g.width(project.label) + 30)), 18);
        boolean has = app.project != null;
        undo.visible = redo.visible = save.visible = apply.visible = regen.visible = liveBtn.visible = has;
        pinBtn.visible = radBtn.visible = has && app.live;
        int rx = x + w - 6 - 20;
        close.setBounds(rx, by, 20, 18);
        rx -= 54 + 6;
        save.setBounds(rx, by, 54, 18);
        redo.setBounds(rx - 6 - 44, by, 44, 18);
        undo.setBounds(rx - 6 - 44 - 4 - 44, by, 44, 18);
        regen.setBounds(undo.x - 14 - 46, by, 46, 18);
        apply.setBounds(regen.x - 4 - 50, by, 50, 18);
        liveBtn.setBounds(apply.x - 4 - 40, by, 40, 18);
        radBtn.setBounds(liveBtn.x - 4 - 34, by, 34, 18);
        pinBtn.setBounds(radBtn.x - 4 - 34, by, 34, 18);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        g.fill(x, y, w, h, Ui.BG_APP);
        g.hline(x, y + h - 1, w, Ui.LINE_STRONG);
        g.textMid("WORLDGEN STUDIO", x + 10, y, h, Ui.ACCENT);
        if (LiveWorld.inWorld() && com.worldgenv2.pack.ProjectRuntime.isWorldLinked()) {
            String ws = com.worldgenv2.pack.ProjectRuntime.activeProjectName();
            boolean same = ws != null && app.project != null && ws.equals(app.project.name);
            String msg = ws == null ? "this world: built-in default" : same ? "editing this world's setup" : "this world uses '" + ws + "' - Apply will switch it";
            g.textMid(g.fit(msg, Math.max(40, (app.live ? pinBtn.x : liveBtn.x) - 200 - (project.x + project.w))), project.x + project.w + 10, y, h, same ? Ui.TEXT_FAINT : Ui.YELLOW);
        }
        boolean has = app.project != null;
        undo.enabled = has && app.project.canUndo();
        redo.enabled = has && app.project.canRedo();
        save.enabled = has && app.project.isDirty();
        regen.enabled = LiveWorld.inWorld();
        liveBtn.selected = app.live;
        liveBtn.enabled = LiveWorld.inWorld();
        radBtn.label = "r" + app.liveRadius;
        app.tickLive();
        if (has && undo.enabled) undo.tooltip = "Undo: " + app.project.undoLabel() + "  (Ctrl+Z)";
        if (has && redo.enabled) redo.tooltip = "Redo: " + app.project.redoLabel() + "  (Ctrl+Y)";
        if (has) {
            int d = app.project.dirtyCount();
            String s = d == 0 ? "All changes saved" : d + " unsaved";
            g.textRight(s, (app.live ? pinBtn.x : liveBtn.x) - 12, y, h, d == 0 ? Ui.TEXT_FAINT : Ui.ACCENT);
        }
    }
}
