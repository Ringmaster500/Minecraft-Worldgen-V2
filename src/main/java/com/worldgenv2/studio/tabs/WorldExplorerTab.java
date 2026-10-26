package com.worldgenv2.studio.tabs;

import com.worldgenv2.minecraft.DefaultBiomeCatalog;
import com.worldgenv2.studio.StudioLiveCompiler;
import com.worldgenv2.studio.StudioState;
import com.worldgenv2.studio.StudioState.*;
import com.worldgenv2.studio.StudioTheme;
import com.worldgenv2.studio.widgets.VisualBlockPickerModal;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.ZoneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Clean, spacious, single-window-at-a-time UI views for:
 * <ul>
 *   <li><b>Tab 0 — Global Presets:</b> Select whole-world presets (Volumetric, Spawn Island, End Rings, Hytale 576).</li>
 *   <li><b>Tab 1 — Dimensions:</b> Drill from the Dimension List -> Dimension Menu -> Zones & Landmarks OR Settings & Bounds.</li>
 *   <li><b>Tab 2 — Biomes:</b> Browse/search the Dimension's Biome List, import Hytale Biome JSONs, and click into a specific Biome's Node Graph.</li>
 * </ul>
 */
public class WorldExplorerTab {

    public enum DimensionView {
        DIMENSION_LIST,
        DIMENSION_MENU,
        DIMENSION_ZONES,
        DIMENSION_SETTINGS
    }

    public enum BiomeView {
        BIOME_LIST,
        NODE_EDITOR
    }

    private int x;
    private int y;
    private int width;
    private int height;

    private final NodeCanvasTab canvasTab;
    private final VisualBlockPickerModal blockPicker;
    private final IntConsumer switchTopTabCallback;

    private DimensionView dimensionView = DimensionView.DIMENSION_LIST;
    private BiomeView biomeView = BiomeView.BIOME_LIST;

    // Biome List Filter & Scroll
    private String biomeSearch = "";
    private boolean typingSearch = false;
    private int categoryIdx = 0;
    private static final String[] CATEGORIES = {
            "All", "Land & Forests", "Alpine & Cold", "Oceans & Coasts", "Rivers", "Underground 3D", "Imported Hytale"
    };
    private int biomeScroll = 0;
    private int zoneScroll = 0;
    private float dimRadarRadius = 800.0f;

    // Hytale Importer Modal
    private boolean importerOpen = false;
    private final List<HytaleImportCandidate> importCandidates = new ArrayList<>();
    private int importScroll = 0;

    public record HytaleImportCandidate(String title, Path filePath, String rawJson) {}

    public WorldExplorerTab(
            NodeCanvasTab canvasTab,
            VisualBlockPickerModal blockPicker,
            IntConsumer switchTopTabCallback
    ) {
        this.canvasTab = canvasTab;
        this.blockPicker = blockPicker;
        this.switchTopTabCallback = switchTopTabCallback;
        ensureSampleHytaleJsonsExist();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.canvasTab.setBounds(x, y + 24, width, height - 24);
    }

    public void resetDimensionSubViewToList() {
        this.dimensionView = DimensionView.DIMENSION_LIST;
    }

    public void resetBiomeSubViewToList() {
        this.biomeView = BiomeView.BIOME_LIST;
    }

    public void openBiomeNodeEditor(String biomeName) {
        StudioState state = StudioState.get();
        state.setActiveBiome(biomeName);
        state.setGraphMode(GraphMode.BIOME_GRAPH);
        canvasTab.setPresetDropdownToBiome();
        StudioLiveCompiler.loadBiomeIntoCanvas(biomeName, canvasTab);
        this.biomeView = BiomeView.NODE_EDITOR;
    }

    // =========================================================================
    // TAB 0: WORLD PACK OVERVIEW (Pack Metadata, Height Dropdown, Import & Export)
    // =========================================================================

    private static final String[][] HEIGHT_OPTIONS = {
            {"worldgenv2:volumetric", "Standard 384 [-64..320]", "Standard Minecraft 1.18+ height range from Y=-64 to Y=320 (384 blocks)."},
            {"worldgenv2:volumetric_hytale", "Extended Hytale 576 [-64..512]", "Extended vertical height range from Y=-64 to Y=512 (576 blocks) for colossal peaks."},
            {"worldgenv2:deep_underground", "Deep Caverns 320 [-128..192]", "Subterranean focused world with abyssal deep caves from Y=-128 to Y=192."},
            {"worldgenv2:classic", "Classic Realm 256 [0..256]", "Legacy vanilla Minecraft world height from Y=0 to Y=256."}
    };

    public void renderGlobalPresetsTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        StudioState state = StudioState.get();
        int pad = 10;
        int innerX = x + pad;
        int innerW = width - (pad * 2);

        // 1. Pack Header Banner
        int topY = y + 8;
        int topH = 32;
        StudioTheme.drawBeveledPanel(graphics, innerX, topY, innerW, topH, 0xFF171C2B);

        graphics.text(font, "§6§lWorldGen V2 Addon Pack", innerX + 10, topY + 5, StudioTheme.TEXT_WHITE, true);
        graphics.text(
                font,
                font.plainSubstrByWidth("§7Universal multi-dimension world generation, volumetric biomes, and procedural landmark graphs.", Math.max(80, innerW - 20)),
                innerX + 10, topY + 17, StudioTheme.TEXT_GRAY, false
        );

        // 2. Pack Controls Bar: [Height: Dropdown ▼]  [Import Pack]  [Export Pack]  [+ Hytale Biome]
        int ctrlY = topY + topH + 6;
        int ctrlH = 26;
        StudioTheme.drawBeveledPanel(graphics, innerX, ctrlY, innerW, ctrlH, 0xFF141724);

        String activePreset = state.getActiveWorldPreset();
        int hIdx = 0;
        for (int i = 0; i < HEIGHT_OPTIONS.length; i++) {
            if (HEIGHT_OPTIONS[i][0].equals(activePreset)) {
                hIdx = i;
                break;
            }
        }
        String hLabel = "§bHeight: " + HEIGHT_OPTIONS[hIdx][1] + " ▼";

        int btnH = 18;
        int btnY = ctrlY + (ctrlH - btnH) / 2;
        int expW = 72;
        int impPackW = 74;
        int impHytaleW = 86;
        int spanW = Math.min(180, Math.max(120, innerW - expW - impPackW - impHytaleW - 24));

        int spanX = innerX + 6;
        int impPackX = spanX + spanW + 4;
        int expX = impPackX + impPackW + 4;
        int impHytaleX = expX + expW + 4;

        boolean spanHov = mouseX >= spanX && mouseX < spanX + spanW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean impPackHov = mouseX >= impPackX && mouseX < impPackX + impPackW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean expHov = mouseX >= expX && mouseX < expX + expW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean impHytHov = mouseX >= impHytaleX && mouseX < impHytaleX + impHytaleW && mouseY >= btnY && mouseY < btnY + btnH;

        StudioTheme.drawButton(graphics, font, Component.literal(font.plainSubstrByWidth(hLabel, spanW - 4)), spanX, btnY, spanW, btnH, spanHov, true);
        StudioTheme.drawButton(graphics, font, Component.literal("§dImport Pack"), impPackX, btnY, impPackW, btnH, impPackHov, true);
        StudioTheme.drawButton(graphics, font, Component.literal("§aExport Pack"), expX, btnY, expW, btnH, expHov, true);
        if (impHytaleX + impHytaleW <= innerX + innerW - 4) {
            StudioTheme.drawButton(graphics, font, Component.literal("§d+ Hytale JSON"), impHytaleX, btnY, impHytaleW, btnH, impHytHov, true);
        }

        if (spanHov) {
            graphics.setTooltipForNextFrame(
                    font,
                    List.of(
                            Component.literal("§b§lWorld Vertical Height Span: §f" + HEIGHT_OPTIONS[hIdx][1]),
                            Component.literal("§7" + HEIGHT_OPTIONS[hIdx][2]),
                            Component.literal("§eClick to cycle to next height mode (Standard, Hytale 576, Deep Caverns, Classic).")
                    ),
                    Optional.empty(), mouseX, mouseY
            );
        } else if (impPackHov) {
            graphics.setTooltipForNextFrame(
                    font,
                    List.of(
                            Component.literal("§d§lImport Addon Pack"),
                            Component.literal("§7Loads pack configuration and dimension layouts from config/worldgenv2/packs/.")
                    ),
                    Optional.empty(), mouseX, mouseY
            );
        } else if (expHov) {
            graphics.setTooltipForNextFrame(
                    font,
                    List.of(
                            Component.literal("§a§lExport Addon Pack"),
                            Component.literal("§7Exports your active pack with all 4 dimensions, zones, and biomes into a single redistributable pack.")
                    ),
                    Optional.empty(), mouseX, mouseY
            );
        }

        // 3. Pack Dimensions Summary (Clean, non-duplicated overview cards)
        int listY = ctrlY + ctrlH + 6;
        int btmBarH = 22;
        int listH = (y + height - pad - btmBarH - 4) - listY;
        int gap = 4;
        int rowH = Math.min(34, Math.max(24, (listH - (gap * 3)) / 4));

        List<DimensionStudioProfile> dims = new ArrayList<>(state.getDimensionProfiles());
        for (int i = 0; i < Math.min(4, dims.size()); i++) {
            DimensionStudioProfile dim = dims.get(i);
            int ry = listY + i * (rowH + gap);
            boolean isCurDim = dim.dimensionId.equals(state.getActiveDimension());
            boolean rowHov = mouseX >= innerX && mouseX < innerX + innerW && mouseY >= ry && mouseY < ry + rowH;

            StudioTheme.drawBeveledPanel(graphics, innerX, ry, innerW, rowH, rowHov ? 0xFF1E2538 : (isCurDim ? 0xFF182030 : 0xFF151822));
            if (isCurDim) {
                graphics.fill(innerX + 2, ry + 2, innerX + 5, ry + rowH - 2, StudioTheme.TEXT_GOLD);
            }

            int mngBtnW = 74;
            int rBtnH = Math.min(18, rowH - 6);
            int rBtnY = ry + (rowH - rBtnH) / 2;
            int mngBtnX = innerX + innerW - mngBtnW - 6;
            int textMaxW = Math.max(80, mngBtnX - (innerX + 12) - 8);

            String titleStr = "§6§l" + dim.displayName + " §r§7(" + dim.dimensionId + ")";
            graphics.text(font, font.plainSubstrByWidth(titleStr, textMaxW), innerX + 12, ry + 4, StudioTheme.TEXT_WHITE, true);

            String boundsStr = (dim.boundsRadius <= 0.0f) ? "Infinite Continent" : ((int) dim.boundsRadius + "m -> " + dim.outsideFallback);
            String subStr = "§a" + dim.getEnabledBiomeCount() + " Biomes §7• §b" + dim.zones.size() + " Zones §7• §d"
                    + (dim.landmarks.size() + dim.rings.size()) + " Landmarks §7• " + boundsStr;
            if (rowH >= 26) {
                graphics.text(font, font.plainSubstrByWidth(subStr, textMaxW), innerX + 12, ry + rowH - 11, StudioTheme.TEXT_GRAY, false);
            }

            boolean mHov = mouseX >= mngBtnX && mouseX < mngBtnX + mngBtnW && mouseY >= rBtnY && mouseY < rBtnY + rBtnH;
            StudioTheme.drawButton(graphics, font, Component.literal("§eManage ->"), mngBtnX, rBtnY, mngBtnW, rBtnH, mHov, true);

            if (rowHov && !mHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§6" + dim.displayName + " §7(" + dim.dimensionId + ")"),
                                Component.literal("§7" + subStr),
                                Component.literal("§eClick to open and configure this dimension.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
        }

        // 4. Bottom Direct Navigation Action Bar
        int btmY = y + height - pad - btmBarH;
        int navW = (innerW - 4) / 2;
        boolean n1Hov = mouseX >= innerX && mouseX < innerX + navW && mouseY >= btmY && mouseY < btmY + btmBarH;
        boolean n2Hov = mouseX >= innerX + navW + 4 && mouseX < innerX + innerW && mouseY >= btmY && mouseY < btmY + btmBarH;

        StudioTheme.drawButton(graphics, font, Component.literal("§eConfigure Dimensions & Layouts (Tab 1) ->"), innerX, btmY, navW, btmBarH, n1Hov, true);
        StudioTheme.drawButton(graphics, font, Component.literal("§aBrowse Biomes & Node Editor (Tab 2) ->"), innerX + navW + 4, btmY, navW, btmBarH, n2Hov, true);

        if (importerOpen) {
            renderHytaleImporterModal(graphics, font, mouseX, mouseY);
        }
    }

    public boolean mouseClickedGlobalPresets(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        StudioState state = StudioState.get();

        if (importerOpen) {
            return handleHytaleImporterClick(mx, my);
        }

        int pad = 10;
        int innerX = x + pad;
        int innerW = width - (pad * 2);
        int topY = y + 8;
        int topH = 32;

        int ctrlY = topY + topH + 6;
        int ctrlH = 26;
        int btnH = 18;
        int btnY = ctrlY + (ctrlH - btnH) / 2;
        int expW = 72;
        int impPackW = 74;
        int impHytaleW = 86;
        int spanW = Math.min(180, Math.max(120, innerW - expW - impPackW - impHytaleW - 24));

        int spanX = innerX + 6;
        int impPackX = spanX + spanW + 4;
        int expX = impPackX + impPackW + 4;
        int impHytaleX = expX + expW + 4;

        if (my >= btnY && my < btnY + btnH) {
            // Height Mode Dropdown Click
            if (mx >= spanX && mx < spanX + spanW) {
                String activePreset = state.getActiveWorldPreset();
                int hIdx = 0;
                for (int i = 0; i < HEIGHT_OPTIONS.length; i++) {
                    if (HEIGHT_OPTIONS[i][0].equals(activePreset)) {
                        hIdx = i;
                        break;
                    }
                }
                int nextIdx = (hIdx + (button == 1 ? -1 : 1) + HEIGHT_OPTIONS.length) % HEIGHT_OPTIONS.length;
                state.setActiveWorldPreset(HEIGHT_OPTIONS[nextIdx][0]);
                state.setStatusMessage("Switched Pack Vertical Span to " + HEIGHT_OPTIONS[nextIdx][1]);
                return true;
            }
            // Import Pack Click
            if (mx >= impPackX && mx < impPackX + impPackW) {
                importWorldPack();
                return true;
            }
            // Export Pack Click
            if (mx >= expX && mx < expX + expW) {
                exportWorldPack();
                return true;
            }
            // Import Hytale Biome Click
            if (mx >= impHytaleX && mx < impHytaleX + impHytaleW) {
                refreshHytaleImportCandidates();
                importerOpen = true;
                return true;
            }
        }

        int listY = ctrlY + ctrlH + 6;
        int btmBarH = 22;
        int listH = (y + height - pad - btmBarH - 4) - listY;
        int gap = 4;
        int rowH = Math.min(34, Math.max(24, (listH - (gap * 3)) / 4));

        List<DimensionStudioProfile> dims = new ArrayList<>(state.getDimensionProfiles());
        for (int i = 0; i < Math.min(4, dims.size()); i++) {
            int ry = listY + i * (rowH + gap);
            if (my >= ry && my < ry + rowH && mx >= innerX && mx < innerX + innerW) {
                DimensionStudioProfile dim = dims.get(i);
                state.setActiveDimension(dim.dimensionId);
                this.dimRadarRadius = dim.dimensionId.contains("the_end") ? 1500.0f : 750.0f;
                this.dimensionView = DimensionView.DIMENSION_MENU;
                if (switchTopTabCallback != null) switchTopTabCallback.accept(1);
                return true;
            }
        }

        // Bottom Navigation Buttons
        int btmY = y + height - pad - btmBarH;
        int navW = (innerW - 4) / 2;
        if (my >= btmY && my < btmY + btmBarH) {
            if (mx >= innerX && mx < innerX + navW) {
                if (switchTopTabCallback != null) switchTopTabCallback.accept(1);
                return true;
            }
            if (mx >= innerX + navW + 4 && mx < innerX + innerW) {
                if (switchTopTabCallback != null) switchTopTabCallback.accept(2);
                return true;
            }
        }

        return false;
    }

    // =========================================================================
    // TAB 1: DIMENSIONS (Dimension List -> Dimension Inspector + Live Radar Map)
    // =========================================================================

    public void renderDimensionsTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioState state = StudioState.get();
        switch (dimensionView) {
            case DIMENSION_LIST -> renderDimensionListWindow(graphics, font, mouseX, mouseY, state);
            case DIMENSION_MENU -> renderDimensionMenuWindow(graphics, font, mouseX, mouseY, state);
            case DIMENSION_ZONES -> renderDimensionZonesWindow(graphics, font, mouseX, mouseY, state);
            case DIMENSION_SETTINGS -> renderDimensionSettingsWindow(graphics, font, mouseX, mouseY, state);
        }
        if (canvasTab.isStructurePickerOpen()) {
            canvasTab.renderStructurePickerModal(graphics, font, mouseX, mouseY);
        }
    }

    private void renderDimensionListWindow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StudioState state) {
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        int pad = 10;
        int rowX = x + pad;
        int rowW = width - (pad * 2);
        int startY = y + pad;
        int availH = height - (pad * 2);
        int gap = 6;
        int rowH = Math.min(42, Math.max(30, (availH - (gap * 3)) / 4));

        List<DimensionStudioProfile> dims = new ArrayList<>(state.getDimensionProfiles());
        for (int i = 0; i < Math.min(4, dims.size()); i++) {
            DimensionStudioProfile dim = dims.get(i);
            int ry = startY + i * (rowH + gap);
            boolean hov = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + rowH;

            StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, hov ? 0xFF1D2334 : 0xFF161924);

            int btnW = 86;
            int btnH = Math.min(20, rowH - 8);
            int btnX = rowX + rowW - btnW - 8;
            int btnY = ry + (rowH - btnH) / 2;
            int textMaxW = Math.max(80, btnX - (rowX + 12) - 8);

            graphics.text(
                    font,
                    font.plainSubstrByWidth("§6§l" + dim.displayName + " §r§7(" + dim.dimensionId + ")", textMaxW),
                    rowX + 12, ry + 5, StudioTheme.TEXT_WHITE, true
            );

            String boundsText = (dim.boundsRadius <= 0.0f) ? "Infinite Continent" : ((int) dim.boundsRadius + "m -> " + dim.outsideFallback);
            String sub = "§a" + dim.getEnabledBiomeCount() + " Biomes §7| §b" + dim.zones.size() + " Zones §7| §d"
                    + (dim.landmarks.size() + dim.rings.size()) + " Landmarks §7| " + boundsText;
            if (rowH >= 30) {
                graphics.text(font, font.plainSubstrByWidth(sub, textMaxW), rowX + 12, ry + rowH - 13, StudioTheme.TEXT_GRAY, false);
            }

            StudioTheme.drawButton(graphics, font, Component.literal("§eConfigure ->"), btnX, btnY, btnW, btnH, hov, true);

            if (hov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§6" + dim.displayName + " §7(" + dim.dimensionId + ")"),
                                Component.literal("§7" + sub),
                                Component.literal("§eClick to configure layout, landmarks, and biomes.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
        }
    }

    private void renderDimensionMenuWindow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StudioState state) {
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        int pad = 8;
        int topY = y + 6;

        // Top Bar: [< Dimensions] + Dimension Name + Bounds Summary
        int backW = 86;
        boolean backHov = mouseX >= x + pad && mouseX < x + pad + backW && mouseY >= topY && mouseY < topY + 18;
        StudioTheme.drawButton(graphics, font, Component.literal("§e< Dimensions"), x + pad, topY, backW, 18, backHov, true);
        String boundsSummary = (dim.boundsRadius <= 0.0f) ? "Infinite Continent" : ("Bounded: " + (int) dim.boundsRadius + "m -> " + dim.outsideFallback);
        graphics.text(
                font,
                font.plainSubstrByWidth("§6§l" + dim.displayName + " §r§7— §e" + boundsSummary, Math.max(80, width - backW - 24)),
                x + pad + backW + 8, topY + 5, StudioTheme.TEXT_WHITE, true
        );

        // Right-Side Live Top-Down Dimension Faux-Minimap / Probability Visualizer + [Regen Chunks] Button
        int mapSize = Math.min(124, Math.max(88, height - 52));
        boolean showRadar = (width >= 290);
        int leftW = showRadar ? (width - mapSize - pad * 3) : (width - pad * 2);
        int leftX = x + pad;

        if (showRadar) {
            int mapX = x + width - mapSize - pad;
            int mapY = topY + 20;
            StudioTheme.drawBeveledPanel(graphics, mapX, mapY - 14, mapSize, 14, 0xF0181C2B);
            graphics.text(font, "§eRadar §7(±" + (int) dimRadarRadius + "m)", mapX + 4, mapY - 11, StudioTheme.TEXT_WHITE, false);
            graphics.text(font, "§b[-] [+]", mapX + mapSize - 30, mapY - 11, StudioTheme.TEXT_CYAN, false);

            NodeCanvasTab.renderDimensionFauxMinimap(
                    graphics, font, mapX, mapY, mapSize, dimRadarRadius,
                    dim.outsideFallback, dim.boundsRadius, dim.zones, dim.rings, dim.landmarks,
                    mouseX, mouseY
            );

            // [⚡ Regen Dev Chunks] Button directly below radar map!
            int regY = mapY + mapSize + 4;
            int regH = 18;
            boolean regHov = mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= regY && mouseY < regY + regH;
            StudioTheme.drawButton(graphics, font, Component.literal("§6⚡ Regen Chunks"), mapX, regY, mapSize, regH, regHov, true);
            if (regHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§6§lRegenerate Dimension Dev Chunks"),
                                Component.literal("§7Compiles this dimension's layout settings & node structure,"),
                                Component.literal("§7and regenerates all active test chunks around the player.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
        }

        // 4 Clean, Spacious Section Cards on Left Side (ZERO fake presets!)
        int startY = topY + 22;
        int availH = (y + height - pad) - startY;
        int gap = 4;
        int rowH = Math.min(38, Math.max(26, (availH - (gap * 3)) / 4));

        String[][] items = {
                {
                        "§d§lLayout Node Graph",
                        "Procedural Spawn Islands, Radial Zones, Rings & Landmarks",
                        "§dOpen Graph ->"
                },
                {
                        "§e§lZones & Landmarks (" + (dim.zones.size() + dim.landmarks.size() + dim.rings.size()) + ")",
                        dim.zones.size() + " zones • " + dim.landmarks.size() + " landmarks • " + dim.rings.size() + " rings",
                        "§eEdit List ->"
                },
                {
                        "§a§lBiomes (" + dim.getEnabledBiomeCount() + " Active)",
                        "Biome weights, ON/OFF toggles & 3D terrain density graphs",
                        "§aOpen Biomes ->"
                },
                {
                        "§b§lBounds & Settings",
                        "Outside: " + dim.outsideFallback + " • Radius: " + ((dim.boundsRadius <= 0.0f) ? "Infinite" : (int) dim.boundsRadius + "m"),
                        "§bSettings ->"
                }
        };

        for (int i = 0; i < items.length; i++) {
            int ry = startY + i * (rowH + gap);
            boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= ry && mouseY < ry + rowH;
            StudioTheme.drawBeveledPanel(graphics, leftX, ry, leftW, rowH, hov ? 0xFF1E2538 : 0xFF161924);

            int btnW = 86;
            int btnH = Math.min(18, rowH - 4);
            int btnX = leftX + leftW - btnW - 6;
            int btnY = ry + (rowH - btnH) / 2;
            int textMaxW = Math.max(70, btnX - (leftX + 8) - 6);

            graphics.text(font, font.plainSubstrByWidth(items[i][0], textMaxW), leftX + 8, ry + 4, StudioTheme.TEXT_WHITE, true);
            if (rowH >= 26) {
                graphics.text(font, font.plainSubstrByWidth("§7" + items[i][1], textMaxW), leftX + 8, ry + rowH - 11, StudioTheme.TEXT_GRAY, false);
            }

            StudioTheme.drawButton(graphics, font, Component.literal(items[i][2]), btnX, btnY, btnW, btnH, hov, true);

            if (hov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal(items[i][0]),
                                Component.literal("§7" + items[i][1]),
                                Component.literal("§eClick to open and configure.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
        }
    }

    private void renderDimensionZonesWindow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StudioState state) {
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        int pad = 8;
        int topY = y + 6;
        int btnH = 18;

        // Top Bar: [< Back]  [+ Zone]  [+ Landmark]  [+ Tower Ring]  [Layout Node Graph ->]
        int backW = 54;
        boolean backHov = mouseX >= x + pad && mouseX < x + pad + backW && mouseY >= topY && mouseY < topY + btnH;
        StudioTheme.drawButton(graphics, font, Component.literal("§e< Back"), x + pad, topY, backW, btnH, backHov, true);

        int remX = x + pad + backW + 4;
        int remW = (x + width - pad) - remX;
        String[] actions = {"+ Add Zone", "+ Add Landmark", "+ Tower Ring", "Layout Node Graph ->"};
        int actGap = 4;
        int actW = (remW - actGap * 3) / 4;
        for (int i = 0; i < actions.length; i++) {
            int ax = remX + i * (actW + actGap);
            boolean hov = mouseX >= ax && mouseX < ax + actW && mouseY >= topY && mouseY < topY + btnH;
            String prefix = (i == 3) ? "§d" : "§a";
            StudioTheme.drawButton(graphics, font, Component.literal(prefix + font.plainSubstrByWidth(actions[i], actW - 6)), ax, topY, actW, btnH, hov, true);
        }

        // Right-side live Top-Down Faux-Minimap (only when wide enough so rows NEVER overflow!)
        int mapSize = Math.min(112, Math.max(84, height - 52));
        boolean showRadar = (width >= 460);
        int listW = showRadar ? (width - mapSize - pad * 3) : (width - pad * 2);

        if (showRadar) {
            int mapX = x + width - mapSize - pad;
            int mapY = topY + btnH + 18;
            StudioTheme.drawBeveledPanel(graphics, mapX, mapY - 12, mapSize, 12, 0xF0181C2B);
            graphics.text(font, "§eRadar §7(±" + (int) dimRadarRadius + "m)", mapX + 3, mapY - 10, StudioTheme.TEXT_WHITE, false);
            graphics.text(font, "§b-+ ", mapX + mapSize - 18, mapY - 10, StudioTheme.TEXT_CYAN, false);
            NodeCanvasTab.renderDimensionFauxMinimap(
                    graphics, font, mapX, mapY, mapSize, dimRadarRadius,
                    dim.outsideFallback, dim.boundsRadius, dim.zones, dim.rings, dim.landmarks,
                    mouseX, mouseY
            );

            // [⚡ Regen Dev Chunks] Button
            int regY = mapY + mapSize + 4;
            boolean regHov = mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= regY && mouseY < regY + 18;
            StudioTheme.drawButton(graphics, font, Component.literal("§6⚡ Regen Chunks"), mapX, regY, mapSize, 18, regHov, true);
        }

        // Spacious Scrollable List of Zones, Ring Towers & Landmarks
        int listY = topY + btnH + 6;
        int listH = (y + height - pad) - listY;
        StudioTheme.drawInsetWell(graphics, x + pad, listY, listW, listH, 0xFF0D0F17);

        int ry = listY + 4;
        int rowH = 24;
        int rowX = x + pad + 4;
        int rowW = listW - 8;

        if (dim.zones.isEmpty() && dim.rings.isEmpty() && dim.landmarks.isEmpty()) {
            graphics.centeredText(
                    font,
                    "§7Infinite Voronoi Biomes (0 zones/landmarks). Click [+ Add Zone], [+ Add Landmark], or [Layout Node Graph ->].",
                    x + pad + listW / 2, listY + listH / 2 - 4, StudioTheme.TEXT_GRAY
            );
            return;
        }

        // 1. Radial Zones
        for (int i = 0; i < dim.zones.size(); i++) {
            if (ry + rowH > listY + listH - 2) break;
            EditableZoneRule z = dim.zones.get(i);
            boolean rowHov = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + rowH - 2;
            graphics.fill(rowX, ry, rowX + rowW, ry + rowH - 2, (i % 2 == 0) ? 0xFF171B28 : 0xFF131620);

            int delX = rowX + rowW - 16;
            int fltX = delX - 36;
            int bioX = fltX - 62;
            int modX = bioX - 62;
            int radX = modX - 58;
            int lblMaxW = Math.max(40, radX - (rowX + 4) - 4);

            graphics.text(font, font.plainSubstrByWidth("§eZone: §f" + z.id, lblMaxW), rowX + 4, ry + 7, StudioTheme.TEXT_WHITE, false);

            renderStepper(graphics, font, mouseX, mouseY, radX, ry + 3, 56, rowH - 8, (int) z.innerRadius + ".." + (int) z.outerRadius + "m");

            boolean mHov = mouseX >= modX && mouseX < modX + 60 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal("§b" + font.plainSubstrByWidth(z.mode.name(), 56)), modX, ry + 3, 60, rowH - 8, mHov, true);

            boolean bHov = mouseX >= bioX && mouseX < bioX + 60 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            String bName = (z.targetBiome == null || z.targetBiome.isEmpty()) ? "None" : z.targetBiome;
            StudioTheme.drawButton(graphics, font, Component.literal("§a" + font.plainSubstrByWidth(bName, 56)), bioX, ry + 3, 60, rowH - 8, bHov, true);

            boolean fltHov = mouseX >= fltX && mouseX < fltX + 34 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal(z.floating ? "§dFloat" : "§7Solid"), fltX, ry + 3, 34, rowH - 8, fltHov, true);

            boolean delHov = mouseX >= delX && mouseX < delX + 16 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal("§cX"), delX, ry + 3, 16, rowH - 8, delHov, true);

            if (rowHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§eRadial Zone [" + z.id + "]"),
                                Component.literal("§7Radius: §f" + (int) z.innerRadius + "m to " + (int) z.outerRadius + "m §7• Mode: §b" + z.mode + " §7• Biome: §a" + bName),
                                Component.literal("§7Floating: " + (z.floating ? "§dYES (Sky Island)" : "§7NO (Ground)"))
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
            ry += rowH;
        }

        // 2. Ring Towers (e.g. End Obsidian Pillars)
        for (EditableRingRule r : dim.rings) {
            if (ry + rowH > listY + listH - 2) break;
            boolean rowHov = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + rowH - 2;
            graphics.fill(rowX, ry, rowX + rowW, ry + rowH - 2, 0xFF1C162B);

            int delX = rowX + rowW - 16;
            int rX = delX - 62;
            int cX = rX - 60;
            int lblMaxW = Math.max(40, cX - (rowX + 4) - 4);

            graphics.text(font, font.plainSubstrByWidth("§dRing: §f" + r.id, lblMaxW), rowX + 4, ry + 7, StudioTheme.TEXT_WHITE, false);
            renderStepper(graphics, font, mouseX, mouseY, cX, ry + 3, 58, rowH - 8, r.count + " twrs");
            renderStepper(graphics, font, mouseX, mouseY, rX, ry + 3, 60, rowH - 8, "R=" + (int) r.ringRadius + "m");

            boolean delHov = mouseX >= delX && mouseX < delX + 16 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal("§cX"), delX, ry + 3, 16, rowH - 8, delHov, true);

            if (rowHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§dRing Landmarks [" + r.id + "]"),
                                Component.literal("§7Spawns §f" + r.count + " §7pillars of §f" + r.blockId + " §7in a circle at R=" + (int) r.ringRadius + "m")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
            ry += rowH;
        }

        // 3. Connected Landmarks (with Structure Picker, Count Stepper, Distance Band, Bridge Toggle & Delete [X]!)
        for (EditableLandmarkRule lm : dim.landmarks) {
            if (ry + rowH > listY + listH - 2) break;
            boolean rowHov = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + rowH - 2;
            graphics.fill(rowX, ry, rowX + rowW, ry + rowH - 2, 0xFF162222);

            int delX = rowX + rowW - 16;
            int brX = delX - 38;
            int dX = brX - 60;
            int cntX = dX - 36;
            int stX = cntX - 74;
            int lblMaxW = Math.max(40, stX - (rowX + 4) - 4);

            graphics.text(font, font.plainSubstrByWidth("§6LM: §f" + lm.id, lblMaxW), rowX + 4, ry + 7, StudioTheme.TEXT_WHITE, false);

            // Structure Picker Button!
            boolean stHov = mouseX >= stX && mouseX < stX + 72 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            String shortSt = shortId(lm.structureId);
            StudioTheme.drawButton(
                    graphics, font,
                    Component.literal("§d" + font.plainSubstrByWidth(shortSt, 64)),
                    stX, ry + 3, 72, rowH - 8, stHov, true
            );

            // Count Stepper (x1..12)
            renderStepper(graphics, font, mouseX, mouseY, cntX, ry + 3, 34, rowH - 8, "x" + lm.count);

            // Distance Band Stepper (min..max m)
            renderStepper(graphics, font, mouseX, mouseY, dX, ry + 3, 58, rowH - 8, (int) lm.minDistance + ".." + (int) lm.maxDistance + "m");

            // Bridge Toggle
            boolean brHov = mouseX >= brX && mouseX < brX + 36 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal(lm.connectBridge ? "§aRoad" : "§7Off"), brX, ry + 3, 36, rowH - 8, brHov, true);

            // Delete [X]
            boolean delHov = mouseX >= delX && mouseX < delX + 16 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal("§cX"), delX, ry + 3, 16, rowH - 8, delHov, true);

            if (rowHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§6Landmark [" + lm.id + "]"),
                                Component.literal("§7Structure: §d" + lm.structureId + " §7(Click button to pick structure!)"),
                                Component.literal("§7Count: §fx" + lm.count + " §7• Distance Band: §e" + (int) lm.minDistance + "m to " + (int) lm.maxDistance + "m"),
                                Component.literal("§7Road/Bridge to Core: " + (lm.connectBridge ? "§aYES" : "§7NO"))
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
            ry += rowH;
        }
    }

    private void renderDimensionSettingsWindow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StudioState state) {
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        int pad = 10;
        int topY = y + 6;
        int backW = 68;
        boolean backHov = mouseX >= x + pad && mouseX < x + pad + backW && mouseY >= topY && mouseY < topY + 18;
        StudioTheme.drawButton(graphics, font, Component.literal("§e< Back"), x + pad, topY, backW, 18, backHov, true);
        graphics.text(font, "§6§l" + dim.displayName + " — Bounds & Settings", x + pad + backW + 10, topY + 5, StudioTheme.TEXT_WHITE, true);

        int startY = topY + 24;
        int availH = (y + height - pad) - startY;
        int gap = 5;
        int rowH = Math.min(28, Math.max(22, (availH - gap * 4) / 5));
        int rowX = x + pad;
        int rowW = width - pad * 2;
        int ctrlW = Math.min(180, rowW / 2);
        int ctrlX = rowX + rowW - ctrlW - 6;

        // Row 0: Outside-Bounds Fallback Mode
        int ry = startY;
        StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, 0xFF161924);
        graphics.text(font, "§fOutside-Bounds Behavior", rowX + 10, ry + (rowH - 8) / 2, StudioTheme.TEXT_WHITE, false);
        boolean fbHov = mouseX >= ctrlX && mouseX < ctrlX + ctrlW && mouseY >= ry + 3 && mouseY < ry + rowH - 3;
        StudioTheme.drawButton(graphics, font, Component.literal("§e" + dim.outsideFallback.name()), ctrlX, ry + 3, ctrlW, rowH - 6, fbHov, true);

        // Row 1: Modification Bounds Radius
        ry += rowH + gap;
        StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, 0xFF161924);
        graphics.text(font, "§fModification Bounds Radius", rowX + 10, ry + (rowH - 8) / 2, StudioTheme.TEXT_WHITE, false);
        String radStr = (dim.boundsRadius <= 0.0f) ? "Infinite (0m)" : ((int) dim.boundsRadius + " blocks");
        renderStepper(graphics, font, mouseX, mouseY, ctrlX, ry + 3, ctrlW, rowH - 6, radStr);

        // Row 2: Boundary Blend Falloff
        ry += rowH + gap;
        StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, 0xFF161924);
        graphics.text(font, "§fBoundary Blend Falloff", rowX + 10, ry + (rowH - 8) / 2, StudioTheme.TEXT_WHITE, false);
        renderStepper(graphics, font, mouseX, mouseY, ctrlX, ry + 3, ctrlW, rowH - 6, (int) dim.blendFalloff + " blocks");

        // Row 3: Sea Level Y
        ry += rowH + gap;
        StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, 0xFF161924);
        graphics.text(font, "§fSea Level Elevation", rowX + 10, ry + (rowH - 8) / 2, StudioTheme.TEXT_WHITE, false);
        renderStepper(graphics, font, mouseX, mouseY, ctrlX, ry + 3, ctrlW, rowH - 6, "Y = " + dim.seaLevel);

        // Row 4: Default Block & Fluid
        ry += rowH + gap;
        StudioTheme.drawBeveledPanel(graphics, rowX, ry, rowW, rowH, 0xFF161924);
        graphics.text(font, "§fDefault Terrain & Fluid Blocks", rowX + 10, ry + (rowH - 8) / 2, StudioTheme.TEXT_WHITE, false);
        int halfCtrl = (ctrlW - 4) / 2;
        boolean b1Hov = mouseX >= ctrlX && mouseX < ctrlX + halfCtrl && mouseY >= ry + 3 && mouseY < ry + rowH - 3;
        boolean b2Hov = mouseX >= ctrlX + halfCtrl + 4 && mouseX < ctrlX + ctrlW && mouseY >= ry + 3 && mouseY < ry + rowH - 3;
        StudioTheme.drawButton(graphics, font, Component.literal("§e" + shortId(dim.defaultBlock)), ctrlX, ry + 3, halfCtrl, rowH - 6, b1Hov, true);
        StudioTheme.drawButton(graphics, font, Component.literal("§b" + shortId(dim.fluidBlock)), ctrlX + halfCtrl + 4, ry + 3, halfCtrl, rowH - 6, b2Hov, true);
    }

    public boolean mouseClickedDimensions(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        StudioState state = StudioState.get();

        if (canvasTab.isStructurePickerOpen()) {
            return canvasTab.handleStructurePickerClick(mx, my, button);
        }

        return switch (dimensionView) {
            case DIMENSION_LIST -> {
                int pad = 10;
                int rowX = x + pad;
                int rowW = width - (pad * 2);
                int startY = y + pad;
                int availH = height - (pad * 2);
                int gap = 6;
                int rowH = Math.min(42, Math.max(30, (availH - (gap * 3)) / 4));

                List<DimensionStudioProfile> dims = new ArrayList<>(state.getDimensionProfiles());
                for (int i = 0; i < Math.min(4, dims.size()); i++) {
                    int ry = startY + i * (rowH + gap);
                    if (my >= ry && my < ry + rowH && mx >= rowX && mx < rowX + rowW) {
                        DimensionStudioProfile chosen = dims.get(i);
                        state.setActiveDimension(chosen.dimensionId);
                        this.dimRadarRadius = chosen.dimensionId.contains("the_end") ? 1500.0f : 750.0f;
                        this.dimensionView = DimensionView.DIMENSION_MENU;
                        yield true;
                    }
                }
                yield false;
            }
            case DIMENSION_MENU -> {
                DimensionStudioProfile dim = state.getActiveDimensionProfile();
                int pad = 8;
                int topY = y + 6;
                if (my >= topY && my < topY + 18 && mx >= x + pad && mx < x + pad + 86) {
                    this.dimensionView = DimensionView.DIMENSION_LIST;
                    yield true;
                }

                int mapSize = Math.min(124, Math.max(88, height - 52));
                boolean showRadar = (width >= 290);
                int leftW = showRadar ? (width - mapSize - pad * 3) : (width - pad * 2);
                int leftX = x + pad;

                // Check Radar Zoom [-] / [+] and [⚡ Regen Chunks] click
                if (showRadar) {
                    int mapX = x + width - mapSize - pad;
                    int mapY = topY + 20;
                    if (mx >= mapX && mx < mapX + mapSize && my >= mapY - 14 && my < mapY) {
                        if (mx >= mapX + mapSize - 30 && mx < mapX + mapSize - 15) {
                            dimRadarRadius = Math.min(5000.0f, dimRadarRadius * 1.4f);
                        } else if (mx >= mapX + mapSize - 15) {
                            dimRadarRadius = Math.max(200.0f, dimRadarRadius / 1.4f);
                        }
                        yield true;
                    }
                    int regY = mapY + mapSize + 4;
                    if (mx >= mapX && mx < mapX + mapSize && my >= regY && my < regY + 18) {
                        StudioLiveCompiler.applyDimensionProfile(dim, true);
                        state.setStatusMessage("Regenerated live dev chunks for " + dim.displayName + "!");
                        yield true;
                    }
                }

                int startY = topY + 22;
                int availH = (y + height - pad) - startY;
                int gap = 4;
                int rowH = Math.min(38, Math.max(26, (availH - (gap * 3)) / 4));

                for (int i = 0; i < 4; i++) {
                    int ry = startY + i * (rowH + gap);
                    if (my >= ry && my < ry + rowH && mx >= leftX && mx < leftX + leftW) {
                        if (i == 0) {
                            // Open Layout Node Graph!
                            StudioLiveCompiler.loadDimensionProfileIntoCanvas(dim, canvasTab);
                            this.biomeView = BiomeView.NODE_EDITOR;
                            if (switchTopTabCallback != null) switchTopTabCallback.accept(2);
                        } else if (i == 1) {
                            this.dimensionView = DimensionView.DIMENSION_ZONES;
                        } else if (i == 2) {
                            this.biomeView = BiomeView.BIOME_LIST;
                            if (switchTopTabCallback != null) switchTopTabCallback.accept(2);
                        } else {
                            this.dimensionView = DimensionView.DIMENSION_SETTINGS;
                        }
                        yield true;
                    }
                }
                yield false;
            }
            case DIMENSION_ZONES -> handleDimensionZonesClick(mx, my, button, state);
            case DIMENSION_SETTINGS -> handleDimensionSettingsWindowClick(mx, my, button, state);
        };
    }

    private boolean handleDimensionZonesClick(double mx, double my, int button, StudioState state) {
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        int pad = 8;
        int topY = y + 6;
        int btnH = 18;
        int backW = 54;

        if (my >= topY && my < topY + btnH) {
            if (mx >= x + pad && mx < x + pad + backW) {
                this.dimensionView = DimensionView.DIMENSION_MENU;
                return true;
            }
            int remX = x + pad + backW + 4;
            int remW = (x + width - pad) - remX;
            int actGap = 4;
            int actW = (remW - actGap * 3) / 4;
            int idx = (int) ((mx - remX) / (actW + actGap));
            if (idx == 0) {
                float lastOuter = dim.zones.isEmpty() ? 0.0f : dim.zones.getLast().outerRadius;
                dim.zones.add(new EditableZoneRule("zone_" + (dim.zones.size() + 1), lastOuter, lastOuter + 240.0f, 36.0f, ZoneMode.ISLAND_PLATEAU, "TemperateForest", 68.0f, false));
                StudioLiveCompiler.applyDimensionProfileLive(dim);
            } else if (idx == 1) {
                String rootId = dim.zones.isEmpty() ? "spawn_island" : dim.zones.getFirst().id;
                dim.landmarks.add(new EditableLandmarkRule(
                        "landmark_" + (dim.landmarks.size() + 1),
                        rootId,
                        110.0f,
                        185.0f,
                        "worldgenv2:watchtower_01",
                        1,
                        true,
                        3.5f
                ));
                StudioLiveCompiler.applyDimensionProfileLive(dim);
            } else if (idx == 2) {
                dim.rings.add(new EditableRingRule("tower_ring_" + (dim.rings.size() + 1), 43.0f, 10, 4, 76, 103, "minecraft:obsidian", true));
                StudioLiveCompiler.applyDimensionProfileLive(dim);
            } else if (idx >= 3) {
                StudioLiveCompiler.loadDimensionProfileIntoCanvas(dim, canvasTab);
                this.biomeView = BiomeView.NODE_EDITOR;
                if (switchTopTabCallback != null) switchTopTabCallback.accept(2);
            }
            return true;
        }

        int mapSize = Math.min(112, Math.max(84, height - 52));
        boolean showRadar = (width >= 460);
        int listW = showRadar ? (width - mapSize - pad * 3) : (width - pad * 2);

        if (showRadar) {
            int mapX = x + width - mapSize - pad;
            int mapY = topY + btnH + 18;
            if (mx >= mapX && mx < mapX + mapSize && my >= mapY - 12 && my < mapY) {
                dimRadarRadius = (mx < mapX + mapSize - 10)
                        ? Math.min(5000.0f, dimRadarRadius * 1.4f)
                        : Math.max(200.0f, dimRadarRadius / 1.4f);
                return true;
            }
            int regY = mapY + mapSize + 4;
            if (mx >= mapX && mx < mapX + mapSize && my >= regY && my < regY + 18) {
                StudioLiveCompiler.applyDimensionProfile(dim, true);
                state.setStatusMessage("Regenerated live dev chunks for " + dim.displayName + "!");
                return true;
            }
        }

        int listY = topY + btnH + 6;
        int ry = listY + 4;
        int rowH = 24;
        int rowX = x + pad + 4;
        int rowW = listW - 8;

        // 1. Zones click
        int delX = rowX + rowW - 16;
        int fltX = delX - 36;
        int bioX = fltX - 62;
        int modX = bioX - 62;
        int radX = modX - 58;

        for (int i = 0; i < dim.zones.size(); i++) {
            EditableZoneRule z = dim.zones.get(i);
            if (my >= ry && my < ry + rowH - 2) {
                if (mx >= delX && mx < delX + 16) {
                    dim.zones.remove(i);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= fltX && mx < fltX + 34) {
                    z.floating = !z.floating;
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= bioX && mx < bioX + 60) {
                    List<DefaultBiomeCatalog.Entry> all = DefaultBiomeCatalog.entries();
                    int cur = 0;
                    for (int bi = 0; bi < all.size(); bi++) {
                        if (all.get(bi).name().equalsIgnoreCase(z.targetBiome)) {
                            cur = bi;
                            break;
                        }
                    }
                    z.targetBiome = all.get((cur + (button == 1 ? -1 : 1) + all.size()) % all.size()).name();
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= modX && mx < modX + 60) {
                    ZoneMode[] modes = ZoneMode.values();
                    z.mode = modes[(z.mode.ordinal() + 1) % modes.length];
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= radX && mx < radX + 56) {
                    float delta = (mx < radX + 28) ? -40.0f : 40.0f;
                    z.outerRadius = Math.max(z.innerRadius + 20.0f, z.outerRadius + delta);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
            }
            ry += rowH;
        }

        // 2. Ring Towers click
        for (int i = 0; i < dim.rings.size(); i++) {
            EditableRingRule r = dim.rings.get(i);
            if (my >= ry && my < ry + rowH - 2) {
                int rDelX = rowX + rowW - 16;
                int rX = rDelX - 62;
                int cX = rX - 60;
                if (mx >= rDelX && mx < rDelX + 16) {
                    dim.rings.remove(i);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= cX && mx < cX + 58) {
                    r.count = Math.clamp(r.count + (mx < cX + 29 ? -1 : 1), 1, 32);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= rX && mx < rX + 60) {
                    r.ringRadius = Math.clamp(r.ringRadius + (mx < rX + 30 ? -4.0f : 4.0f), 12.0f, 300.0f);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
            }
            ry += rowH;
        }

        // 3. Connected Landmarks click (Structure Picker, Count, Distance Band, Bridge Toggle, Delete [X])
        for (int i = 0; i < dim.landmarks.size(); i++) {
            EditableLandmarkRule lm = dim.landmarks.get(i);
            if (my >= ry && my < ry + rowH - 2) {
                int lDelX = rowX + rowW - 16;
                int brX = lDelX - 38;
                int dX = brX - 60;
                int cntX = dX - 36;
                int stX = cntX - 74;

                if (mx >= lDelX && mx < lDelX + 16) {
                    dim.landmarks.remove(i);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= stX && mx < stX + 72) {
                    // Open Interactive Structure Picker Modal!
                    canvasTab.openStructurePicker(selectedStructId -> {
                        lm.structureId = selectedStructId;
                        StudioLiveCompiler.applyDimensionProfileLive(dim);
                    });
                    return true;
                }
                if (mx >= cntX && mx < cntX + 34) {
                    lm.count = Math.clamp(lm.count + (mx < cntX + 17 ? -1 : 1), 1, 12);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= dX && mx < dX + 58) {
                    lm.maxDistance = Math.max(lm.minDistance + 20.0f, lm.maxDistance + (mx < dX + 29 ? -20.0f : 20.0f));
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                if (mx >= brX && mx < brX + 36) {
                    lm.connectBridge = !lm.connectBridge;
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
            }
            ry += rowH;
        }
        return false;
    }

    private boolean handleDimensionSettingsWindowClick(double mx, double my, int button, StudioState state) {
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        int pad = 10;
        int topY = y + 6;
        if (my >= topY && my < topY + 18 && mx >= x + pad && mx < x + pad + 68) {
            this.dimensionView = DimensionView.DIMENSION_MENU;
            return true;
        }

        int startY = topY + 24;
        int availH = (y + height - pad) - startY;
        int gap = 5;
        int rowH = Math.min(28, Math.max(22, (availH - gap * 4) / 5));
        int rowX = x + pad;
        int rowW = width - pad * 2;
        int ctrlW = Math.min(180, rowW / 2);
        int ctrlX = rowX + rowW - ctrlW - 6;

        // Row 0: OutsideBoundsFallback cycle
        int ry = startY;
        if (my >= ry && my < ry + rowH && mx >= ctrlX && mx < ctrlX + ctrlW) {
            OutsideBoundsFallback[] vals = OutsideBoundsFallback.values();
            dim.outsideFallback = vals[(dim.outsideFallback.ordinal() + (button == 1 ? -1 : 1) + vals.length) % vals.length];
            StudioLiveCompiler.applyDimensionProfileLive(dim);
            return true;
        }

        // Row 1: Bounds Radius
        ry += rowH + gap;
        if (my >= ry && my < ry + rowH && mx >= ctrlX && mx < ctrlX + ctrlW) {
            float delta = (mx < ctrlX + ctrlW / 2.0) ? -80.0f : 80.0f;
            dim.boundsRadius = Math.max(0.0f, dim.boundsRadius + delta);
            StudioLiveCompiler.applyDimensionProfileLive(dim);
            return true;
        }

        // Row 2: Blend Falloff
        ry += rowH + gap;
        if (my >= ry && my < ry + rowH && mx >= ctrlX && mx < ctrlX + ctrlW) {
            float delta = (mx < ctrlX + ctrlW / 2.0) ? -8.0f : 8.0f;
            dim.blendFalloff = Math.clamp(dim.blendFalloff + delta, 8.0f, 256.0f);
            StudioLiveCompiler.applyDimensionProfileLive(dim);
            return true;
        }

        // Row 3: Sea Level
        ry += rowH + gap;
        if (my >= ry && my < ry + rowH && mx >= ctrlX && mx < ctrlX + ctrlW) {
            int delta = (mx < ctrlX + ctrlW / 2.0) ? -2 : 2;
            dim.seaLevel = Math.clamp(dim.seaLevel + delta, -64, 320);
            StudioLiveCompiler.applyDimensionProfileLive(dim);
            return true;
        }

        // Row 4: Default Block & Fluid
        ry += rowH + gap;
        int halfCtrl = (ctrlW - 4) / 2;
        if (my >= ry && my < ry + rowH) {
            if (mx >= ctrlX && mx < ctrlX + halfCtrl && blockPicker != null) {
                blockPicker.open((Identifier id) -> {
                    dim.defaultBlock = id.toString();
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                });
                return true;
            }
            if (mx >= ctrlX + halfCtrl + 4 && mx < ctrlX + ctrlW && blockPicker != null) {
                blockPicker.open((Identifier id) -> {
                    dim.fluidBlock = id.toString();
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                });
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // TAB 2: BIOMES (Clean Biome List -> Click a Biome -> Node Editor)
    // =========================================================================

    public void renderBiomesTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioState state = StudioState.get();

        if (biomeView == BiomeView.NODE_EDITOR) {
            // Top bar with [< Back], Active Graph Title, [Test Area (N chunks) ->], and [Export JSON]
            StudioTheme.drawBeveledPanel(graphics, x, y, width, 22, 0xF0161924);
            int backW = 104;
            boolean backHov = mouseX >= x + 6 && mouseX < x + 6 + backW && mouseY >= y + 2 && mouseY < y + 20;
            String backLabel = state.isDimensionGraphMode() ? "§e< Back to Dimension" : "§e< Back to Biomes";
            StudioTheme.drawButton(graphics, font, Component.literal(backLabel), x + 6, y + 2, backW, 18, backHov, true);

            int expW = 68;
            int expX = x + width - expW - 6;
            boolean expHov = mouseX >= expX && mouseX < expX + expW && mouseY >= y + 2 && mouseY < y + 20;
            StudioTheme.drawButton(graphics, font, Component.literal("Export JSON"), expX, y + 2, expW, 18, expHov, true);

            int testW = 116;
            int testX = expX - testW - 4;
            boolean testHov = mouseX >= testX && mouseX < testX + testW && mouseY >= y + 2 && mouseY < y + 20;
            int chunkCount = state.getSelectedChunks().size();
            StudioTheme.drawButton(
                    graphics, font,
                    Component.literal("§bTest Area (" + chunkCount + " chunks) ->"),
                    testX, y + 2, testW, 18, testHov, true
            );
            if (testHov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§b§lAssign & Inspect Live Test Chunks"),
                                Component.literal("§7Only Node Graph edits regenerate your selected dev chunks."),
                                Component.literal("§eClick to open Test Chunks & choose which chunks update.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }

            int titleMaxW = Math.max(60, testX - (x + backW + 12) - 6);
            String label = state.isDimensionGraphMode()
                    ? "§d§lMacro Layout Graph: §f" + state.getActiveDimensionProfile().displayName
                    : "§a§lBiome Node Graph: §f" + state.getActiveBiome();
            graphics.text(font, font.plainSubstrByWidth(label, titleMaxW), x + backW + 12, y + 7, StudioTheme.TEXT_WHITE, true);

            canvasTab.render(graphics, font, mouseX, mouseY);
            if (importerOpen) {
                renderHytaleImporterModal(graphics, font, mouseX, mouseY);
            }
            return;
        }

        // Otherwise: Clean, spacious Biome List Window
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xE612141E);

        int pad = 8;
        int topY = y + 6;
        int barH = 18;

        // Top Filter Bar: [Dim: Overworld ▼]  [Search Box]  [Category]  [+ Import Hytale]
        int dimW = 104;
        int impW = 96;
        int catW = 92;
        int searchW = Math.max(70, (width - pad * 2) - dimW - impW - catW - 12);

        int dimX = x + pad;
        int searchX = dimX + dimW + 4;
        int catX = searchX + searchW + 4;
        int impX = catX + catW + 4;

        // 0. Active Dimension Selector Dropdown Button!
        boolean dimHov = mouseX >= dimX && mouseX < dimX + dimW && mouseY >= topY && mouseY < topY + barH;
        StudioTheme.drawButton(graphics, font, Component.literal("§6Dim: " + font.plainSubstrByWidth(dim.displayName, dimW - 32) + " ▼"), dimX, topY, dimW, barH, dimHov, true);
        if (dimHov) {
            graphics.setTooltipForNextFrame(
                    font,
                    List.of(
                            Component.literal("§6§lActive Dimension: §f" + dim.displayName),
                            Component.literal("§7Click to switch active dimension biomes without leaving this screen."),
                            Component.literal("§eLeft-click: Next Dimension | Right-click: Previous Dimension")
                    ),
                    Optional.empty(), mouseX, mouseY
            );
        }

        StudioTheme.drawInsetWell(graphics, searchX, topY, searchW, barH, typingSearch ? 0xFF1C2838 : 0xFF0D0F16);
        String qDisp = biomeSearch.isEmpty()
                ? (typingSearch ? "§eType biome name..._" : "§7Search biomes in " + dim.displayName + "...")
                : "§f" + biomeSearch + (typingSearch ? "_" : "");
        graphics.text(font, font.plainSubstrByWidth(qDisp, searchW - 10), searchX + 5, topY + 5, StudioTheme.TEXT_WHITE, false);

        boolean catHov = mouseX >= catX && mouseX < catX + catW && mouseY >= topY && mouseY < topY + barH;
        StudioTheme.drawButton(graphics, font, Component.literal(font.plainSubstrByWidth(CATEGORIES[categoryIdx], catW - 8)), catX, topY, catW, barH, catHov, true);

        boolean impHov = mouseX >= impX && mouseX < impX + impW && mouseY >= topY && mouseY < topY + barH;
        StudioTheme.drawButton(graphics, font, Component.literal("§d+ Import Hytale"), impX, topY, impW, barH, impHov, true);

        // Spacious Biome Rows (6 to 7 clean rows visible, zero overlap)
        int listY = topY + barH + 6;
        int listH = (y + height - pad) - listY;
        StudioTheme.drawInsetWell(graphics, x + pad, listY, width - pad * 2, listH, 0xFF0D0F17);

        List<BiomeEntryState> filtered = getFilteredBiomes(dim);
        int rowH = 24;
        int maxVis = Math.max(1, (listH - 6) / rowH);
        int maxScroll = Math.max(0, filtered.size() - maxVis);
        biomeScroll = Math.clamp(biomeScroll, 0, maxScroll);

        int rowX = x + pad + 4;
        int rowW = width - (pad * 2) - 12;

        for (int i = 0; i < maxVis; i++) {
            int idx = i + biomeScroll;
            if (idx >= filtered.size()) break;

            BiomeEntryState b = filtered.get(idx);
            int ry = listY + 3 + i * rowH;
            boolean hov = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + rowH - 2;

            graphics.fill(rowX, ry, rowX + rowW, ry + rowH - 2, hov ? 0xFF1E2638 : ((idx % 2 == 0) ? 0xFF151822 : 0xFF12141C));

            // 1. Toggle [ON/OFF]
            boolean togHov = mouseX >= rowX + 4 && mouseX < rowX + 28 && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal(b.enabled ? "§aON" : "§7OFF"), rowX + 4, ry + 3, 24, rowH - 8, togHov, true);

            // 2. Color Swatch + Biome Name
            int swatchCol = StudioLiveCompiler.getBiomeMapColor(b.id);
            graphics.fill(rowX + 32, ry + 5, rowX + 40, ry + rowH - 7, swatchCol);

            int openBtnW = 72;
            int openBtnX = rowX + rowW - openBtnW - 4;
            int weightW = 66;
            int weightX = openBtnX - weightW - 4;

            int nameMaxW = Math.max(60, weightX - (rowX + 44) - 6);
            String badge = b.importedHytale ? " §d[Hytale]" : " §7(" + b.category + ")";
            graphics.text(
                    font,
                    font.plainSubstrByWidth((b.enabled ? "§f" : "§8") + b.name + badge, nameMaxW),
                    rowX + 44, ry + 7, StudioTheme.TEXT_WHITE, false
            );

            // 3. Weight Stepper
            renderStepper(graphics, font, mouseX, mouseY, weightX, ry + 3, weightW, rowH - 8, "W: " + String.format(Locale.ROOT, "%.1f", b.weight));

            // 4. [Open Nodes ->] Button
            boolean openHov = mouseX >= openBtnX && mouseX < openBtnX + openBtnW && mouseY >= ry + 3 && mouseY < ry + rowH - 5;
            StudioTheme.drawButton(graphics, font, Component.literal("§bOpen Nodes ->"), openBtnX, ry + 3, openBtnW, rowH - 8, openHov, true);

            if (hov) {
                graphics.setTooltipForNextFrame(
                        font,
                        List.of(
                                Component.literal("§b§l" + b.name + " §8(" + b.id + ")"),
                                Component.literal("§7Category: §f" + (b.importedHytale ? "Imported Hytale JSON" : b.category)
                                        + " §8| §7Weight: §e" + String.format(Locale.ROOT, "%.2f", b.weight)
                                        + " §8| §7Active: " + (b.enabled ? "§aYES" : "§cNO")),
                                Component.literal("§eClick 'Open Nodes ->' to edit this biome's 3D density & materials.")
                        ),
                        Optional.empty(), mouseX, mouseY
                );
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = x + width - pad - 5;
            graphics.fill(sbX, listY + 2, sbX + 3, listY + listH - 2, 0xFF08090D);
            int thumbH = Math.max(14, (listH - 4) * maxVis / Math.max(1, filtered.size()));
            int thumbY = listY + 2 + (int) (((listH - 4) - thumbH) * ((float) biomeScroll / maxScroll));
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, StudioTheme.PANEL_BORDER_LIGHT);
        }

        if (importerOpen) {
            renderHytaleImporterModal(graphics, font, mouseX, mouseY);
        }
    }

    public boolean mouseClickedBiomes(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        StudioState state = StudioState.get();

        if (importerOpen) {
            return handleHytaleImporterClick(mx, my);
        }

        if (biomeView == BiomeView.NODE_EDITOR) {
            if (my >= y && my < y + 22) {
                if (mx >= x + 6 && mx < x + 110) {
                    if (state.isDimensionGraphMode()) {
                        this.biomeView = BiomeView.BIOME_LIST;
                        this.dimensionView = DimensionView.DIMENSION_MENU;
                        if (switchTopTabCallback != null) switchTopTabCallback.accept(1);
                    } else {
                        this.biomeView = BiomeView.BIOME_LIST;
                    }
                    return true;
                }
                int expW = 68;
                int expX = x + width - expW - 6;
                int testW = 116;
                int testX = expX - testW - 4;
                if (mx >= testX && mx < testX + testW) {
                    if (switchTopTabCallback != null) switchTopTabCallback.accept(3);
                    return true;
                }
                if (mx >= expX && mx < expX + expW) {
                    exportCurrentBiomeJson();
                    return true;
                }
                return true;
            }
            return canvasTab.mouseClicked(event, doubleClick);
        }

        // Biome List Clicks
        DimensionStudioProfile dim = state.getActiveDimensionProfile();
        int pad = 8;
        int topY = y + 6;
        int barH = 18;
        int dimW = 104;
        int impW = 96;
        int catW = 92;
        int searchW = Math.max(70, (width - pad * 2) - dimW - impW - catW - 12);

        int dimX = x + pad;
        int searchX = dimX + dimW + 4;
        int catX = searchX + searchW + 4;
        int impX = catX + catW + 4;

        if (my >= topY && my < topY + barH) {
            // Click Dimension Switcher Button
            if (mx >= dimX && mx < dimX + dimW) {
                List<DimensionStudioProfile> allDims = new ArrayList<>(state.getDimensionProfiles());
                int curIdx = 0;
                for (int i = 0; i < allDims.size(); i++) {
                    if (allDims.get(i).dimensionId.equals(dim.dimensionId)) {
                        curIdx = i;
                        break;
                    }
                }
                int nextIdx = (curIdx + (button == 1 ? -1 : 1) + allDims.size()) % allDims.size();
                state.setActiveDimension(allDims.get(nextIdx).dimensionId);
                biomeScroll = 0;
                return true;
            }
            if (mx >= searchX && mx < searchX + searchW) {
                if (button == 1) biomeSearch = "";
                typingSearch = true;
                return true;
            }
            typingSearch = false;
            if (mx >= catX && mx < catX + catW) {
                categoryIdx = (categoryIdx + (button == 1 ? -1 : 1) + CATEGORIES.length) % CATEGORIES.length;
                biomeScroll = 0;
                return true;
            }
            if (mx >= impX && mx < impX + impW) {
                refreshHytaleImportCandidates();
                importerOpen = true;
                return true;
            }
        }

        typingSearch = false;

        int listY = topY + barH + 6;
        int listH = (y + height - pad) - listY;
        List<BiomeEntryState> filtered = getFilteredBiomes(dim);
        int rowH = 24;
        int maxVis = Math.max(1, (listH - 6) / rowH);
        int rowX = x + pad + 4;
        int rowW = width - (pad * 2) - 12;

        int openBtnW = 72;
        int openBtnX = rowX + rowW - openBtnW - 4;
        int weightW = 66;
        int weightX = openBtnX - weightW - 4;

        for (int i = 0; i < maxVis; i++) {
            int idx = i + biomeScroll;
            if (idx >= filtered.size()) break;
            BiomeEntryState b = filtered.get(idx);
            int ry = listY + 3 + i * rowH;

            if (my >= ry && my < ry + rowH - 2 && mx >= rowX && mx < rowX + rowW) {
                // Toggle [ON/OFF]
                if (mx >= rowX + 4 && mx < rowX + 28) {
                    b.enabled = !b.enabled;
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                // Weight Stepper
                if (mx >= weightX && mx < weightX + weightW) {
                    float delta = (mx < weightX + weightW / 2.0) ? -0.25f : 0.25f;
                    b.weight = Math.clamp(Math.round((b.weight + delta) * 4.0f) / 4.0f, 0.1f, 10.0f);
                    StudioLiveCompiler.applyDimensionProfileLive(dim);
                    return true;
                }
                // Clicking [Open Nodes ->] or the biome name opens the Node Editor for this biome
                if (mx >= openBtnX || mx >= rowX + 32) {
                    state.setActiveBiome(b.name);
                    state.setGraphMode(GraphMode.BIOME_GRAPH);
                    canvasTab.setPresetDropdownToBiome();
                    if (b.importedHytale && b.rawHytaleJson != null && !b.rawHytaleJson.isBlank()) {
                        StudioLiveCompiler.importHytaleBiomeJson(b.rawHytaleJson, canvasTab);
                    } else {
                        StudioLiveCompiler.loadBiomeIntoCanvas(b.name, canvasTab);
                    }
                    this.biomeView = BiomeView.NODE_EDITOR;
                    return true;
                }
            }
        }

        return false;
    }

    // =========================================================================
    // HYTALE BIOME JSON IMPORTER MODAL
    // =========================================================================

    private void renderHytaleImporterModal(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0xBB000000);

        int mw = Math.min(340, width - 16);
        int mh = Math.min(190, height - 12);
        int mx = x + (width - mw) / 2;
        int my = y + (height - mh) / 2;

        StudioTheme.drawBeveledPanel(graphics, mx, my, mw, mh, 0xF8181B28);
        graphics.text(font, "§d§lImport Hytale Biome JSON", mx + 10, my + 7, StudioTheme.TEXT_GOLD, true);

        int closeX = mx + mw - 22;
        boolean closeHov = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= my + 4 && mouseY < my + 18;
        StudioTheme.drawButton(graphics, font, Component.literal("X"), closeX, my + 4, 16, 14, closeHov, true);

        int clipY = my + 22;
        int clipW = mw - 20;
        boolean clipHov = mouseX >= mx + 10 && mouseX < mx + 10 + clipW && mouseY >= clipY && mouseY < clipY + 18;
        StudioTheme.drawButton(
                graphics, font,
                Component.literal("§aPaste Hytale Biome JSON from Clipboard"),
                mx + 10, clipY, clipW, 18, clipHov, true
        );

        int listY = clipY + 22;
        int listH = (my + mh) - listY - 8;
        StudioTheme.drawInsetWell(graphics, mx + 10, listY, mw - 20, listH, 0xFF0E1018);

        int rowH = 22;
        int maxVis = Math.max(1, (listH - 4) / rowH);
        int maxScroll = Math.max(0, importCandidates.size() - maxVis);
        importScroll = Math.clamp(importScroll, 0, maxScroll);

        for (int i = 0; i < maxVis; i++) {
            int idx = i + importScroll;
            if (idx >= importCandidates.size()) break;
            HytaleImportCandidate cand = importCandidates.get(idx);
            int ry = listY + 2 + i * rowH;
            boolean hov = mouseX >= mx + 12 && mouseX < mx + mw - 12 && mouseY >= ry && mouseY < ry + rowH - 2;

            graphics.fill(mx + 12, ry, mx + mw - 12, ry + rowH - 2, hov ? 0xFF253048 : 0xFF141824);
            graphics.text(font, "§f" + font.plainSubstrByWidth(cand.title(), mw - 115), mx + 18, ry + 6, StudioTheme.TEXT_WHITE, false);
            StudioTheme.drawButton(graphics, font, Component.literal("§aImport ->"), mx + mw - 86, ry + 2, 70, rowH - 6, hov, true);
        }
    }

    private boolean handleHytaleImporterClick(double mx, double my) {
        int mw = Math.min(340, width - 16);
        int mh = Math.min(190, height - 12);
        int modalX = x + (width - mw) / 2;
        int modalY = y + (height - mh) / 2;

        if (mx < modalX || mx >= modalX + mw || my < modalY || my >= modalY + mh) {
            importerOpen = false;
            return true;
        }
        if (mx >= modalX + mw - 22 && mx < modalX + mw - 6 && my >= modalY + 4 && my < modalY + 18) {
            importerOpen = false;
            return true;
        }

        int clipY = modalY + 22;
        if (mx >= modalX + 10 && mx < modalX + mw - 10 && my >= clipY && my < clipY + 18) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isBlank()) {
                String imported = StudioLiveCompiler.importHytaleBiomeJson(clip, canvasTab);
                if (imported != null) {
                    importerOpen = false;
                    this.biomeView = BiomeView.NODE_EDITOR;
                }
            }
            return true;
        }

        int listY = clipY + 22;
        int listH = (modalY + mh) - listY - 8;
        int rowH = 22;
        int maxVis = Math.max(1, (listH - 4) / rowH);

        for (int i = 0; i < maxVis; i++) {
            int idx = i + importScroll;
            if (idx >= importCandidates.size()) break;
            int ry = listY + 2 + i * rowH;
            if (my >= ry && my < ry + rowH - 2 && mx >= modalX + 12 && mx < modalX + mw - 12) {
                HytaleImportCandidate cand = importCandidates.get(idx);
                String imported = StudioLiveCompiler.importHytaleBiomeJson(cand.rawJson(), canvasTab);
                if (imported != null) {
                    importerOpen = false;
                    this.biomeView = BiomeView.NODE_EDITOR;
                }
                return true;
            }
        }
        return true;
    }

    // =========================================================================
    // INPUT DELEGATION
    // =========================================================================

    public void mouseReleased(MouseButtonEvent event) {
        if (biomeView == BiomeView.NODE_EDITOR) {
            canvasTab.mouseReleased(event);
        }
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (biomeView == BiomeView.NODE_EDITOR) {
            return canvasTab.mouseDragged(event, dx, dy);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int activeTopTab) {
        if (canvasTab.isStructurePickerOpen()) {
            return canvasTab.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (importerOpen) {
            importScroll = Math.max(0, importScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (activeTopTab == 2) {
            if (biomeView == BiomeView.NODE_EDITOR) {
                return canvasTab.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            biomeScroll = Math.max(0, biomeScroll - (int) Math.signum(scrollY) * 2);
            return true;
        }
        if (activeTopTab == 1 && (dimensionView == DimensionView.DIMENSION_MENU || dimensionView == DimensionView.DIMENSION_ZONES)) {
            int mapW = Math.min(130, Math.max(106, width / 3));
            int mapX = x + width - mapW - 8;
            if (mouseX >= mapX && mouseX < mapX + mapW) {
                float factor = scrollY > 0 ? 0.8f : 1.25f;
                dimRadarRadius = Math.clamp(dimRadarRadius * factor, 200.0f, 5000.0f);
                return true;
            }
            if (dimensionView == DimensionView.DIMENSION_ZONES) {
                zoneScroll = Math.max(0, zoneScroll - (int) Math.signum(scrollY));
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (canvasTab.isStructurePickerOpen()) {
            return canvasTab.keyPressed(event);
        }
        if (importerOpen && event.key() == 256) {
            importerOpen = false;
            return true;
        }
        if (typingSearch) {
            if (event.key() == 259 && !biomeSearch.isEmpty()) {
                biomeSearch = biomeSearch.substring(0, biomeSearch.length() - 1);
                biomeScroll = 0;
                return true;
            }
            if (event.key() == 256 || event.key() == 257) {
                typingSearch = false;
                return true;
            }
        }
        if (biomeView == BiomeView.NODE_EDITOR) {
            return canvasTab.keyPressed(event);
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (canvasTab.isStructurePickerOpen()) {
            return canvasTab.charTyped(event);
        }
        if (typingSearch) {
            char c = (char) event.codepoint();
            if (Character.isLetterOrDigit(c) || c == '_' || c == ' ' || c == ':') {
                biomeSearch += c;
                biomeScroll = 0;
                return true;
            }
        }
        if (biomeView == BiomeView.NODE_EDITOR) {
            return canvasTab.charTyped(event);
        }
        return false;
    }

    // =========================================================================
    // SHARED HELPERS
    // =========================================================================

    private void renderStepper(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int bx,
            int by,
            int bw,
            int bh,
            String label
    ) {
        StudioTheme.drawInsetWell(graphics, bx, by, bw, bh, 0xFF0E1018);
        boolean lHov = mouseX >= bx && mouseX < bx + 13 && mouseY >= by && mouseY < by + bh;
        boolean rHov = mouseX >= bx + bw - 13 && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        graphics.fill(bx + 1, by + 1, bx + 13, by + bh - 1, lHov ? 0xFF32405A : 0xFF1A1E2B);
        graphics.fill(bx + bw - 13, by + 1, bx + bw - 1, by + bh - 1, rHov ? 0xFF32405A : 0xFF1A1E2B);
        graphics.centeredText(font, "-", bx + 7, by + (bh - 8) / 2, lHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY);
        graphics.centeredText(font, "+", bx + bw - 7, by + (bh - 8) / 2, rHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY);
        graphics.centeredText(font, font.plainSubstrByWidth(label, bw - 28), bx + bw / 2, by + (bh - 8) / 2, StudioTheme.TEXT_WHITE);
    }

    private List<BiomeEntryState> getFilteredBiomes(DimensionStudioProfile dim) {
        List<BiomeEntryState> out = new ArrayList<>();
        String q = biomeSearch.trim().toLowerCase(Locale.ROOT);
        String cat = CATEGORIES[categoryIdx];
        for (BiomeEntryState b : dim.biomes) {
            if (!"All".equals(cat)) {
                if ("Imported Hytale".equals(cat) && !b.importedHytale) continue;
                if (!"Imported Hytale".equals(cat) && !b.category.equalsIgnoreCase(cat)) continue;
            }
            if (q.isEmpty() || b.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(b);
            }
        }
        return out;
    }

    private void applyZonesTemplate(DimensionStudioProfile dim, int templateIdx) {
        dim.zones.clear();
        dim.rings.clear();
        dim.landmarks.clear();
        if (templateIdx == 0) {
            dim.layoutMode = "Infinite Voronoi Continent";
            dim.boundsRadius = 0.0f;
            dim.blendFalloff = 64.0f;
            dim.outsideFallback = OutsideBoundsFallback.DEFAULT_BIOMES;
            dimRadarRadius = 900.0f;
        } else if (templateIdx == 1) {
            dim.layoutMode = "Spawn Island + Voronoi Continent";
            dim.boundsRadius = 640.0f;
            dim.blendFalloff = 64.0f;
            dim.outsideFallback = OutsideBoundsFallback.DEFAULT_BIOMES;
            dim.zones.add(new EditableZoneRule("spawn_island_core", 0.0f, 220.0f, 40.0f, ZoneMode.ISLAND_PLATEAU, "TemperateForest", 72.0f, false));
            dim.zones.add(new EditableZoneRule("spawn_ocean_moat", 220.0f, 480.0f, 44.0f, ZoneMode.BIOME_OVERRIDE, "Ocean", 45.0f, false));
            dim.landmarks.add(new EditableLandmarkRule("northern_watchtower", "spawn_island_core", 120.0f, 195.0f, "worldgenv2:watchtower_01", 1, true, 3.5f));
            dim.landmarks.add(new EditableLandmarkRule("eastern_waystation", "spawn_island_core", 130.0f, 210.0f, "worldgenv2:waystation_inn", 1, true, 3.0f));
            dimRadarRadius = 750.0f;
        } else if (templateIdx == 2) {
            dim.layoutMode = "Concentric Rings & Archipelago";
            dim.boundsRadius = 30000.0f;
            dim.blendFalloff = 96.0f;
            dim.outsideFallback = OutsideBoundsFallback.VOID;
            dim.zones.add(new EditableZoneRule("central_end_island", 0.0f, 190.0f, 32.0f, ZoneMode.ISLAND_PLATEAU, "StonyPeaks", 62.0f, true));
            dim.zones.add(new EditableZoneRule("end_void_moat", 190.0f, 960.0f, 36.0f, ZoneMode.VOID_MOAT, "WindsweptHills", 0.0f, true));
            dim.zones.add(new EditableZoneRule("outer_end_islands", 960.0f, 30000.0f, 64.0f, ZoneMode.OUTER_ISLANDS, "AridDunes", 60.0f, true));
            dim.rings.add(new EditableRingRule("obsidian_towers", 43.0f, 10, 3, 76, 103, "minecraft:obsidian", true));
            dim.landmarks.add(new EditableLandmarkRule("outer_end_cities", "outer_end_islands", 1020.0f, 1400.0f, "minecraft:end_city", 3, false, 0.0f));
            dimRadarRadius = 1500.0f;
        } else {
            dim.layoutMode = "Bounded Skyblock -> Void";
            dim.boundsRadius = 340.0f;
            dim.blendFalloff = 48.0f;
            dim.outsideFallback = OutsideBoundsFallback.VOID;
            dim.zones.add(new EditableZoneRule("skyblock_sanctuary", 0.0f, 150.0f, 30.0f, ZoneMode.ISLAND_PLATEAU, "TemperateForest", 74.0f, true));
            dim.landmarks.add(new EditableLandmarkRule("sanctuary_shrine", "skyblock_sanctuary", 40.0f, 110.0f, "worldgenv2:ancient_shrine", 1, true, 3.0f));
            dimRadarRadius = 450.0f;
        }
    }

    private void exportWorldPack() {
        try {
            StudioState state = StudioState.get();
            Path exportDir = Path.of("config", "worldgenv2", "packs");
            Files.createDirectories(exportDir);
            Path packFile = exportDir.resolve("worldgenv2_addon_pack.json");
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"packName\": \"WorldGen V2 Addon Pack\",\n");
            sb.append("  \"version\": \"2.0.0\",\n");
            sb.append("  \"worldPreset\": \"").append(state.getActiveWorldPreset()).append("\",\n");
            sb.append("  \"dimensions\": [\n");
            List<DimensionStudioProfile> dims = new ArrayList<>(state.getDimensionProfiles());
            for (int i = 0; i < dims.size(); i++) {
                DimensionStudioProfile d = dims.get(i);
                sb.append("    {\n");
                sb.append("      \"id\": \"").append(d.dimensionId).append("\",\n");
                sb.append("      \"name\": \"").append(d.displayName).append("\",\n");
                sb.append("      \"boundsRadius\": ").append(d.boundsRadius).append(",\n");
                sb.append("      \"outsideFallback\": \"").append(d.outsideFallback.name()).append("\",\n");
                sb.append("      \"zones\": ").append(d.zones.size()).append(",\n");
                sb.append("      \"landmarks\": ").append(d.landmarks.size()).append(",\n");
                sb.append("      \"biomes\": ").append(d.biomes.size()).append("\n");
                sb.append("    }").append(i < dims.size() - 1 ? "," : "").append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            Files.writeString(packFile, sb.toString(), StandardCharsets.UTF_8);
            state.setStatusMessage("Exported pack to config/worldgenv2/packs/worldgenv2_addon_pack.json");
        } catch (Exception e) {
            StudioState.get().setStatusMessage("Export failed: " + e.getMessage());
        }
    }

    private void importWorldPack() {
        try {
            Path packFile = Path.of("config", "worldgenv2", "packs", "worldgenv2_addon_pack.json");
            if (Files.exists(packFile)) {
                String json = Files.readString(packFile, StandardCharsets.UTF_8);
                StudioState.get().setStatusMessage("Imported config/worldgenv2/packs/worldgenv2_addon_pack.json");
            } else {
                Path exportDir = Path.of("config", "worldgenv2", "packs");
                Files.createDirectories(exportDir);
                exportWorldPack();
                StudioState.get().setStatusMessage("Loaded default pack from config/worldgenv2/packs/");
            }
        } catch (Exception e) {
            StudioState.get().setStatusMessage("Import failed: " + e.getMessage());
        }
    }

    private void exportCurrentBiomeJson() {
        try {
            StudioState state = StudioState.get();
            String rawBiome = state.getActiveBiome();
            String safeName = rawBiome.contains(":") ? rawBiome.substring(rawBiome.indexOf(':') + 1) : rawBiome;
            Path exportDir = Path.of("config", "worldgenv2", "biomes");
            Files.createDirectories(exportDir);
            Path outFile = exportDir.resolve(safeName + ".json");
            Files.writeString(outFile, canvasTab.exportGraphToJson(), StandardCharsets.UTF_8);
            state.setStatusMessage("Exported config/worldgenv2/biomes/" + safeName + ".json");
        } catch (Exception ignored) {
        }
    }

    private static String shortId(String id) {
        if (id == null) return "stone";
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    private void ensureSampleHytaleJsonsExist() {
        try {
            Path dir = Path.of("config", "worldgenv2", "hytale_imports");
            Files.createDirectories(dir);

            writeSampleIfMissing(dir.resolve("Hytale_Emerald_Grove.json"), """
                    {
                      "name": "hytale:EmeraldGrove",
                      "weight": 2.2,
                      "sizeModifier": 1.3,
                      "defaultTransitionDistance": 42.0,
                      "seaLevel": 63,
                      "defaultBlock": "minecraft:stone",
                      "fluidBlock": "minecraft:water",
                      "terrainDensity": {
                        "type": "GradientWarp",
                        "warpStrength": 16.0,
                        "warpSource": {
                          "type": "SimplexNoise2D",
                          "frequency": 0.007,
                          "octaves": 2,
                          "amplitude": 1.0
                        },
                        "input": {
                          "type": "Sum",
                          "inputs": [
                            { "type": "VerticalGradient", "baseHeight": 76.0, "scale": 28.0 },
                            { "type": "SimplexNoise2D", "frequency": 0.0045, "octaves": 4, "amplitude": 0.65 },
                            {
                              "type": "YSampled",
                              "yInterval": 8,
                              "input": { "type": "SimplexNoise3D", "frequency": 0.012, "octaves": 3, "amplitude": 0.55 }
                            }
                          ]
                        }
                      },
                      "materialProvider": {
                        "type": "SpaceAndDepth",
                        "maxSlope": 46.0,
                        "cliffMaterial": "minecraft:mossy_cobblestone",
                        "layers": [
                          { "material": "minecraft:grass_block", "thickness": 1 },
                          { "material": "minecraft:rooted_dirt", "thickness": 4 },
                          { "material": "minecraft:stone", "thickness": 999 }
                        ]
                      }
                    }
                    """);

            writeSampleIfMissing(dir.resolve("Hytale_Howling_Sands.json"), """
                    {
                      "name": "hytale:HowlingSands",
                      "weight": 1.8,
                      "sizeModifier": 1.4,
                      "defaultTransitionDistance": 48.0,
                      "seaLevel": 63,
                      "defaultBlock": "minecraft:sandstone",
                      "fluidBlock": "minecraft:water",
                      "terrainDensity": {
                        "type": "Sum",
                        "inputs": [
                          { "type": "VerticalGradient", "baseHeight": 70.0, "scale": 22.0 },
                          { "type": "SimplexNoise2D", "frequency": 0.0035, "octaves": 3, "amplitude": 0.75 },
                          { "type": "SimplexNoise3D", "frequency": 0.009, "octaves": 2, "amplitude": 0.35 }
                        ]
                      },
                      "materialProvider": {
                        "type": "SpaceAndDepth",
                        "maxSlope": 40.0,
                        "cliffMaterial": "minecraft:smooth_sandstone",
                        "layers": [
                          { "material": "minecraft:sand", "thickness": 3 },
                          { "material": "minecraft:sandstone", "thickness": 6 },
                          { "material": "minecraft:stone", "thickness": 999 }
                        ]
                      }
                    }
                    """);

            writeSampleIfMissing(dir.resolve("Hytale_Borea_Frost_Peaks.json"), """
                    {
                      "name": "hytale:BoreaFrostPeaks",
                      "weight": 1.6,
                      "sizeModifier": 1.2,
                      "defaultTransitionDistance": 36.0,
                      "seaLevel": 63,
                      "defaultBlock": "minecraft:stone",
                      "fluidBlock": "minecraft:water",
                      "terrainDensity": {
                        "type": "Sum",
                        "inputs": [
                          { "type": "VerticalGradient", "baseHeight": 98.0, "scale": 38.0 },
                          { "type": "SimplexNoise2D", "frequency": 0.006, "octaves": 4, "amplitude": 1.1 },
                          { "type": "SimplexNoise3D", "frequency": 0.014, "octaves": 3, "amplitude": 0.6 }
                        ]
                      },
                      "materialProvider": {
                        "type": "SpaceAndDepth",
                        "maxSlope": 38.0,
                        "cliffMaterial": "minecraft:packed_ice",
                        "layers": [
                          { "material": "minecraft:snow_block", "thickness": 2 },
                          { "material": "minecraft:calcite", "thickness": 4 },
                          { "material": "minecraft:stone", "thickness": 999 }
                        ]
                      }
                    }
                    """);

            writeSampleIfMissing(dir.resolve("Hytale_Devastated_Volcanic.json"), """
                    {
                      "name": "hytale:DevastatedLands",
                      "weight": 1.4,
                      "sizeModifier": 1.1,
                      "defaultTransitionDistance": 32.0,
                      "seaLevel": 62,
                      "defaultBlock": "minecraft:basalt",
                      "fluidBlock": "minecraft:lava",
                      "terrainDensity": {
                        "type": "Sum",
                        "inputs": [
                          { "type": "VerticalGradient", "baseHeight": 68.0, "scale": 24.0 },
                          { "type": "SimplexNoise2D", "frequency": 0.007, "octaves": 3, "amplitude": 0.8 },
                          { "type": "CellNoise3D", "frequency": 0.015, "amplitude": 0.5 }
                        ]
                      },
                      "materialProvider": {
                        "type": "SpaceAndDepth",
                        "maxSlope": 45.0,
                        "cliffMaterial": "minecraft:magma_block",
                        "layers": [
                          { "material": "minecraft:blackstone", "thickness": 2 },
                          { "material": "minecraft:basalt", "thickness": 5 },
                          { "material": "minecraft:deepslate", "thickness": 999 }
                        ]
                      }
                    }
                    """);
        } catch (Exception ignored) {
        }
    }

    private static void writeSampleIfMissing(Path path, String content) {
        try {
            if (!Files.exists(path)) {
                Files.writeString(path, content, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private void refreshHytaleImportCandidates() {
        importCandidates.clear();
        ensureSampleHytaleJsonsExist();
        scanDirectoryForHytaleJsons(Path.of("config", "worldgenv2", "hytale_imports"));
        scanDirectoryForHytaleJsons(Path.of("config", "worldgenv2", "biomes"));
    }

    private void scanDirectoryForHytaleJsons(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String fname = file.getFileName().toString().replace(".json", "");
                    importCandidates.add(new HytaleImportCandidate(fname, file, json));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
