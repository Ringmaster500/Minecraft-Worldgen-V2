package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The in-game Guide: search and a contents list on the left, a page on the right (with a live demo for density nodes). */
final class GuideView extends Widget {
    private static final int NAV_W = 230, ROW = 14;

    private record Row(String header, GuideContent.Page page) {}
    private record Line(String text, int kind) {}      // kind: 0 paragraph, 1 heading, 2 bullet, 3 formula

    private final StudioApp app;
    private final TextField search = new TextField().placeholder("Search the guide...");
    private List<Row> rows = new ArrayList<>();
    private String selectedId = "start";
    private int navScroll, pageScroll;
    private Widget demo;
    private String demoFor = "";
    private List<Line> lines = new ArrayList<>();
    private String linesFor = "";
    private int linesW = -1;
    private String builtFilter = null;
    private final Button addRecipe = new Button("Add to my setup", () -> {});

    GuideView(StudioApp app) {
        this.app = app;
        add(search);
        search.onChange = s -> { builtFilter = null; navScroll = 0; };
        add(addRecipe);
        addRecipe.style(Button.Style.PRIMARY);
        addRecipe.tooltip = "Adds this biome to the open world setup so you can open it in the Graph and change it";
        addRecipe.onClick = () -> {
            GuideRecipes.Recipe r = selectedId.startsWith("recipe:") ? GuideRecipes.byId(selectedId.substring(7)) : null;
            if (r == null) return;
            if (app.project == null) { app.say("Open a world setup first (the project button at the top), then add this biome to it.", Ui.YELLOW); return; }
            app.guideOpen = false;
            app.importBiome(r.id().startsWith("guide_") ? r.id() : "guide_" + r.id(), r.biome());
        };
    }

    @Override
    public void layout() {
        search.setBounds(x + 6, y + 6, NAV_W - 12, Ui.FIELD_H);
        if (demo != null) demo.setBounds(x + NAV_W + 10, y + 34, w - NAV_W - 20, demo.h);
    }

    private void rebuildRows() {
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        builtFilter = q;
        rows = new ArrayList<>();
        String last = null;
        for (GuideContent.Page p : GuideContent.pages()) {
            if (!q.isEmpty() && !p.search().contains(q)) continue;
            if (!p.section().equals(last)) { rows.add(new Row(p.section(), null)); last = p.section(); }
            rows.add(new Row(null, p));
        }
    }

    private GuideContent.Page selected() {
        for (GuideContent.Page p : GuideContent.pages()) if (p.id().equals(selectedId)) return p;
        return GuideContent.pages().isEmpty() ? null : GuideContent.pages().get(0);
    }

    private void select(String id) {
        selectedId = id;
        pageScroll = 0;
    }

    /** Wraps the page's markup to {@code width} pixels. */
    private List<Line> wrap(Gfx g, List<String> markup, int width) {
        List<Line> out = new ArrayList<>();
        for (String m : markup) {
            int kind = 0, indent = 0;
            String text = m;
            if (m.startsWith("# ")) { kind = 1; text = m.substring(2); }
            else if (m.startsWith("- ")) { kind = 2; text = m.substring(2); indent = 10; }
            else if (m.startsWith("`")) { kind = 3; text = m.substring(1); }
            if (kind == 1 && !out.isEmpty()) out.add(new Line("", 0));
            String[] words = text.split(" ");
            StringBuilder cur = new StringBuilder();
            boolean first = true;
            for (String wd : words) {
                String trial = cur.length() == 0 ? wd : cur + " " + wd;
                if (g.width(trial) > width - indent && cur.length() > 0) {
                    out.add(new Line((kind == 2 && first ? "• " : kind == 2 ? "  " : "") + cur, kind == 2 ? 2 : kind));
                    first = false;
                    cur = new StringBuilder(wd);
                } else cur = new StringBuilder(trial);
            }
            out.add(new Line((kind == 2 && first ? "• " : kind == 2 ? "  " : "") + cur, kind == 2 ? 2 : kind));
        }
        return out;
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        if (builtFilter == null) rebuildRows();
        layout();
        g.fill(x, y, w, h, Ui.BG_PANEL);
        g.vline(x + NAV_W, y, h, Ui.LINE);

        // contents
        int top = y + 28;
        int visible = (h - 32) / ROW;
        navScroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), navScroll));
        g.clip(x, top, NAV_W, h - 32);
        for (int i = 0; i < visible && i + navScroll < rows.size(); i++) {
            Row r = rows.get(i + navScroll);
            int ry = top + i * ROW;
            if (r.header() != null) {
                g.textMid(r.header().toUpperCase(Locale.ROOT), x + 8, ry, ROW, Ui.TEXT_FAINT);
            } else {
                boolean sel = r.page().id().equals(selectedId);
                if (sel) g.fill(x + 1, ry, NAV_W - 2, ROW, Ui.BG_ACTIVE);
                else if (Gfx.in(mx, my, x, ry, NAV_W, ROW)) g.fill(x + 1, ry, NAV_W - 2, ROW, Ui.BG_HOVER);
                if (r.page().node() != null) g.fill(x + 10, ry + 5, 4, 4, NodeSchemas.kindColor(r.page().node().kind()));
                g.textFit(r.page().title(), x + (r.page().node() != null ? 20 : 14), ry, ROW, NAV_W - 26, sel ? Ui.TEXT : Ui.TEXT_DIM);
            }
        }
        if (rows.isEmpty()) g.textMid("Nothing matches.", x + 10, top, ROW, Ui.TEXT_FAINT);
        g.unclip();

        // page
        GuideContent.Page p = selected();
        if (p == null) return;
        int px = x + NAV_W + 12, pw = w - NAV_W - 24;
        if (p.node() != null) {
            g.fill(px, y + 12, 6, 6, NodeSchemas.kindColor(p.node().kind()));
            g.textMid(p.title(), px + 12, y + 8, 14, Ui.TEXT);
            g.textMid(p.node().kind() + " node", px + 16 + g.width(p.title()) + 6, y + 8, 14, Ui.TEXT_FAINT);
        } else {
            g.textMid(p.title(), px, y + 8, 14, Ui.ACCENT);
        }
        g.hline(px, y + 26, pw, Ui.LINE);
        addRecipe.visible = p.id().startsWith("recipe:");
        if (addRecipe.visible) addRecipe.setBounds(px + pw - 130, y + 6, 130, 16);

        if (!p.id().equals(demoFor)) {
            if (demo != null) { remove(demo); demo = null; }
            demoFor = p.id();
            if (p.node() != null) {
                demo = DemoInfo.forNode(p.node());
                if (demo != null) { add(demo); layout(); }
            }
        }
        int textTop = y + 34;
        if (demo != null) textTop += demo.h + 8;

        if (linesW != pw || !linesFor.equals(p.id())) {
            List<String> markup = new ArrayList<>(p.node() != null ? GuideContent.nodeLines(p.node()) : p.lines());
            if (demo instanceof DemoInfo di && !di.standIns().isEmpty()) {
                markup.add("# Stand-in inputs in the demo above");
                for (String s : di.standIns()) markup.add("- " + s);
            }
            lines = wrap(g, markup, pw);
            linesW = pw;
            linesFor = p.id();
        }
        int avail = y + h - textTop - 6;
        int total = lines.size() * 12;
        pageScroll = Math.max(0, Math.min(Math.max(0, total - avail), pageScroll));
        g.clip(px, textTop, pw, avail);
        for (int i = 0; i < lines.size(); i++) {
            int ly = textTop + i * 12 - pageScroll;
            if (ly < textTop - 12 || ly > textTop + avail) continue;
            Line l = lines.get(i);
            switch (l.kind()) {
                case 1 -> g.textMid(l.text(), px, ly, 12, Ui.ACCENT);
                case 2 -> g.textMid(l.text(), px + 4, ly, 12, Ui.TEXT_DIM);
                case 3 -> { g.fill(px, ly, pw, 12, Ui.BG_INPUT); g.textMid(l.text(), px + 4, ly, 12, Ui.CYAN); }
                default -> g.textMid(l.text(), px, ly, 12, Ui.TEXT_DIM);
            }
        }
        g.unclip();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0) return false;
        if (mx >= x && mx < x + NAV_W && my >= y + 28) {
            int i = (int) ((my - (y + 28)) / ROW) + navScroll;
            if (i >= 0 && i < rows.size() && rows.get(i).page() != null) { select(rows.get(i).page().id()); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int step = (int) -Math.signum(dy) * 3;
        if (mx < x + NAV_W) navScroll += step; else pageScroll += step * 12;
        return true;
    }
}
