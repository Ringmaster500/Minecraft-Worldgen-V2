package com.worldgenv2.studio.gui;

import com.worldgenv2.minecraft.DefaultBiomeCatalog;
import com.worldgenv2.minecraft.DefaultBiomeCatalog.Entry;
import com.worldgenv2.minecraft.WorldHeightConfig.Preset;
import com.worldgenv2.studio.WorldGenStudioClient;
import com.worldgenv2.studio.projects.StudioProjectManager;
import com.worldgenv2.studio.projects.StudioProjectManager.ProjectEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimalist Fullscreen Studio Hub for WorldGen V2.
 * <p>
 * Clean, distraction-free canvas with a top navigation bar:
 * - Project/Workspace dropdown selector (default is active world)
 * - Dimensions tab: Left side pane listing dimensions; clicking expands into Biomes & Generation Zoning Nodes.
 * - World Settings tab: World height, total depth, and sea level with direct editable text boxes and autosave.
 */
public class WorldGenStudioScreen extends Screen {

    private static WorldGenStudioScreen instance;

    public static WorldGenStudioScreen getInstance() {
        if (instance == null) {
            instance = new WorldGenStudioScreen();
        }
        return instance;
    }

    public static void resetSession() {
        instance = null;
    }

    public enum StudioNavTab {
        DIMENSIONS,
        WORLD_SETTINGS
    }

    public enum DimensionSubSection {
        BIOMES,
        ZONING_NODES
    }

    public enum BiomeFilter {
        ALL,
        LAND,
        OCEANS,
        CAVES,
        RIVERS
    }

    public enum ActiveInputField {
        NONE,
        MIN_Y,
        GEN_DEPTH,
        SEA_LEVEL
    }

    public record DimensionEntry(String id, String displayName, String icon) {}

    // Navigation tab state
    private StudioNavTab activeTab = StudioNavTab.DIMENSIONS;

    // Active project state
    private ProjectEntry activeProject = null;

    // Dropdown state
    private boolean projectDropdownOpen = false;

    // Creation modal state
    private boolean showNewProjectModal = false;
    private String newProjectName = "";

    // Dimensions Side Pane state
    private String selectedDimensionId = "minecraft:overworld";
    private DimensionSubSection activeSubSection = DimensionSubSection.BIOMES;
    private BiomeFilter activeBiomeFilter = BiomeFilter.ALL;
    private int biomeScrollOffset = 0;

    // World Height Settings state
    private Preset heightPreset = Preset.STANDARD;
    private int currentMinY = -64;
    private int currentGenDepth = 384;
    private int currentSeaLevel = 63;

    // Direct text box editing state
    private ActiveInputField activeField = ActiveInputField.NONE;
    private String minYText = "-64";
    private String genDepthText = "384";
    private String seaLevelText = "63";

    // Sleek minimal dark theme palette
    private static final int CANVAS_BG       = 0xFF0D0E12; // Pitch blank canvas
    private static final int TOPBAR_BG       = 0xFF12141A; // Clean dark bar
    private static final int SIDEBAR_BG      = 0xFF101217; // Elevated left side pane
    private static final int BORDER_COLOR    = 0xFF1F222C; // Subtle hairline
    private static final int BUTTON_BG       = 0xFF171A23; // Sleek button
    private static final int BUTTON_HOVER    = 0xFF212532; // Hovered button
    private static final int BUTTON_BORDER   = 0xFF292E3E; // Button outline
    private static final int POPUP_BG        = 0xFF151720; // Dropdown panel
    private static final int POPUP_BORDER    = 0xFF2D3244; // Dropdown border
    private static final int PANEL_BG        = 0xFF13151D; // Settings container
    private static final int TEXT_PRIMARY    = 0xFFE4E7ED; // Crisp text
    private static final int TEXT_MUTED      = 0xFF838896; // Muted label
    private static final int ACCENT_COLOR    = 0xFFFFAA00; // Gold accent
    private static final int ACCENT_BLUE     = 0xFF4FA3E3; // Blue accent

    public WorldGenStudioScreen() {
        super(Component.literal("WorldGen Studio"));
    }

    @Override
    protected void init() {
        super.init();
        if (activeProject == null) {
            activeProject = StudioProjectManager.createCurrentWorldProject();
            StudioProjectManager.setActiveProject(activeProject);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // 1. Clean Blank Canvas
        graphics.fill(0, 0, width, height, CANVAS_BG);

        // 2. Top Navigation Bar
        int barH = 32;
        graphics.fill(0, 0, width, barH, TOPBAR_BG);
        graphics.horizontalLine(0, width, barH - 1, BORDER_COLOR);

        // 3. Workspace Dropdown Selector Button (Left)
        String worldName = (activeProject != null) ? activeProject.name() : "Current World";
        String displayLabel = (activeProject != null && !activeProject.isCurrentWorld())
                ? "📁 " + worldName + "  ▾"
                : "🌍 " + worldName + "  ▾";

        int btnX = 10;
        int btnY = 5;
        int btnH = 22;
        int btnW = font.width(displayLabel) + 16;

        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, (projectDropdownOpen || btnHov) ? BUTTON_HOVER : BUTTON_BG);
        drawHollowRect(graphics, btnX, btnY, btnW, btnH, (projectDropdownOpen || btnHov) ? 0xFF3D445C : BUTTON_BORDER);
        graphics.text(font, displayLabel, btnX + 8, btnY + 7, TEXT_PRIMARY, false);

        // 4. Divider between Workspace Selector and Navigation Tabs
        int tabDividerX = btnX + btnW + 12;
        graphics.verticalLine(tabDividerX, 8, 24, BORDER_COLOR);

        // 5. Navigation Tabs: Dimensions & World Settings
        int tabStartX = tabDividerX + 12;

        // Tab A: Dimensions
        int dimTabW = font.width("Dimensions") + 16;
        int dimTabX = tabStartX;
        boolean dimActive = (activeTab == StudioNavTab.DIMENSIONS);
        boolean dimHov = mouseX >= dimTabX && mouseX <= dimTabX + dimTabW && mouseY >= btnY && mouseY <= btnY + btnH;

        if (dimActive) {
            graphics.fill(dimTabX, btnY, dimTabX + dimTabW, btnY + btnH, 0xFF191C26);
            drawHollowRect(graphics, dimTabX, btnY, dimTabW, btnH, 0xFF2A2F40);
            graphics.horizontalLine(dimTabX, dimTabX + dimTabW - 1, barH - 1, ACCENT_COLOR);
        } else if (dimHov) {
            graphics.fill(dimTabX, btnY, dimTabX + dimTabW, btnY + btnH, 0xFF161922);
        }
        graphics.centeredText(font, "Dimensions", dimTabX + dimTabW / 2, btnY + 7, dimActive ? TEXT_PRIMARY : (dimHov ? 0xFFCAD0DE : TEXT_MUTED));

        // Tab B: World Settings
        int wsTabW = font.width("World Settings") + 16;
        int wsTabX = dimTabX + dimTabW + 4;
        boolean wsActive = (activeTab == StudioNavTab.WORLD_SETTINGS);
        boolean wsHov = mouseX >= wsTabX && mouseX <= wsTabX + wsTabW && mouseY >= btnY && mouseY <= btnY + btnH;

        if (wsActive) {
            graphics.fill(wsTabX, btnY, wsTabX + wsTabW, btnY + btnH, 0xFF191C26);
            drawHollowRect(graphics, wsTabX, btnY, wsTabW, btnH, 0xFF2A2F40);
            graphics.horizontalLine(wsTabX, wsTabX + wsTabW - 1, barH - 1, ACCENT_COLOR);
        } else if (wsHov) {
            graphics.fill(wsTabX, btnY, wsTabX + wsTabW, btnY + btnH, 0xFF161922);
        }
        graphics.centeredText(font, "World Settings", wsTabX + wsTabW / 2, btnY + 7, wsActive ? TEXT_PRIMARY : (wsHov ? 0xFFCAD0DE : TEXT_MUTED));

        // 6. Close Button (Right)
        int closeW = 22;
        int closeX = width - 10 - closeW;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(closeX, btnY, closeX + closeW, btnY + btnH, closeHov ? 0xFF331A1A : BUTTON_BG);
        drawHollowRect(graphics, closeX, btnY, closeW, btnH, closeHov ? 0xFFFF5555 : BUTTON_BORDER);
        graphics.centeredText(font, "✕", closeX + closeW / 2, btnY + 6, closeHov ? 0xFFFF6666 : TEXT_MUTED);

        // 7. Render Active Tab Content
        if (activeTab == StudioNavTab.DIMENSIONS) {
            renderDimensionsView(graphics, mouseX, mouseY);
        } else if (activeTab == StudioNavTab.WORLD_SETTINGS) {
            renderWorldSettings(graphics, mouseX, mouseY);
        }

        // 8. Project Dropdown Menu Overlay
        if (projectDropdownOpen) {
            renderProjectDropdown(graphics, mouseX, mouseY, btnX, btnY + btnH + 3, btnW);
        }

        // 9. New Project Modal Dialog
        if (showNewProjectModal) {
            renderNewProjectModal(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private List<DimensionEntry> getDimensionEntries() {
        List<DimensionEntry> list = new ArrayList<>();
        list.add(new DimensionEntry("minecraft:overworld", "Overworld", ""));
        list.add(new DimensionEntry("minecraft:the_nether", "The Nether", ""));
        list.add(new DimensionEntry("minecraft:the_end", "The End", ""));
        return list;
    }

    private void renderDimensionsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int sideW = 126;
        int topBarH = 32;

        // 1. Left Dimensions Side Pane
        graphics.fill(0, topBarH, sideW, height, SIDEBAR_BG);
        graphics.verticalLine(sideW - 1, topBarH, height, BORDER_COLOR);

        // Header
        graphics.text(font, "DIMENSIONS", 10, topBarH + 9, 0xFF585D6C, false);

        int curY = topBarH + 22;
        List<DimensionEntry> dims = getDimensionEntries();

        for (DimensionEntry dim : dims) {
            boolean isExpanded = dim.id().equals(selectedDimensionId);
            boolean hovDim = mouseX >= 6 && mouseX <= sideW - 6 && mouseY >= curY && mouseY <= curY + 18;

            if (isExpanded) {
                graphics.fill(6, curY, sideW - 6, curY + 18, 0xFF181B24);
                drawHollowRect(graphics, 6, curY, sideW - 12, 18, 0xFF272C3A);
                graphics.fill(6, curY, 8, curY + 18, ACCENT_COLOR);
            } else if (hovDim) {
                graphics.fill(6, curY, sideW - 6, curY + 18, 0xFF14161F);
            }

            graphics.text(font, dim.displayName(), 12, curY + 5, isExpanded ? TEXT_PRIMARY : (hovDim ? 0xFFCCD1DF : TEXT_MUTED), false);
            graphics.text(font, isExpanded ? "▾" : "▸", sideW - 14, curY + 5, isExpanded ? ACCENT_COLOR : 0xFF505566, false);

            curY += 21;

            // Expanded Sub-Items
            if (isExpanded) {
                // Sub-Item 1: Biomes
                boolean hovBiomes = mouseX >= 12 && mouseX <= sideW - 8 && mouseY >= curY && mouseY <= curY + 16;
                boolean isBiomes = (activeSubSection == DimensionSubSection.BIOMES);

                if (isBiomes) {
                    graphics.fill(12, curY, sideW - 8, curY + 16, 0xFF1E2330);
                    drawHollowRect(graphics, 12, curY, sideW - 20, 16, 0xFF303A4E);
                } else if (hovBiomes) {
                    graphics.fill(12, curY, sideW - 8, curY + 16, 0xFF151821);
                }
                graphics.text(font, "Biomes", 18, curY + 4, isBiomes ? ACCENT_COLOR : (hovBiomes ? TEXT_PRIMARY : TEXT_MUTED), false);
                curY += 19;

                // Sub-Item 2: Zoning Nodes
                boolean hovNodes = mouseX >= 12 && mouseX <= sideW - 8 && mouseY >= curY && mouseY <= curY + 16;
                boolean isNodes = (activeSubSection == DimensionSubSection.ZONING_NODES);

                if (isNodes) {
                    graphics.fill(12, curY, sideW - 8, curY + 16, 0xFF1E2330);
                    drawHollowRect(graphics, 12, curY, sideW - 20, 16, 0xFF303A4E);
                } else if (hovNodes) {
                    graphics.fill(12, curY, sideW - 8, curY + 16, 0xFF151821);
                }
                graphics.text(font, "Zoning Nodes", 18, curY + 4, isNodes ? ACCENT_COLOR : (hovNodes ? TEXT_PRIMARY : TEXT_MUTED), false);
                curY += 22;
            }
        }

        // 2. Main Right Canvas Area
        int mainX = sideW + 14;
        int mainW = width - sideW - 24;
        int mainY = topBarH + 10;

        String dimName = "Overworld";
        for (DimensionEntry d : dims) {
            if (d.id().equals(selectedDimensionId)) {
                dimName = d.displayName();
                break;
            }
        }

        // Breadcrumb Title Header
        String subName = (activeSubSection == DimensionSubSection.BIOMES) ? "Biomes" : "Generation Zoning Nodes";
        graphics.text(font, dimName, mainX, mainY, TEXT_MUTED, false);
        graphics.text(font, "➔", mainX + font.width(dimName) + 5, mainY, 0xFF525767, false);
        graphics.text(font, subName, mainX + font.width(dimName) + 16, mainY, TEXT_PRIMARY, true);

        graphics.horizontalLine(mainX, mainX + mainW, mainY + 12, BORDER_COLOR);

        if (activeSubSection == DimensionSubSection.BIOMES) {
            renderBiomesSubSection(graphics, mouseX, mouseY, mainX, mainY + 18, mainW);
        } else {
            renderZoningNodesSubSection(graphics, mouseX, mouseY, mainX, mainY + 18, mainW);
        }
    }

    private void renderBiomesSubSection(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        // Filter pills row
        int filterY = y;
        int pillH = 15;
        int pillX = x;

        BiomeFilter[] filters = BiomeFilter.values();
        String[] filterNames = { "All (39)", "Land (18)", "Oceans (8)", "Caves (5)", "Rivers (2)" };

        for (int i = 0; i < filters.length; i++) {
            int pw = font.width(filterNames[i]) + 10;
            boolean isSel = (activeBiomeFilter == filters[i]);
            boolean hov = mouseX >= pillX && mouseX <= pillX + pw && mouseY >= filterY && mouseY <= filterY + pillH;

            graphics.fill(pillX, filterY, pillX + pw, filterY + pillH, isSel ? 0xFF222838 : (hov ? 0xFF171A24 : 0xFF11131A));
            drawHollowRect(graphics, pillX, filterY, pw, pillH, isSel ? ACCENT_COLOR : (hov ? 0xFF353B4E : BUTTON_BORDER));
            graphics.centeredText(font, filterNames[i], pillX + pw / 2, filterY + 4, isSel ? ACCENT_COLOR : (hov ? TEXT_PRIMARY : TEXT_MUTED));

            pillX += pw + 5;
        }

        // Biome Cards Grid
        int listY = filterY + pillH + 10;
        List<Entry> allBiomes = DefaultBiomeCatalog.entries();
        List<Entry> filtered = new ArrayList<>();

        for (Entry b : allBiomes) {
            if (activeBiomeFilter == BiomeFilter.LAND && (b.kind() != DefaultBiomeCatalog.Kind.TILE || !b.land())) continue;
            if (activeBiomeFilter == BiomeFilter.OCEANS && (b.kind() != DefaultBiomeCatalog.Kind.TILE || b.land())) continue;
            if (activeBiomeFilter == BiomeFilter.CAVES && b.kind() != DefaultBiomeCatalog.Kind.UNDERGROUND) continue;
            if (activeBiomeFilter == BiomeFilter.RIVERS && b.kind() != DefaultBiomeCatalog.Kind.RIVER) continue;
            filtered.add(b);
        }

        int cols = (w > 520) ? 3 : 2;
        int cardGap = 6;
        int cardW = (w - (cardGap * (cols - 1))) / cols;
        int cardH = 32;

        int row = 0;
        int col = 0;

        for (Entry b : filtered) {
            int cx = x + col * (cardW + cardGap);
            int cy = listY + row * (cardH + cardGap) - biomeScrollOffset;

            if (cy + cardH >= listY && cy <= height - 8) {
                boolean hov = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                graphics.fill(cx, cy, cx + cardW, cy + cardH, hov ? 0xFF171922 : PANEL_BG);
                drawHollowRect(graphics, cx, cy, cardW, cardH, hov ? 0xFF32384B : BORDER_COLOR);

                String formattedName = formatCamelCase(b.name());
                graphics.text(font, formattedName, cx + 8, cy + 6, TEXT_PRIMARY, false);

                String vanillaId = (b.vanilla() != null) ? b.vanilla().identifier().getPath() : "procedural";
                graphics.text(font, vanillaId, cx + 8, cy + 18, 0xFF606675, false);

                String kindLabel = switch (b.kind()) {
                    case TILE -> b.land() ? "Land" : "Ocean";
                    case UNDERGROUND -> "3D Cave";
                    case RIVER -> "River";
                    case CUSTOM -> "Overlay";
                };
                int badgeColor = switch (b.kind()) {
                    case TILE -> b.land() ? 0xFF5FA85F : 0xFF3FA3FF;
                    case UNDERGROUND -> 0xFFB37BE8;
                    case RIVER -> 0xFF3FC5FF;
                    case CUSTOM -> 0xFFE89A3C;
                };

                int bw = font.width(kindLabel) + 8;
                int bx = cx + cardW - bw - 6;
                int by = cy + 6;
                graphics.fill(bx, by, bx + bw, by + 14, 0xFF13151D);
                drawHollowRect(graphics, bx, by, bw, 14, 0xFF242835);
                graphics.centeredText(font, kindLabel, bx + bw / 2, by + 3, badgeColor);
            }

            col++;
            if (col >= cols) {
                col = 0;
                row++;
            }
        }
    }

    private void renderZoningNodesSubSection(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        record NodeEntry(String title, String type, String desc, int badgeColor) {}

        List<NodeEntry> nodes = List.of(
                new NodeEntry("Macro Continent Zoning (BiCarta)", "Cellular Voronoi", "Allocates continental landmasses, ocean boundaries, and macro climate regions.", 0xFF5FA85F),
                new NodeEntry("Elevation Spline & Coastal Curve", "Density Spline", "Translates continental distance into vertical base terrain elevation and coastlines.", 0xFFFFAA00),
                new NodeEntry("Erosion & Peaks / Valleys", "Octave Perlin Noise", "Generates high mountain ridges, rolling plateaus, and low valleys across land cells.", 0xFFE89A3C),
                new NodeEntry("Aquatic River Carvers", "Ridge Voronoi Curves", "Carves natural winding river valleys connected across continental land to oceans.", 0xFF3FA3FF),
                new NodeEntry("3D Subterranean Cave Carvers", "3D Simplex Graph", "Carves volumetric cave rooms, noodle passages, and interconnected cavern chambers.", 0xFFB37BE8),
                new NodeEntry("3D Strata & Material Assignment", "Depth Material Resolver", "Synthesizes geological block strata, surface soil, sand, stone, and bedrock layers.", 0xFF4FA3E3)
        );

        int cy = y + 2;
        int cardH = 34;

        for (NodeEntry node : nodes) {
            if (cy + cardH > height - 8) break;

            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= cy && mouseY <= cy + cardH;
            graphics.fill(x, cy, x + w, cy + cardH, hov ? 0xFF171922 : PANEL_BG);
            drawHollowRect(graphics, x, cy, w, cardH, hov ? 0xFF353C50 : BORDER_COLOR);

            graphics.text(font, node.title(), x + 10, cy + 6, TEXT_PRIMARY, false);
            graphics.text(font, node.desc(), x + 10, cy + 18, 0xFF656B7A, false);

            int bw = font.width(node.type()) + 10;
            int bx = x + w - bw - 10;
            int by = cy + 6;
            graphics.fill(bx, by, bx + bw, by + 14, 0xFF13151D);
            drawHollowRect(graphics, bx, by, bw, 14, 0xFF242835);
            graphics.centeredText(font, node.type(), bx + bw / 2, by + 3, node.badgeColor());

            cy += cardH + 6;
        }
    }

    private String formatCamelCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void renderWorldSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelW = Math.min(540, width - 40);
        int panelX = width / 2 - panelW / 2;
        int startY = 48;

        // Section Title
        graphics.text(font, "WORLD HEIGHT & VERTICAL SCALE", panelX, startY, TEXT_PRIMARY, true);
        graphics.text(font, "Configure volumetric coordinate limits, total block depth, and water sea level.", panelX, startY + 12, TEXT_MUTED, false);

        int currentY = startY + 30;

        // Preset Pills
        Preset[] presets = { Preset.STANDARD, Preset.HYTALE_512, Preset.HYTALE_EXTENDED, Preset.EPIC_1024, Preset.CUSTOM };
        String[] presetNames = { "Standard (384)", "Canonical 512", "Extended 576", "Epic 1024", "Custom" };

        int pillGap = 6;
        int pillW = (panelW - (pillGap * (presets.length - 1))) / presets.length;
        int pillH = 22;

        for (int i = 0; i < presets.length; i++) {
            int px = panelX + i * (pillW + pillGap);
            boolean isSel = (heightPreset == presets[i]);
            boolean hov = mouseX >= px && mouseX <= px + pillW && mouseY >= currentY && mouseY <= currentY + pillH;

            graphics.fill(px, currentY, px + pillW, currentY + pillH, isSel ? 0xFF242A3C : (hov ? 0xFF1C1F2B : 0xFF14161F));
            drawHollowRect(graphics, px, currentY, pillW, pillH, isSel ? ACCENT_COLOR : (hov ? 0xFF3B4156 : 0xFF242735));
            graphics.centeredText(font, presetNames[i], px + pillW / 2, currentY + 7, isSel ? ACCENT_COLOR : (hov ? TEXT_PRIMARY : TEXT_MUTED));
        }

        currentY += pillH + 16;

        // Properties Card
        int cardH = 136;
        graphics.fill(panelX, currentY, panelX + panelW, currentY + cardH, PANEL_BG);
        drawHollowRect(graphics, panelX, currentY, panelW, cardH, BORDER_COLOR);

        int rowY = currentY + 14;

        // Row 1: Min Y (Floor)
        renderSettingInputRow(graphics, mouseX, mouseY, panelX + 16, rowY, panelW - 32,
                "Min Y (Bedrock Floor)",
                "Lowest coordinate for volumetric generation (multiples of 16)",
                minYText, activeField == ActiveInputField.MIN_Y);

        rowY += 38;

        // Row 2: Total Gen Depth / Ceiling
        int maxY = currentMinY + currentGenDepth;
        renderSettingInputRow(graphics, mouseX, mouseY, panelX + 16, rowY, panelW - 32,
                "Total World Depth",
                "Total vertical blocks (Ceiling: Y = " + maxY + ")",
                genDepthText, activeField == ActiveInputField.GEN_DEPTH);

        rowY += 38;

        // Row 3: Sea Level
        renderSettingInputRow(graphics, mouseX, mouseY, panelX + 16, rowY, panelW - 32,
                "Sea Level",
                "Altitude coordinate for oceans and rivers",
                seaLevelText, activeField == ActiveInputField.SEA_LEVEL);

        // Visual Proportion Bar
        int barY = currentY + cardH + 16;
        int barH = 18;
        graphics.fill(panelX, barY, panelX + panelW, barY + barH, 0xFF101218);
        drawHollowRect(graphics, panelX, barY, panelW, barH, BORDER_COLOR);

        float underFrac = Math.max(0f, Math.min(1f, (float) (currentSeaLevel - currentMinY) / (float) Math.max(1, currentGenDepth)));
        int underW = (int) ((panelW - 2) * underFrac);

        graphics.fill(panelX + 1, barY + 1, panelX + 1 + underW, barY + barH - 1, 0xFF1C2C3D);
        graphics.fill(panelX + 1 + underW, barY + 1, panelX + panelW - 1, barY + barH - 1, 0xFF141F2B);
        if (underW > 0 && underW < panelW - 2) {
            graphics.verticalLine(panelX + 1 + underW, barY + 1, barY + barH - 1, 0xFF3FA3FF);
        }

        graphics.text(font, "Bedrock: " + currentMinY, panelX + 6, barY + 5, 0xFF838896, false);
        graphics.centeredText(font, "Sea Level: " + currentSeaLevel, panelX + panelW / 2, barY + 5, 0xFF3FA3FF);
        graphics.text(font, "Ceiling: " + maxY, panelX + panelW - font.width("Ceiling: " + maxY) - 6, barY + 5, 0xFF838896, false);
    }

    private void renderSettingInputRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w,
                                       String title, String subtitle, String textValue, boolean isFocused) {
        graphics.text(font, title, x, y + 2, TEXT_PRIMARY, false);
        graphics.text(font, subtitle, x, y + 14, 0xFF656A7A, false);

        int boxW = 84;
        int boxH = 22;
        int boxX = x + w - boxW;
        int boxY = y + 2;

        boolean hov = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, isFocused ? 0xFF1B1E29 : 0xFF0D0E13);
        drawHollowRect(graphics, boxX, boxY, boxW, boxH, isFocused ? ACCENT_COLOR : (hov ? 0xFF3D445C : BUTTON_BORDER));

        String display = textValue + (isFocused ? "_" : "");
        int valColor = isFocused ? ACCENT_COLOR : TEXT_PRIMARY;
        graphics.centeredText(font, display, boxX + boxW / 2, boxY + 7, valColor);
    }

    private void commitInputFields() {
        try {
            int parsedMinY = Integer.parseInt(minYText.trim());
            currentMinY = Math.max(-2048, Math.min(0, parsedMinY));
            minYText = String.valueOf(currentMinY);
        } catch (NumberFormatException ignored) {
            minYText = String.valueOf(currentMinY);
        }

        try {
            int parsedDepth = Integer.parseInt(genDepthText.trim());
            currentGenDepth = Math.max(64, Math.min(4096, parsedDepth));
            genDepthText = String.valueOf(currentGenDepth);
        } catch (NumberFormatException ignored) {
            genDepthText = String.valueOf(currentGenDepth);
        }

        try {
            int parsedSea = Integer.parseInt(seaLevelText.trim());
            currentSeaLevel = Math.max(currentMinY, Math.min(currentMinY + currentGenDepth, parsedSea));
            seaLevelText = String.valueOf(currentSeaLevel);
        } catch (NumberFormatException ignored) {
            seaLevelText = String.valueOf(currentSeaLevel);
        }

        heightPreset = Preset.CUSTOM;
    }

    private void renderProjectDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int startX, int startY, int minWidth) {
        List<ProjectEntry> projects = StudioProjectManager.listProjects();
        int itemH = 22;
        int totalItems = 1 + projects.size() + 1;
        int menuW = Math.max(minWidth + 30, 220);
        int menuH = (totalItems * itemH) + 6;

        graphics.fill(startX + 2, startY + 2, startX + menuW + 2, startY + menuH + 2, 0x55000000);
        graphics.fill(startX, startY, startX + menuW, startY + menuH, POPUP_BG);
        drawHollowRect(graphics, startX, startY, menuW, menuH, POPUP_BORDER);

        int currentY = startY + 2;

        String currentWorldName = (Minecraft.getInstance().getSingleplayerServer() != null)
                ? Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName()
                : "Current World";
        boolean isCurrentWorldActive = (activeProject != null && activeProject.isCurrentWorld());
        boolean hovWorld = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;

        if (hovWorld) {
            graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
        }
        graphics.text(font, "🌍 " + currentWorldName, startX + 8, currentY + 6, isCurrentWorldActive ? ACCENT_COLOR : TEXT_PRIMARY, false);
        if (isCurrentWorldActive) {
            graphics.text(font, "✓", startX + menuW - 16, currentY + 6, ACCENT_COLOR, false);
        }
        currentY += itemH;

        for (ProjectEntry p : projects) {
            boolean isThisActive = (activeProject != null && !activeProject.isCurrentWorld() && p.name().equals(activeProject.name()));
            boolean hovProj = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;

            if (hovProj) {
                graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
            }
            graphics.text(font, "📁 " + p.name(), startX + 8, currentY + 6, isThisActive ? ACCENT_COLOR : TEXT_PRIMARY, false);
            if (isThisActive) {
                graphics.text(font, "✓", startX + menuW - 16, currentY + 6, ACCENT_COLOR, false);
            }
            currentY += itemH;
        }

        graphics.horizontalLine(startX + 6, startX + menuW - 6, currentY + 1, 0xFF252937);
        currentY += 3;

        boolean hovNew = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;
        if (hovNew) {
            graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
        }
        graphics.text(font, "+ New Project...", startX + 8, currentY + 6, hovNew ? 0xFF70BFFF : ACCENT_BLUE, false);
    }

    private void renderNewProjectModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0x88000000);

        int modalW = 280;
        int modalH = 96;
        int modalX = width / 2 - modalW / 2;
        int modalY = height / 2 - modalH / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF14161F);
        drawHollowRect(graphics, modalX, modalY, modalW, modalH, POPUP_BORDER);

        graphics.text(font, "New Project Workspace", modalX + 14, modalY + 12, TEXT_PRIMARY, true);

        int inputX = modalX + 14;
        int inputY = modalY + 30;
        int inputW = modalW - 28;
        int inputH = 22;

        graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, 0xFF0D0E13);
        drawHollowRect(graphics, inputX, inputY, inputW, inputH, 0xFF2B2F3E);

        String display = newProjectName.isEmpty() ? "Enter project name..." : newProjectName;
        int txtColor = newProjectName.isEmpty() ? 0xFF525768 : TEXT_PRIMARY;
        graphics.text(font, display + (newProjectName.isEmpty() ? "" : "_"), inputX + 6, inputY + 6, txtColor, false);

        int btnW = 68;
        int btnH = 20;
        int btnY = modalY + 62;

        int createX = modalX + modalW - 14 - btnW;
        boolean createHov = mouseX >= createX && mouseX <= createX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(createX, btnY, createX + btnW, btnY + btnH, createHov ? 0xFF00B4D8 : 0xFF0077B6);
        graphics.centeredText(font, "Create", createX + btnW / 2, btnY + 5, TEXT_PRIMARY);

        int cancelX = createX - btnW - 6;
        boolean cancelHov = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelHov ? 0xFF262A37 : 0xFF1A1C24);
        drawHollowRect(graphics, cancelX, btnY, btnW, btnH, BUTTON_BORDER);
        graphics.centeredText(font, "Cancel", cancelX + btnW / 2, btnY + 5, TEXT_MUTED);
    }

    private void drawHollowRect(GuiGraphicsExtractor graphics, int rx, int ry, int rw, int rh, int color) {
        graphics.horizontalLine(rx, rx + rw - 1, ry, color);
        graphics.horizontalLine(rx, rx + rw - 1, ry + rh - 1, color);
        graphics.verticalLine(rx, ry, ry + rh - 1, color);
        graphics.verticalLine(rx + rw - 1, ry, ry + rh - 1, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 1. Modal clicks intercept everything
        if (showNewProjectModal) {
            int modalW = 280;
            int modalH = 96;
            int modalX = width / 2 - modalW / 2;
            int modalY = height / 2 - modalH / 2;

            int btnW = 68;
            int btnH = 20;
            int btnY = modalY + 62;
            int createX = modalX + modalW - 14 - btnW;
            int cancelX = createX - btnW - 6;

            if (event.x() >= createX && event.x() <= createX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                if (!newProjectName.isBlank()) {
                    activeProject = StudioProjectManager.createNewProject(newProjectName);
                    StudioProjectManager.setActiveProject(activeProject);
                    showNewProjectModal = false;
                }
                return true;
            }

            if (event.x() >= cancelX && event.x() <= cancelX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                showNewProjectModal = false;
                return true;
            }

            if (event.x() < modalX || event.x() > modalX + modalW || event.y() < modalY || event.y() > modalY + modalH) {
                showNewProjectModal = false;
                return true;
            }
            return true;
        }

        // 2. Dropdown menu clicks
        String worldName = (activeProject != null) ? activeProject.name() : "Current World";
        String displayLabel = (activeProject != null && !activeProject.isCurrentWorld())
                ? "📁 " + worldName + "  ▾"
                : "🌍 " + worldName + "  ▾";
        int btnX = 10;
        int btnY = 5;
        int btnH = 22;
        int btnW = font.width(displayLabel) + 16;

        if (projectDropdownOpen) {
            int startX = btnX;
            int startY = btnY + btnH + 3;
            List<ProjectEntry> projects = StudioProjectManager.listProjects();
            int itemH = 22;
            int menuW = Math.max(btnW + 30, 220);
            int menuH = ((1 + projects.size() + 1) * itemH) + 6;

            if (event.x() >= startX && event.x() <= startX + menuW && event.y() >= startY && event.y() <= startY + menuH) {
                int clickedY = (int) event.y() - startY - 2;
                int clickedIdx = clickedY / itemH;

                if (clickedIdx == 0) {
                    activeProject = StudioProjectManager.createCurrentWorldProject();
                    StudioProjectManager.setActiveProject(activeProject);
                    projectDropdownOpen = false;
                    return true;
                } else if (clickedIdx >= 1 && clickedIdx <= projects.size()) {
                    activeProject = projects.get(clickedIdx - 1);
                    StudioProjectManager.setActiveProject(activeProject);
                    projectDropdownOpen = false;
                    return true;
                } else {
                    projectDropdownOpen = false;
                    newProjectName = "Pack_" + (projects.size() + 1);
                    showNewProjectModal = true;
                    return true;
                }
            } else {
                projectDropdownOpen = false;
                if (event.x() >= btnX && event.x() <= btnX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                    return true;
                }
            }
        }

        // 3. Dropdown toggle button click
        if (event.x() >= btnX && event.x() <= btnX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
            projectDropdownOpen = !projectDropdownOpen;
            return true;
        }

        // 4. Navigation Tab Clicks
        int tabDividerX = btnX + btnW + 12;
        int tabStartX = tabDividerX + 12;

        int dimTabW = font.width("Dimensions") + 16;
        int dimTabX = tabStartX;
        if (event.x() >= dimTabX && event.x() <= dimTabX + dimTabW && event.y() >= btnY && event.y() <= btnY + btnH) {
            if (activeField != ActiveInputField.NONE) {
                commitInputFields();
                activeField = ActiveInputField.NONE;
            }
            activeTab = StudioNavTab.DIMENSIONS;
            return true;
        }

        int wsTabW = font.width("World Settings") + 16;
        int wsTabX = dimTabX + dimTabW + 4;
        if (event.x() >= wsTabX && event.x() <= wsTabX + wsTabW && event.y() >= btnY && event.y() <= btnY + btnH) {
            activeTab = StudioNavTab.WORLD_SETTINGS;
            return true;
        }

        // 5. Close button click
        int closeW = 22;
        int closeX = width - 10 - closeW;
        if (event.x() >= closeX && event.x() <= closeX + closeW && event.y() >= btnY && event.y() <= btnY + btnH) {
            if (activeField != ActiveInputField.NONE) {
                commitInputFields();
            }
            this.onClose();
            return true;
        }

        // 6. Dimensions Tab Clicks
        if (activeTab == StudioNavTab.DIMENSIONS) {
            int sideW = 126;
            int topBarH = 32;

            // Check Left Side Pane Clicks
            if (event.x() <= sideW) {
                int curY = topBarH + 22;
                List<DimensionEntry> dims = getDimensionEntries();

                for (DimensionEntry dim : dims) {
                    boolean isExpanded = dim.id().equals(selectedDimensionId);

                    // Clicked on dimension header
                    if (event.x() >= 6 && event.x() <= sideW - 6 && event.y() >= curY && event.y() <= curY + 18) {
                        selectedDimensionId = dim.id();
                        biomeScrollOffset = 0;
                        return true;
                    }
                    curY += 21;

                    if (isExpanded) {
                        // Clicked Biomes sub-item
                        if (event.x() >= 12 && event.x() <= sideW - 8 && event.y() >= curY && event.y() <= curY + 16) {
                            activeSubSection = DimensionSubSection.BIOMES;
                            biomeScrollOffset = 0;
                            return true;
                        }
                        curY += 19;

                        // Clicked Zoning Nodes sub-item
                        if (event.x() >= 12 && event.x() <= sideW - 8 && event.y() >= curY && event.y() <= curY + 16) {
                            activeSubSection = DimensionSubSection.ZONING_NODES;
                            return true;
                        }
                        curY += 22;
                    }
                }
            } else {
                // Right Pane Clicks
                if (activeSubSection == DimensionSubSection.BIOMES) {
                    int mainX = sideW + 14;
                    int filterY = topBarH + 28;
                    int pillH = 15;
                    int pillX = mainX;

                    BiomeFilter[] filters = BiomeFilter.values();
                    String[] filterNames = { "All (39)", "Land (18)", "Oceans (8)", "Caves (5)", "Rivers (2)" };

                    for (int i = 0; i < filters.length; i++) {
                        int pw = font.width(filterNames[i]) + 10;
                        if (event.x() >= pillX && event.x() <= pillX + pw && event.y() >= filterY && event.y() <= filterY + pillH) {
                            activeBiomeFilter = filters[i];
                            biomeScrollOffset = 0;
                            return true;
                        }
                        pillX += pw + 5;
                    }
                }
            }
        }

        // 7. World Settings Controls Clicks
        if (activeTab == StudioNavTab.WORLD_SETTINGS) {
            int panelW = Math.min(540, width - 40);
            int panelX = width / 2 - panelW / 2;
            int startY = 48 + 30;

            Preset[] presets = { Preset.STANDARD, Preset.HYTALE_512, Preset.HYTALE_EXTENDED, Preset.EPIC_1024, Preset.CUSTOM };
            int pillGap = 6;
            int pillW = (panelW - (pillGap * (presets.length - 1))) / presets.length;
            int pillH = 22;

            for (int i = 0; i < presets.length; i++) {
                int px = panelX + i * (pillW + pillGap);
                if (event.x() >= px && event.x() <= px + pillW && event.y() >= startY && event.y() <= startY + pillH) {
                    heightPreset = presets[i];
                    if (heightPreset != Preset.CUSTOM) {
                        currentMinY = heightPreset.getDefaultMinY();
                        currentGenDepth = heightPreset.getDefaultGenDepth();
                        currentSeaLevel = heightPreset.getDefaultSeaLevel();
                        minYText = String.valueOf(currentMinY);
                        genDepthText = String.valueOf(currentGenDepth);
                        seaLevelText = String.valueOf(currentSeaLevel);
                        activeField = ActiveInputField.NONE;
                    }
                    return true;
                }
            }

            int cardY = startY + pillH + 16;
            int rowY = cardY + 14;

            int rowW = panelW - 32;
            int boxW = 84;
            int boxH = 22;
            int boxX = panelX + 16 + rowW - boxW;

            // Row 1: Min Y Text Box
            if (event.x() >= boxX && event.x() <= boxX + boxW && event.y() >= rowY && event.y() <= rowY + boxH) {
                if (activeField != ActiveInputField.MIN_Y) {
                    commitInputFields();
                    activeField = ActiveInputField.MIN_Y;
                }
                return true;
            }

            // Row 2: Depth Text Box
            rowY += 38;
            if (event.x() >= boxX && event.x() <= boxX + boxW && event.y() >= rowY && event.y() <= rowY + boxH) {
                if (activeField != ActiveInputField.GEN_DEPTH) {
                    commitInputFields();
                    activeField = ActiveInputField.GEN_DEPTH;
                }
                return true;
            }

            // Row 3: Sea Level Text Box
            rowY += 38;
            if (event.x() >= boxX && event.x() <= boxX + boxW && event.y() >= rowY && event.y() <= rowY + boxH) {
                if (activeField != ActiveInputField.SEA_LEVEL) {
                    commitInputFields();
                    activeField = ActiveInputField.SEA_LEVEL;
                }
                return true;
            }

            if (activeField != ActiveInputField.NONE) {
                commitInputFields();
                activeField = ActiveInputField.NONE;
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == StudioNavTab.DIMENSIONS && activeSubSection == DimensionSubSection.BIOMES) {
            biomeScrollOffset = Math.max(0, biomeScrollOffset - (int) (scrollY * 28));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (showNewProjectModal) {
            if (event.key() == 259 && !newProjectName.isEmpty()) {
                newProjectName = newProjectName.substring(0, newProjectName.length() - 1);
                return true;
            }
            if (event.key() == 257 && !newProjectName.isBlank()) {
                activeProject = StudioProjectManager.createNewProject(newProjectName);
                StudioProjectManager.setActiveProject(activeProject);
                showNewProjectModal = false;
                return true;
            }
            if (event.key() == 256) {
                showNewProjectModal = false;
                return true;
            }
        }

        // Active text box input handling
        if (activeField != ActiveInputField.NONE) {
            if (event.key() == 259) {
                switch (activeField) {
                    case MIN_Y -> {
                        if (!minYText.isEmpty()) minYText = minYText.substring(0, minYText.length() - 1);
                    }
                    case GEN_DEPTH -> {
                        if (!genDepthText.isEmpty()) genDepthText = genDepthText.substring(0, genDepthText.length() - 1);
                    }
                    case SEA_LEVEL -> {
                        if (!seaLevelText.isEmpty()) seaLevelText = seaLevelText.substring(0, seaLevelText.length() - 1);
                    }
                    default -> {}
                }
                return true;
            }
            if (event.key() == 257 || event.key() == 256) {
                commitInputFields();
                activeField = ActiveInputField.NONE;
                return true;
            }
        }

        if (projectDropdownOpen) {
            if (event.key() == 256) {
                projectDropdownOpen = false;
                return true;
            }
        }

        if (event.key() == 256 || WorldGenStudioClient.isStudioKey(event)) {
            if (activeField != ActiveInputField.NONE) {
                commitInputFields();
            }
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (showNewProjectModal) {
            char ch = (char) event.codepoint();
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                newProjectName += ch;
                return true;
            }
        }

        if (activeField != ActiveInputField.NONE) {
            char ch = (char) event.codepoint();
            switch (activeField) {
                case MIN_Y -> {
                    if (Character.isDigit(ch) || (ch == '-' && minYText.isEmpty())) {
                        if (minYText.length() < 6) minYText += ch;
                        return true;
                    }
                }
                case GEN_DEPTH -> {
                    if (Character.isDigit(ch)) {
                        if (genDepthText.length() < 5) genDepthText += ch;
                        return true;
                    }
                }
                case SEA_LEVEL -> {
                    if (Character.isDigit(ch) || (ch == '-' && seaLevelText.isEmpty())) {
                        if (seaLevelText.length() < 5) seaLevelText += ch;
                        return true;
                    }
                }
                default -> {}
            }
        }

        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
