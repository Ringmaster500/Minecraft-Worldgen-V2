package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.ProjectStore;
import com.worldgenv2.studio.ui.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** What you see when no project is open: pick a project or start one. */
final class HomeView extends Widget {
    private final StudioApp app;
    private final Button create;
    private List<ProjectStore.Entry> entries = List.of();
    private long loadedAt;
    private int listX, listY, listW;

    HomeView(StudioApp app, UiRootProvider rootOf) {
        this.app = app;
        create = add(new Button("New project", () -> Dialogs.newProject(rootOf.get(), app)).style(Button.Style.PRIMARY));
    }

    interface UiRootProvider { UiRoot get(); }

    @Override
    public void layout() {
        int cw = Math.min(560, w - 40);
        int cx = x + (w - cw) / 2;
        listX = cx;
        listW = cw;
        listY = y + Math.max(24, h / 6) + 78;
        create.setBounds(cx + cw - 96, listY - 24, 96, 18);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        g.fill(x, y, w, h, Ui.BG_PANEL);
        if (System.currentTimeMillis() - loadedAt > 2000) { entries = ProjectStore.list(); loadedAt = System.currentTimeMillis(); }
        int top = listY - 78;
        g.text("WorldGen Studio", listX, top, Ui.TEXT);
        g.text("Design worlds the way Hytale's generator does: biomes, densities and layouts as data,", listX, top + 16, Ui.TEXT_DIM);
        g.text("organised as a project you can version, share, and ship inside a modpack.", listX, top + 28, Ui.TEXT_DIM);
        g.textMid("PROJECTS", listX, listY - 24, 18, Ui.ACCENT);
        g.hline(listX, listY - 4, listW, Ui.LINE);

        if (entries.isEmpty()) {
            g.text("No projects yet. Create one to get a starter pack with example biomes", listX, listY + 6, Ui.TEXT_DIM);
            g.text("and the Overworld, Nether and End already set up.", listX, listY + 18, Ui.TEXT_DIM);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int ry = listY;
        for (ProjectStore.Entry e : entries) {
            boolean hov = Gfx.in(mx, my, listX, ry, listW, 28);
            g.box(listX, ry, listW, 28, hov ? Ui.BG_HOVER : Ui.BG_RAISED, hov ? 0xFF3D445C : Ui.LINE_STRONG);
            g.fill(listX, ry, 3, 28, Ui.ACCENT);
            g.textMid(e.name(), listX + 12, ry, 28, Ui.TEXT);
            g.textRight("modified " + fmt.format(new Date(e.modified())), listX + listW - 10, ry, 28, Ui.TEXT_FAINT);
            ry += 32;
            if (ry > y + h - 40) break;
        }
        g.text("Projects live in config/worldgenv2/projects/", listX, y + h - 22, Ui.TEXT_FAINT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        int ry = listY;
        for (ProjectStore.Entry e : entries) {
            if (Gfx.in(mx, my, listX, ry, listW, 28)) { app.openProject(e.name()); return true; }
            ry += 32;
        }
        return false;
    }
}
