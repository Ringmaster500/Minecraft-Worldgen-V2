package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.ui.*;

final class StatusBar extends Widget {
    private final StudioApp app;
    private int hitX, hitW;

    StatusBar(StudioApp app) { this.app = app; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_APP);
        g.hline(x, y, w, Ui.LINE);
        String st = app.status();
        g.textMid(st.isEmpty() ? "Ready" : st, x + 8, y, h, st.isEmpty() ? Ui.TEXT_FAINT : app.statusColor());
        if (app.project == null) return;
        int errs = app.project.count(Asset.Severity.ERROR), warns = app.project.count(Asset.Severity.WARNING);
        String e = errs + " errors", wn = warns + " warnings";
        int total = g.width(e) + g.width(wn) + 34;
        hitX = x + w - total - 6;
        hitW = total;
        boolean hov = Gfx.in(mx, my, hitX - 4, y, hitW + 8, h);
        if (hov || app.problemsOpen) g.fill(hitX - 4, y + 1, hitW + 8, h - 1, Ui.BG_HOVER);
        int px = hitX;
        g.fill(px, y + 5, 4, 4, errs > 0 ? Ui.RED : Ui.TEXT_FAINT);
        g.textMid(e, px + 8, y, h, errs > 0 ? Ui.TEXT : Ui.TEXT_DIM);
        px += 8 + g.width(e) + 10;
        g.fill(px, y + 5, 4, 4, warns > 0 ? Ui.YELLOW : Ui.TEXT_FAINT);
        g.textMid(wn, px + 8, y, h, warns > 0 ? Ui.TEXT : Ui.TEXT_DIM);
        tooltip = "Show / hide problems (Ctrl+J)";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (app.project != null && mx >= hitX - 4) { app.problemsOpen = !app.problemsOpen; app.uiRevision++; }
        return true;
    }
}
