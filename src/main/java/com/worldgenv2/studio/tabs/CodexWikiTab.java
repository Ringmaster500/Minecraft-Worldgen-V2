package com.worldgenv2.studio.tabs;

import com.worldgenv2.studio.StudioNodeCatalog;
import com.worldgenv2.studio.StudioNodeCatalog.NodeSchema;
import com.worldgenv2.studio.StudioNodeCatalog.ParamDef;
import com.worldgenv2.studio.StudioNodeCatalog.SocketDef;
import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Built-In WorldGen Codex & In-Game Wiki Tab.
 * Provides searchable documentation for all ~295 AST node types across all 24 categories,
 * plus the built-in default biome presets, mathematical formulas, socket/parameter tables,
 * and inline 2D transfer curve plots.
 */
public class CodexWikiTab {

    public record WikiArticle(
        String id,
        String title,
        String category,
        String description,
        String formula,
        String socketsAndParams,
        String exampleJson,
        boolean isBiome
    ) {}

    private int x;
    private int y;
    private int width;
    private int height;

    private final List<WikiArticle> articles = new ArrayList<>();
    private final List<WikiArticle> filteredArticles = new ArrayList<>();
    private int selectedArticleIndex = 0;
    private String searchQuery = "";
    private int categoryIdx = 0;
    private static final String[] WIKI_CATEGORIES = {
        "All", "Biomes", "Density", "Curve", "Vector", "Material", "Pattern", "Mask", "Positions", "Scanner", "Assign", "Prop", "Graph", "World"
    };

    private int sidebarScroll = 0;
    private int contentScroll = 0;

    public CodexWikiTab() {
        populateWikiArticles();
        filterArticles();
    }

    private void populateWikiArticles() {
        // 1. Built-In Default Biome Reference Presets
        String[][] biomePresets = {
            {"alpine_peaks", "Alpine Peaks", "High elevation mountain biome with towering crags, sheer cliff faces, and perpetual snowfields above Y=220.", "D(x,y,z) = VerticalGrad(96, 40) + 1.8 * Simplex3D(0.008) + CellWall(0.012)"},
            {"autumn_forest", "Autumn Forest", "Temperate deciduous woodland with rolling hills and multi-colored podzol/dirt strata.", "D(x,y,z) = VerticalGrad(68, 24) + 0.6 * Simplex2D(0.005)"},
            {"badlands", "Badlands & Terraced Mesas", "Terraced arid biome with horizontal terracotta strata bands and steep canyon walls.", "D(x,y,z) = MesaPlateau(y) + StepCurve(Simplex2D(0.003))"},
            {"deep_caverns", "Deep Caverns & Swiss Cheese", "Subterranean cave labyrinth featuring large interconnected cavern chambers and narrow crawlways.", "D_cave = CellNoise3D(0.015, Distance2Sub) - 0.35"},
            {"volcanic_caldera", "Volcanic Caldera", "Basalt and blackstone volcanic peaks with lava basins and steep obsidian ridges.", "D(x,y,z) = CraterRingSDF(r=64) + RidgedSimplex3D(0.009)"},
            {"floating_skylands", "Floating Skylands", "Suspended archipelago of 3D islands using Ellipsoid SDFs combined with GradientWarp overhangs.", "D(x,y,z) = EllipsoidSDF(48, 20, 48) + GradientWarp(Simplex3D)"},
            {"lush_karst_pillars", "Lush Karst Pillars", "Towering limestone spires rising out of emerald water basins with hanging flora.", "D(x,y,z) = VerticalGrad(64, 32) + Pow(Abs(Simplex2D(0.012)), 2.5)"},
            {"riparian_riverbank", "Riparian Shoreline", "Smooth river channel and shoreline biome blending gravel, clay, and willow trees along water edges.", "D(x,y,z) = RiverSplineCarver(width=18, depth=8)"}
        };
        for (String[] b : biomePresets) {
            articles.add(new WikiArticle(
                "biome:" + b[0],
                b[1],
                "Biomes",
                b[2],
                b[3],
                "Sockets: Terrain (Density), Material (SpaceAndDepth), Props (AssignmentsProp)",
                "{\n  \"name\": \"worldgenv2:" + b[0] + "\",\n  \"type\": \"TileBiome\",\n  \"seaLevel\": 63\n}",
                true
            ));
        }

        // 2. Dynamically ingest ALL ~295 Node Schemas from StudioNodeCatalog!
        for (NodeSchema s : StudioNodeCatalog.getAllSchemas()) {
            StringBuilder sp = new StringBuilder();
            if (!s.inputs().isEmpty()) {
                sp.append("Inputs: ");
                for (int i = 0; i < s.inputs().size(); i++) {
                    SocketDef in = s.inputs().get(i);
                    if (i > 0) sp.append(", ");
                    sp.append(in.label()).append(" (").append(in.domain().label).append(")");
                }
                sp.append("  |  ");
            }
            if (!s.params().isEmpty()) {
                sp.append("Editable Fields: ");
                for (int i = 0; i < s.params().size(); i++) {
                    ParamDef p = s.params().get(i);
                    if (i > 0) sp.append(", ");
                    sp.append(p.label()).append("=").append(p.defaultValue());
                }
            } else if (sp.isEmpty()) {
                sp.append("Pure operator node (no inline scalar parameters).");
            }

            StringBuilder json = new StringBuilder("{\n  \"type\": \"").append(s.typeName()).append("\"");
            for (ParamDef p : s.params()) {
                json.append(",\n  \"").append(p.key()).append("\": ");
                if (p.type() == StudioNodeCatalog.ParamType.FLOAT || p.type() == StudioNodeCatalog.ParamType.INT || p.type() == StudioNodeCatalog.ParamType.BOOLEAN) {
                    json.append(p.defaultValue());
                } else {
                    json.append("\"").append(p.defaultValue()).append("\"");
                }
            }
            json.append("\n}");

            articles.add(new WikiArticle(
                s.id(),
                s.displayName(),
                s.category(),
                s.description(),
                s.formula(),
                sp.toString(),
                json.toString(),
                false
            ));
        }
    }

    private void filterArticles() {
        filteredArticles.clear();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        String cat = WIKI_CATEGORIES[categoryIdx];

        for (WikiArticle art : articles) {
            if (!"All".equals(cat) && !art.category().equalsIgnoreCase(cat)) {
                continue;
            }
            if (q.isEmpty()
                    || art.title().toLowerCase(Locale.ROOT).contains(q)
                    || art.description().toLowerCase(Locale.ROOT).contains(q)
                    || art.category().toLowerCase(Locale.ROOT).contains(q)
                    || art.formula().toLowerCase(Locale.ROOT).contains(q)) {
                filteredArticles.add(art);
            }
        }
        selectedArticleIndex = Math.min(selectedArticleIndex, Math.max(0, filteredArticles.size() - 1));
        sidebarScroll = 0;
        contentScroll = 0;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int sidebarW = Math.min(165, Math.max(130, width * 36 / 100));
        int sidebarX = x + 4;
        int sidebarY = y + 4;
        int sidebarH = height - 8;

        StudioTheme.drawBeveledPanel(graphics, sidebarX, sidebarY, sidebarW, sidebarH, 0xEE181822);

        // Search Bar
        StudioTheme.drawInsetWell(graphics, sidebarX + 5, sidebarY + 5, sidebarW - 10, 15, 0xFF101014);
        String searchDisplay = searchQuery.isEmpty() ? "§7Search " + articles.size() + " docs..." : searchQuery + "_";
        graphics.text(font, font.plainSubstrByWidth(searchDisplay, sidebarW - 18), sidebarX + 8, sidebarY + 9, StudioTheme.TEXT_WHITE, false);

        // Category Cycle Button: [< Cat: All >]
        int catY = sidebarY + 23;
        StudioTheme.drawBeveledPanel(graphics, sidebarX + 5, catY, sidebarW - 10, 14, 0xFF222230);
        String catText = "< Cat: " + WIKI_CATEGORIES[categoryIdx] + " (" + filteredArticles.size() + ") >";
        graphics.centeredText(font, font.plainSubstrByWidth(catText, sidebarW - 14), sidebarX + sidebarW / 2, catY + 3, StudioTheme.TEXT_CYAN);

        // Articles List Well
        int listY = catY + 17;
        int listH = sidebarH - (listY - sidebarY) - 5;
        StudioTheme.drawInsetWell(graphics, sidebarX + 5, listY, sidebarW - 10, listH, 0xFF121216);

        int itemH = 15;
        int maxVisible = Math.max(1, (listH - 4) / itemH);
        int maxScroll = Math.max(0, filteredArticles.size() - maxVisible);
        sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll));

        for (int i = 0; i < maxVisible; i++) {
            int idx = i + sidebarScroll;
            if (idx >= filteredArticles.size()) break;

            WikiArticle art = filteredArticles.get(idx);
            int itemY = listY + 2 + (i * itemH);
            boolean isHovered = mouseX >= sidebarX + 6 && mouseX < sidebarX + sidebarW - 6 && mouseY >= itemY && mouseY < itemY + itemH;
            boolean isSelected = (idx == selectedArticleIndex);

            if (isSelected) {
                graphics.fill(sidebarX + 6, itemY, sidebarX + sidebarW - 6, itemY + itemH, 0x45FFAA00);
            } else if (isHovered) {
                graphics.fill(sidebarX + 6, itemY, sidebarX + sidebarW - 6, itemY + itemH, 0x22FFFFFF);
            }

            int dotColor = art.isBiome() ? StudioTheme.TEXT_GOLD : StudioTheme.COLOR_DENSITY;
            graphics.fill(sidebarX + 9, itemY + 4, sidebarX + 14, itemY + 9, dotColor);

            int titleColor = isSelected ? StudioTheme.TEXT_GOLD : (isHovered ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE);
            String titleDisplay = font.plainSubstrByWidth(art.title(), sidebarW - 26);
            graphics.text(font, titleDisplay, sidebarX + 18, itemY + 4, titleColor, true);
        }

        // Right Pane: Scrollable Article Content inside Scissor
        int contentX = sidebarX + sidebarW + 6;
        int contentY = sidebarY;
        int contentW = width - (contentX - x) - 4;
        int contentH = sidebarH;

        StudioTheme.drawBeveledPanel(graphics, contentX, contentY, contentW, contentH, 0xF0181820);

        if (!filteredArticles.isEmpty() && selectedArticleIndex < filteredArticles.size()) {
            WikiArticle cur = filteredArticles.get(selectedArticleIndex);

            graphics.enableScissor(contentX + 2, contentY + 2, contentX + contentW - 2, contentY + contentH - 2);

            int textX = contentX + 10;
            int innerW = contentW - 20;
            int curY = contentY + 8 - contentScroll;

            // Title & Category
            String titleAndCat = font.plainSubstrByWidth(cur.title() + "  [" + cur.category() + "]", innerW);
            graphics.text(font, titleAndCat, textX, curY, StudioTheme.TEXT_GOLD, true);
            curY += 13;

            graphics.horizontalLine(textX, contentX + contentW - 10, curY, StudioTheme.PANEL_BORDER_LIGHT);
            curY += 6;

            // Description
            graphics.textWithWordWrap(font, Component.literal(cur.description()), textX, curY, innerW, StudioTheme.TEXT_WHITE);
            curY += 28;

            // Mathematical Formula Box
            graphics.text(font, "Formula / Behavior:", textX, curY, StudioTheme.TEXT_YELLOW, true);
            curY += 10;
            StudioTheme.drawInsetWell(graphics, textX, curY, innerW, 16, 0xFF101014);
            graphics.text(font, font.plainSubstrByWidth("§a" + cur.formula(), innerW - 10), textX + 5, curY + 4, 0xFF44FF44, false);
            curY += 20;

            // Sockets & Editable Parameters
            graphics.text(font, "Sockets & Editable Parameters:", textX, curY, StudioTheme.TEXT_YELLOW, true);
            curY += 10;
            graphics.textWithWordWrap(font, Component.literal("§7" + cur.socketsAndParams()), textX, curY, innerW, StudioTheme.TEXT_GRAY);
            curY += 22;

            // Inline Mini-Visual Plot
            int plotH = 36;
            renderMiniPlot(graphics, font, textX, curY, innerW, plotH, cur);
            curY += plotH + 6;

            // Example JSON Snippet
            graphics.text(font, "AST JSON Snippet:", textX, curY, StudioTheme.TEXT_YELLOW, true);
            curY += 10;
            StudioTheme.drawInsetWell(graphics, textX, curY, innerW, 44, 0xFF0D0D10);
            graphics.textWithWordWrap(font, Component.literal("§7" + cur.exampleJson()), textX + 5, curY + 4, innerW - 10, StudioTheme.TEXT_GRAY);

            graphics.disableScissor();
        }
    }

    private void renderMiniPlot(GuiGraphicsExtractor graphics, Font font, int px, int py, int pw, int ph, WikiArticle article) {
        StudioTheme.drawInsetWell(graphics, px, py, pw, ph, 0xFF0E0E12);
        int midY = py + (ph / 2);
        graphics.horizontalLine(px + 1, px + pw - 1, midY, 0xFF2A2A38);

        int prevX = px + 2;
        for (int i = 2; i < pw - 2; i += 2) {
            float t = (float) i / pw;
            float val;
            if (article.category().equalsIgnoreCase("Curve")) {
                val = (3 * t * t - 2 * t * t * t) * 2.0f - 1.0f;
            } else if (article.isBiome()) {
                val = (float) (Math.sin(t * 8.0) * 0.45 + Math.cos(t * 16.0) * 0.25);
            } else {
                val = (float) Math.sin(t * 12.0) * 0.75f;
            }

            int curX = px + i;
            int curPlotY = Math.max(py + 2, Math.min(py + ph - 2, midY - (int) (val * (ph / 2.4f))));
            graphics.horizontalLine(prevX, curX, curPlotY, StudioTheme.COLOR_CURVE);
            prevX = curX;
        }
        graphics.text(font, "Response Plot", px + 4, py + 3, StudioTheme.TEXT_DARK_GRAY, false);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        int sidebarW = Math.min(165, Math.max(130, width * 36 / 100));
        int sidebarX = x + 4;
        int sidebarY = y + 4;
        int sidebarH = height - 8;

        // Category Cycle Click
        int catY = sidebarY + 23;
        if (mx >= sidebarX + 5 && mx < sidebarX + sidebarW - 5 && my >= catY && my < catY + 14) {
            int dir = event.button() == 1 ? -1 : 1;
            categoryIdx = (categoryIdx + dir + WIKI_CATEGORIES.length) % WIKI_CATEGORIES.length;
            filterArticles();
            return true;
        }

        // Article Selection Click
        int listY = catY + 17;
        int listH = sidebarH - (listY - sidebarY) - 5;
        int itemH = 15;

        if (mx >= sidebarX + 5 && mx < sidebarX + sidebarW - 5 && my >= listY && my < listY + listH) {
            int clickedRow = (int) ((my - listY - 2) / itemH);
            int idx = clickedRow + sidebarScroll;
            if (idx >= 0 && idx < filteredArticles.size()) {
                this.selectedArticleIndex = idx;
                this.contentScroll = 0;
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int sidebarW = Math.min(165, Math.max(130, width * 36 / 100));
        int sidebarX = x + 4;

        if (mouseX >= sidebarX && mouseX < sidebarX + sidebarW) {
            int maxScroll = Math.max(0, filteredArticles.size() - 8);
            this.sidebarScroll = (int) Math.max(0, Math.min(maxScroll, sidebarScroll - scrollY * 2));
            return true;
        } else {
            this.contentScroll = (int) Math.max(0, Math.min(120, contentScroll - scrollY * 12));
            return true;
        }
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 259) { // Backspace
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                filterArticles();
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) {
            searchQuery += c;
            filterArticles();
            return true;
        }
        return false;
    }
}
