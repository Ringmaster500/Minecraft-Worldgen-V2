package com.worldgenv2.studio.shell;

import com.worldgenv2.pack.ProjectCompiler;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.ui.*;
import com.worldgenv2.worldstructure.DimensionLayoutConfig;
import com.worldgenv2.worldstructure.MacroFeatureGraph;
import com.worldgenv2.worldstructure.StructureBudgetManager;
import com.worldgenv2.worldstructure.WorldStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Live top-down map of the dimension being edited: biome layout from the real engine, plus the spawn anchor,
 * landmarks, roads, budgeted structures and zones. Pan with the mouse, zoom with the wheel.
 */
final class MapPreview extends Widget {
    private static final int STEP = 2; // virtual px per sampled cell

    private final StudioApp app;
    private final PreviewEngine engine = new PreviewEngine();
    private final Identifier texId = Identifier.fromNamespaceAndPath("worldgenv2", "studio/map_preview");
    private DynamicTexture tex;
    private int texW, texH;

    private String dimId = "";
    private double centerX, centerZ;
    private double scale = 6; // blocks per virtual pixel
    private PreviewEngine.Frame frame;
    private long lastRequestRev = -1;
    private String lastRequestKey = "";
    private long lastRequestTime;
    private boolean dragged;

    private final Button zoomIn, zoomOut, home;
    private List<Asset.Problem> lastWarnings = List.of();

    MapPreview(StudioApp app) {
        this.app = app;
        zoomIn = add(new Button("+", () -> zoom(0.7)));
        zoomOut = add(new Button("-", () -> zoom(1.0 / 0.7)));
        home = add(new Button("Spawn", this::goHome));
        zoomIn.tooltip = "Zoom in";
        zoomOut.tooltip = "Zoom out";
        home.tooltip = "Center on the spawn anchor / origin";
    }

    void target(String dimensionAssetId) {
        if (!dimensionAssetId.equals(dimId)) {
            dimId = dimensionAssetId;
            frame = null;
            lastRequestKey = "";
            centerX = centerZ = 0;
            goHome();
        }
    }

    private void zoom(double f) {
        scale = Math.max(0.5, Math.min(160, scale * f));
        lastRequestKey = "";
    }

    private void goHome() {
        centerX = centerZ = 0;
        if (frame != null && frame.compiled != null && frame.compiled.spawn != null) {
            centerX = frame.compiled.spawn.originX();
            centerZ = frame.compiled.spawn.originZ();
        }
        lastRequestKey = "";
    }

    @Override
    public void layout() {
        zoomIn.setBounds(x + w - 22, y + 6, 16, 16);
        zoomOut.setBounds(x + w - 22, y + 24, 16, 16);
        home.setBounds(x + w - 56, y + 42, 50, 16);
    }

    private long seedOverride() {
        Asset a = app.project == null ? null : app.project.get("dimensions/" + dimId + ".json");
        return a == null ? Long.MIN_VALUE : (long) J.num(a.json, "seed", 1337);
    }

    private double sx(double wx) { return x + w / 2.0 + (wx - centerX) / scale; }
    private double sy(double wz) { return y + h / 2.0 + (wz - centerZ) / scale; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, 0xFF080A0F);
        g.border(x, y, w, h, Ui.LINE_STRONG);
        if (app.project == null || dimId.isEmpty()) return;

        int tw = Math.max(16, Math.min(420, (w - 2) / STEP)), th = Math.max(16, Math.min(420, (h - 2) / STEP));
        long now = System.currentTimeMillis();
        String key = app.project.revision + "|" + dimId + "|" + (long) centerX / 8 + "|" + (long) centerZ / 8 + "|" + scale + "|" + tw + "x" + th;
        if (!key.equals(lastRequestKey) && now - lastRequestTime > 90) {
            lastRequestKey = key;
            lastRequestTime = now;
            engine.request(app.project, dimId, seedOverride(), app.project.revision + "|" + dimId,
                    centerX, centerZ, scale * STEP, tw, th);
        }
        PreviewEngine.Frame f = engine.take();
        if (f != null) {
            frame = f;
            if (f.argb != null) uploadFrame(f);
            publishWarnings(f);
        }

        g.clip(x + 1, y + 1, w - 2, h - 2);
        if (frame != null && tex != null && frame.argb != null) {
            double worldLeft = frame.centerX - frame.texW * frame.blocksPerSample / 2.0;
            double worldTop = frame.centerZ - frame.texH * frame.blocksPerSample / 2.0;
            float dx = (float) sx(worldLeft), dy = (float) sy(worldTop);
            float k = (float) (frame.blocksPerSample / scale);
            g.g.pose().pushMatrix();
            g.g.pose().translate(dx, dy);
            g.g.pose().scale(k, k);
            g.g.blit(RenderPipelines.GUI_TEXTURED, texId, 0, 0, 0f, 0f, frame.texW, frame.texH, frame.texW, frame.texH);
            g.g.pose().popMatrix();
            drawOverlay(g, frame.compiled);
        } else {
            g.textCentered(engine.busy() ? "Compiling..." : "No preview", x, y, w, h, Ui.TEXT_FAINT);
        }
        g.unclip();

        drawFooter(g, mx, my);
        updateHover(g, mx, my);
    }

    private void uploadFrame(PreviewEngine.Frame f) {
        if (tex == null || texW != f.texW || texH != f.texH) {
            if (tex != null) Minecraft.getInstance().getTextureManager().release(texId);
            tex = new DynamicTexture(() -> "worldgenv2 map preview", f.texW, f.texH, false);
            Minecraft.getInstance().getTextureManager().register(texId, tex);
            texW = f.texW;
            texH = f.texH;
        }
        var img = tex.getPixels();
        if (img == null) return;
        for (int yy = 0; yy < f.texH; yy++) {
            for (int xx = 0; xx < f.texW; xx++) {
                int c = f.argb[yy * f.texW + xx];
                // NativeImage expects ABGR
                int abgr = (c & 0xFF000000) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
                img.setPixelABGR(xx, yy, abgr);
            }
        }
        tex.upload();
    }

    /** Compile warnings become live problems on the dimension asset so they show in the explorer and Problems panel. */
    private void publishWarnings(PreviewEngine.Frame f) {
        List<Asset.Problem> now = new ArrayList<>();
        String path = "dimensions/" + dimId + ".json";
        if (f.error != null) now.add(new Asset.Problem(Asset.Severity.ERROR, path, "Preview failed: " + f.error));
        if (f.compiled != null) for (String wmsg : f.compiled.warnings) now.add(new Asset.Problem(Asset.Severity.WARNING, path, wmsg));
        if (!now.equals(lastWarnings)) {
            lastWarnings = now;
            Asset a = app.project.get(path);
            if (a != null) { a.live = now; app.uiRevision++; }
        }
    }

    private void drawOverlay(Gfx g, ProjectCompiler.Result res) {
        if (res == null || res.structure == null) return;
        WorldStructure s = res.structure;
        DimensionLayoutConfig layout = s.getDimensionLayout();
        boolean labels = scale <= 14;

        if (layout != null) {
            for (DimensionLayoutConfig.RadialZoneRule z : layout.radialZones()) {
                circle(g, z.centerX(), z.centerZ(), z.innerRadius(), 0x80FFFFFF);
                circle(g, z.centerX(), z.centerZ(), z.outerRadius(), 0x80FFFFFF);
            }
            for (DimensionLayoutConfig.PlacedRingPillar p : layout.resolvedPillars()) {
                g.fill((int) sx(p.centerX()) - 1, (int) sy(p.centerZ()) - 1, 3, 3, 0xFFB0A0FF);
            }
        }
        MacroFeatureGraph graph = s.getMacroFeatureGraph();
        if (graph != null) {
            for (MacroFeatureGraph.RoadEdge e : graph.getRoadEdges()) {
                line(g, sx(e.from().x()), sy(e.from().z()), sx(e.to().x()), sy(e.to().z()), 0xFFE6C619);
            }
            for (MacroFeatureGraph.PlacedLandmark l : graph.getPlacedLandmarks().values()) {
                int px = (int) sx(l.x()), py = (int) sy(l.z());
                g.fill(px - 3, py - 3, 7, 7, 0xFF000000);
                g.fill(px - 2, py - 2, 5, 5, Ui.CYAN);
                if (labels) g.text(l.id(), px + 6, py - 4, 0xFFFFFFFF);
            }
        }
        StructureBudgetManager bm = s.getStructureBudgetManager();
        if (bm != null) {
            for (StructureBudgetManager.PlacedBudgetedFeature f : bm.getPlacedFeatures()) {
                int px = (int) sx(f.x()), py = (int) sy(f.z());
                g.fill(px - 3, py - 3, 7, 7, 0xFF000000);
                g.fill(px - 2, py - 2, 5, 5, 0xFFFF8A3C);
                if (labels) g.text(f.ruleId(), px + 6, py - 4, 0xFFFFD0A8);
            }
        }
        // spawn anchor (or plain origin)
        int ox = (int) sx(res.spawn != null ? res.spawn.originX() : 0), oz = (int) sy(res.spawn != null ? res.spawn.originZ() : 0);
        if (res.spawn != null && res.spawn.craterRadius() > 0) circle(g, res.spawn.originX(), res.spawn.originZ(), res.spawn.craterRadius(), 0xFFFFAA00);
        g.fill(ox - 5, oz, 11, 1, Ui.ACCENT);
        g.fill(ox, oz - 5, 1, 11, Ui.ACCENT);
        g.border(ox - 3, oz - 3, 7, 7, Ui.ACCENT);
        if (labels) g.text(res.spawn != null ? "spawn" : "origin", ox + 7, oz + 4, Ui.ACCENT);
    }

    private void circle(Gfx g, double cx, double cz, double r, int color) {
        double pr = r / scale;
        if (pr < 2 || pr > 4000) return;
        int n = (int) Math.max(24, Math.min(240, pr * 1.2));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            g.fill((int) (sx(cx) + Math.cos(a) * pr), (int) (sy(cz) + Math.sin(a) * pr), 1, 1, color);
        }
    }

    private static void line(Gfx g, double x0, double y0, double x1, double y1, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        int n = (int) Math.min(2000, Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            g.fill((int) (x0 + dx * t), (int) (y0 + dy * t), 2, 2, color);
        }
    }

    private void drawFooter(Gfx g, int mx, int my) {
        g.fill(x + 1, y + h - 14, w - 2, 13, 0xCC05060A);
        String info = String.format("scale %.1f blk/px   %s", scale, engine.busy() ? "updating..." : (frame != null ? frame.computeMillis + " ms" : ""));
        g.textMid(info, x + 5, y + h - 14, 13, Ui.TEXT_DIM);
        if (!lastWarnings.isEmpty()) {
            String wtxt = lastWarnings.size() + (lastWarnings.size() == 1 ? " warning" : " warnings");
            g.textRight(wtxt, x + w - 5, y + h - 14, 13, Ui.YELLOW);
        }
        // scale bar
        int barBlocks = scale > 40 ? 2000 : scale > 10 ? 500 : scale > 3 ? 100 : 25;
        int barPx = (int) (barBlocks / scale);
        g.fill(x + 8, y + h - 24, barPx, 2, 0xFFFFFFFF);
        g.text(barBlocks + " blocks", x + 8, y + h - 34, 0xFFFFFFFF);
    }

    private void updateHover(Gfx g, int mx, int my) {
        tooltip = null;
        if (!contains(mx, my) || frame == null || frame.compiled == null || frame.compiled.structure == null) return;
        if (Gfx.in(mx, my, home.x - 2, y, w, 60)) return; // over the buttons
        double wx = centerX + (mx - (x + w / 2.0)) * scale, wz = centerZ + (my - (y + h / 2.0)) * scale;
        WorldStructure s = frame.compiled.structure;
        ProjectCompiler.BiomeBinding b = frame.compiled.bindingById(s.getBiomeId((float) wx, (float) wz));
        tooltip = String.format("x %d  z %d   %s", (int) wx, (int) wz, b == null ? "void" : b.name());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        dragged = false;
        if (button == 1 && uiRoot() != null && !Gfx.in(mx, my, home.x - 2, y, w, 60)) {
            int wx = (int) Math.round(centerX + (mx - (x + w / 2.0)) * scale), wz = (int) Math.round(centerZ + (my - (y + h / 2.0)) * scale);
            List<PopupMenu.Item> items = new ArrayList<>();
            items.add(LiveWorld.inWorld()
                    ? PopupMenu.Item.of("Teleport here  (" + wx + ", " + wz + ")", () -> LiveWorld.teleport(app, wx, wz))
                    : PopupMenu.Item.disabled("Teleport here (open a world first)"));
            items.add(PopupMenu.Item.of("Center map here", () -> { centerX = wx; centerZ = wz; lastRequestKey = ""; }));
            items.add(PopupMenu.Item.of("Copy coordinates", () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(wx + " " + wz);
                app.say("Copied " + wx + " " + wz);
            }));
            PopupMenu.open(uiRoot(), (int) mx, (int) my, 170, items);
            return true;
        }
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        dragged = true;
        centerX -= dx * scale;
        centerZ -= dy * scale;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        zoom(dy > 0 ? 0.8 : 1.25);
        return true;
    }
}
