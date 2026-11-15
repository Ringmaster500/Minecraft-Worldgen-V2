package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.ui.*;

/**
 * Top-level layout. One place decides where every pane goes:
 * <pre>
 *  ┌ TopBar ───────────────────────────────────────────┐
 *  │ Explorer │ Tabs + header + editor (DocPane)       │
 *  │          ├────────────────────────────────────────┤
 *  │          │ Problems (toggle)                      │
 *  └ StatusBar ────────────────────────────────────────┘
 * </pre>
 * With no project open the body is the Home view instead.
 */
public final class Shell extends Widget {
    private final StudioApp app;
    private final TopBar top;
    private final Explorer explorer;
    private final Splitter splitter;
    private final DocPane docs;
    private final ProblemsPanel problems;
    private final StatusBar status;
    private final HomeView home;
    private final GuideView guide;
    private int explorerW = 220;

    public Shell(StudioApp app, Runnable onClose) {
        this.app = app;
        top = add(new TopBar(app, onClose));
        home = add(new HomeView(app, () -> root));
        guide = add(new GuideView(app));
        explorer = add(new Explorer(app));
        docs = add(new DocPane(app));
        problems = add(new ProblemsPanel(app));
        status = add(new StatusBar(app));
        splitter = add(new Splitter(mx -> {
            explorerW = Math.max(150, Math.min(w / 2, mx - x));
            layout();
        }));
    }

    @Override
    public void layout() {
        boolean has = app.project != null;
        top.setBounds(x, y, w, Ui.TOPBAR_H);
        status.setBounds(x, y + h - Ui.STATUS_H, w, Ui.STATUS_H);
        int bodyY = y + Ui.TOPBAR_H;
        int bodyH = h - Ui.TOPBAR_H - Ui.STATUS_H;
        home.visible = !has && !app.guideOpen;
        guide.visible = app.guideOpen;
        explorer.visible = docs.visible = splitter.visible = has && !app.guideOpen;
        problems.visible = has && app.problemsOpen && !app.guideOpen;
        if (app.guideOpen) { guide.setBounds(x, bodyY, w, bodyH); return; }
        if (!has) {
            home.setBounds(x, bodyY, w, bodyH);
            return;
        }
        int pH = problems.visible ? Math.min(110, bodyH / 3) : 0;
        explorer.setBounds(x, bodyY, explorerW, bodyH);
        splitter.setBounds(x + explorerW - 2, bodyY, 4, bodyH);
        docs.setBounds(x + explorerW, bodyY, w - explorerW, bodyH - pH);
        problems.setBounds(x + explorerW, bodyY + bodyH - pH, w - explorerW, pH);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_APP);
        layout();
    }
}
