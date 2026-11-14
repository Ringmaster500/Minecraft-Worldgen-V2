package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.ui.*;

import java.util.List;

/** Bottom drawer listing every validation problem in the project; click a row to jump to the asset. */
final class ProblemsPanel extends Widget {
    private static final int ROW = 14;
    private final StudioApp app;
    private int scroll;

    ProblemsPanel(StudioApp app) { this.app = app; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_PANEL);
        g.hline(x, y, w, Ui.LINE_STRONG);
        List<Asset.Problem> list = app.project == null ? List.of() : app.project.allProblems();
        g.textMid("PROBLEMS", x + 8, y + 1, 16, Ui.TEXT_FAINT);
        g.textMid(list.size() + (list.size() == 1 ? " issue" : " issues"), x + 70, y + 1, 16, Ui.TEXT_DIM);
        g.textRight("Ctrl+J to hide", x + w - 8, y + 1, 16, Ui.TEXT_FAINT);
        int top = y + 18;
        int visible = Math.max(0, (h - 18) / ROW);
        scroll = Math.max(0, Math.min(Math.max(0, list.size() - visible), scroll));
        g.clip(x, top, w, h - 18);
        if (list.isEmpty()) g.textMid("No problems. Everything validates.", x + 10, top, ROW + 2, Ui.GREEN);
        for (int i = 0; i < visible && i + scroll < list.size(); i++) {
            Asset.Problem p = list.get(i + scroll);
            int ry = top + i * ROW;
            if (Gfx.in(mx, my, x, ry, w, ROW)) g.fill(x, ry, w, ROW, Ui.BG_HOVER);
            int c = p.severity() == Asset.Severity.ERROR ? Ui.RED : p.severity() == Asset.Severity.WARNING ? Ui.YELLOW : Ui.BLUE;
            g.fill(x + 10, ry + 5, 4, 4, c);
            g.textMid(p.assetPath(), x + 20, ry, ROW, Ui.TEXT_DIM);
            int mxs = x + 20 + g.width(p.assetPath()) + 10;
            g.textFit(p.message(), mxs, ry, ROW, x + w - mxs - 8, Ui.TEXT);
        }
        g.unclip();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        List<Asset.Problem> list = app.project == null ? List.of() : app.project.allProblems();
        int i = (int) ((my - (y + 18)) / ROW) + scroll;
        if (my >= y + 18 && i >= 0 && i < list.size()) app.openDoc(list.get(i).assetPath());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy) * 3;
        return true;
    }
}
