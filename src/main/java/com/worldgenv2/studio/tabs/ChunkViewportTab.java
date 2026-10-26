package com.worldgenv2.studio.tabs;

import com.worldgenv2.studio.StudioLiveCompiler;
import com.worldgenv2.studio.StudioState;
import com.worldgenv2.studio.StudioTheme;
import com.worldgenv2.worldstructure.TileBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

/**
 * Streamlined Live Testing ("Dev") Chunk Selector & 3D In-World Preview Tab.
 * <ul>
 *   <li>Left panel renders a color-coded 2D Biome & Chunk Minimap around the player, showing
 *       exact biome boundaries, player position, and selected dev chunks.</li>
 *   <li>Clicking any chunk on the minimap highlights it in 3D in the world AND automatically
 *       loads that chunk's {@link TileBiome} node graph into {@link NodeCanvasTab}.</li>
 *   <li>Right viewport is completely see-through to the live 3D Minecraft world so the creator
 *       can inspect the 3D chunk boundaries and live-regenerated terrain directly.</li>
 * </ul>
 */
public class ChunkViewportTab {

    private int x;
    private int y;
    private int width;
    private int height;

    private final NodeCanvasTab canvasTab;
    private final Consumer<String> onBiomeSelectedCallback;
    private final Runnable onConfirmSwitchToEditor;

    private final int gridDim = 9; // 9x9 chunk minimap around center
    private boolean isDraggingGrid = false;
    private boolean dragTargetSelect = true;

    public ChunkViewportTab(
        NodeCanvasTab canvasTab,
        Consumer<String> onBiomeSelectedCallback,
        Runnable onConfirmSwitchToEditor
    ) {
        this.canvasTab = canvasTab;
        this.onBiomeSelectedCallback = onBiomeSelectedCallback;
        this.onConfirmSwitchToEditor = onConfirmSwitchToEditor;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void onTabOpened() {
        Minecraft mc = Minecraft.getInstance();
        StudioState state = StudioState.get();
        if (mc.player != null) {
            BlockPos bp = mc.player.blockPosition();
            ChunkPos playerChunk = new ChunkPos(bp.getX() >> 4, bp.getZ() >> 4);
            if (state.getSelectedChunks().isEmpty()) {
                state.setTestingArea(playerChunk, 1);
            }
        }
        state.setChunkHighlightActive(true);
    }

    public void loadChunkBiomeIntoEditor(int chunkX, int chunkZ) {
        TileBiome biome = StudioLiveCompiler.queryChunkPrimaryBiome(chunkX, chunkZ);
        if (biome != null) {
            StudioState.get().setActiveBiome(biome.name());
            if (canvasTab != null) {
                canvasTab.setPresetDropdownToBiome();
                StudioLiveCompiler.loadBiomeIntoCanvas(biome.name(), canvasTab);
            }
            if (onBiomeSelectedCallback != null) {
                onBiomeSelectedCallback.accept(biome.name());
            }
            StudioState.get().setStatusMessage("Loaded biome [" + biome.name() + "] from chunk (" + chunkX + ", " + chunkZ + ").");
        }
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioState state = StudioState.get();

        // Responsive Left Minimap Sidebar vs Right See-Through 3D World Viewport
        int sidebarW = Math.min(218, Math.max(174, width * 45 / 100));
        int sidebarX = x + 4;
        int sidebarY = y + 4;
        int sidebarH = height - 8;

        StudioTheme.drawBeveledPanel(graphics, sidebarX, sidebarY, sidebarW, sidebarH, 0xEC161620);

        // 1. Color-Coded 2D Biome & Chunk Minimap
        int maxMapSize = Math.min(sidebarW - 68, Math.max(72, sidebarH - 88));
        int cellSize = Math.max(9, maxMapSize / gridDim);
        int mapSize = cellSize * gridDim;
        int mapX = sidebarX + 8;
        int mapY = sidebarY + 16;

        graphics.text(font, "2D Biome & Chunk Minimap:", mapX, sidebarY + 5, StudioTheme.TEXT_GOLD, true);
        StudioTheme.drawInsetWell(graphics, mapX - 1, mapY - 1, mapSize + 2, mapSize + 2, 0xFF0E0E14);

        ChunkPos center = state.getCenterChunk();
        int half = gridDim / 2;
        ChunkPos hoveredGridChunk = null;
        TileBiome hoveredBiome = null;

        Minecraft mc = Minecraft.getInstance();
        ChunkPos playerChunk = (mc.player != null)
            ? new ChunkPos(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4)
            : center;

        for (int r = 0; r < gridDim; r++) {
            for (int c = 0; c < gridDim; c++) {
                int cx = center.x() - half + c;
                int cz = center.z() - half + r;

                int cellX = mapX + (c * cellSize);
                int cellY = mapY + (r * cellSize);

                TileBiome cellBiome = StudioLiveCompiler.queryChunkPrimaryBiome(cx, cz);
                boolean isVoid = StudioLiveCompiler.getActiveWorldStructure().isVoidColumn((cx << 4) + 8, (cz << 4) + 8);
                int biomeColor = isVoid
                        ? 0xFF0B0716
                        : ((cellBiome != null) ? StudioLiveCompiler.getBiomeMapColor(cellBiome.id()) : 0xFF223328);

                boolean isSelected = state.isChunkSelected(cx, cz);
                boolean isPlayerHere = (cx == playerChunk.x() && cz == playerChunk.z());
                boolean isHovered = mouseX >= cellX && mouseX < cellX + cellSize && mouseY >= cellY && mouseY < cellY + cellSize;

                if (isHovered) {
                    hoveredGridChunk = new ChunkPos(cx, cz);
                    hoveredBiome = cellBiome;
                }

                // Draw biome-colored cell background
                graphics.fill(cellX + 1, cellY + 1, cellX + cellSize, cellY + cellSize, biomeColor);

                // Selected overlay tint & neon cyan outline
                if (isSelected) {
                    graphics.fill(cellX + 1, cellY + 1, cellX + cellSize, cellY + cellSize, 0x5500FFAA);
                    graphics.outline(cellX, cellY, cellSize, cellSize, 0xFF00FFAA);
                } else if (isHovered) {
                    graphics.outline(cellX, cellY, cellSize, cellSize, 0xFFFFFF55);
                }

                // Player position marker
                if (isPlayerHere) {
                    int midX = cellX + cellSize / 2;
                    int midY = cellY + cellSize / 2;
                    graphics.fill(midX - 2, midY - 2, midX + 3, midY + 3, 0xFF000000);
                    graphics.fill(midX - 1, midY - 1, midX + 2, midY + 2, 0xFFFFDD00);
                }
            }
        }

        // Update 3D in-world gold hover preview box
        state.setHoveredChunk(hoveredGridChunk);

        // Quick Area Preset Column beside the 2D minimap
        int rightColX = mapX + mapSize + 6;
        int rightColW = sidebarX + sidebarW - rightColX - 6;
        if (rightColW >= 44) {
            int py = mapY;
            int bh = 16;
            drawBtn(graphics, font, "1x1", rightColX, py, rightColW, bh, mouseX, mouseY);
            drawBtn(graphics, font, "3x3", rightColX, py + 18, rightColW, bh, mouseX, mouseY);
            drawBtn(graphics, font, "5x5", rightColX, py + 36, rightColW, bh, mouseX, mouseY);
            drawBtn(graphics, font, "Here", rightColX, py + 54, rightColW, bh, mouseX, mouseY);
            if (py + 72 + bh <= mapY + mapSize + 4) {
                drawBtn(graphics, font, "Clear", rightColX, py + 72, rightColW, bh, mouseX, mouseY);
            }
        }

        // Bottom Controls inside Left Sidebar
        int botY = mapY + mapSize + 6;
        int fullW = sidebarW - 16;

        // Line 1: Active Biome & Selected Chunks Count
        String biomeStr = "Biome: §e" + state.getActiveBiome() + " §7(" + state.getSelectedChunks().size() + " chunks)";
        graphics.text(font, font.plainSubstrByWidth(biomeStr, fullW), mapX, botY, StudioTheme.TEXT_WHITE, true);
        botY += 12;

        // 1. Toggle 3D In-World Highlight Button
        int btnH = 18;
        boolean hlOn = state.isChunkHighlightActive();
        String hlLabel = hlOn ? "3D Highlight: ON (Click to Hide)" : "3D Highlight: OFF (Click to Show)";
        boolean hlHov = mouseX >= mapX && mouseX < mapX + fullW && mouseY >= botY && mouseY < botY + btnH;
        int hlBg = hlOn ? (hlHov ? 0xFF1F6E58 : 0xFF165242) : (hlHov ? 0xFF383848 : 0xFF252532);
        StudioTheme.drawBeveledPanel(graphics, mapX, botY, fullW, btnH, hlBg);
        graphics.centeredText(font, font.plainSubstrByWidth(hlLabel, fullW - 8), mapX + fullW / 2, botY + 5,
                hlOn ? 0xFF55FFCC : StudioTheme.TEXT_GRAY);
        botY += btnH + 4;

        // 2. Confirm & Edit in Node Graph Button (Hides 3D highlight & opens Node Editor for this chunk's biome)
        int confH = Math.min(20, Math.max(16, (sidebarY + sidebarH) - botY - 4));
        if (confH >= 14) {
            boolean confHov = mouseX >= mapX && mouseX < mapX + fullW && mouseY >= botY && mouseY < botY + confH;
            int confBg = confHov ? 0xFF2D6A32 : 0xFF1E4C22;
            StudioTheme.drawBeveledPanel(graphics, mapX, botY, fullW, confH, confBg);
            String confText = font.plainSubstrByWidth("Confirm & Edit Biome Graph ->", fullW - 8);
            graphics.centeredText(font, confText, mapX + fullW / 2, botY + (confH - 8) / 2, 0xFF88FF88);
        }

        // Right Pane: Transparent See-Through 3D World Preview Frame
        int viewX = sidebarX + sidebarW + 6;
        int viewY = sidebarY;
        int viewW = width - (viewX - x) - 4;
        int viewH = sidebarH;

        if (viewW > 60) {
            graphics.outline(viewX, viewY, viewW, viewH, state.isChunkHighlightActive() ? 0xAA00FFAA : 0x88444455);

            // Top compact status bar inside 3D preview
            StudioTheme.drawBeveledPanel(graphics, viewX + 4, viewY + 4, viewW - 8, 26, 0xCC12121A);
            String banner1 = font.plainSubstrByWidth("§6Live 3D World Preview §7— Active Biome: §b" + state.getActiveBiome(), viewW - 16);
            String banner2 = font.plainSubstrByWidth("§7Left-Click / Drag to select test chunks • Right-Click to load biome graph • §eH§7: 3D bounds", viewW - 16);
            graphics.text(font, banner1, viewX + 8, viewY + 7, StudioTheme.TEXT_WHITE, true);
            graphics.text(font, banner2, viewX + 8, viewY + 18, StudioTheme.TEXT_GRAY, false);
        }

        if (hoveredGridChunk != null) {
            String bName = (hoveredBiome != null) ? hoveredBiome.name() : "Unknown";
            graphics.setTooltipForNextFrame(
                    Component.literal("§eChunk [" + hoveredGridChunk.x() + ", " + hoveredGridChunk.z() + "] §7— Biome: §b" + bName + "\n§aLeft-Click: Select Test Chunk §7| §eRight-Click: Load Biome Graph"),
                    mouseX, mouseY
            );
        }
    }

    private void drawBtn(GuiGraphicsExtractor graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        String clamped = font.plainSubstrByWidth(text, bw - 6);
        StudioTheme.drawButton(graphics, font, Component.literal(clamped), bx, by, bw, bh, hov, true);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        StudioState state = StudioState.get();

        int sidebarW = Math.min(218, Math.max(174, width * 45 / 100));
        int sidebarX = x + 4;
        int sidebarY = y + 4;
        int sidebarH = height - 8;

        int maxMapSize = Math.min(sidebarW - 68, Math.max(72, sidebarH - 88));
        int cellSize = Math.max(9, maxMapSize / gridDim);
        int mapSize = cellSize * gridDim;
        int mapX = sidebarX + 8;
        int mapY = sidebarY + 16;

        // 1. 2D Minimap Click & Drag -> Left click selects test chunk, Right click loads graph!
        if (mx >= mapX && mx < mapX + mapSize && my >= mapY && my < mapY + mapSize) {
            int c = (int) ((mx - mapX) / cellSize);
            int r = (int) ((my - mapY) / cellSize);
            int half = gridDim / 2;
            int cx = state.getCenterChunk().x() - half + c;
            int cz = state.getCenterChunk().z() - half + r;

            // Right-click: Load this specific chunk's biome graph into the Node Canvas
            if (event.button() == 1) { // Right click
                loadChunkBiomeIntoEditor(cx, cz);
                return true;
            }

            // Left-click: Toggle or paint test chunks without modifying or wiping the graph
            boolean currentlySel = state.isChunkSelected(cx, cz);
            this.dragTargetSelect = !currentlySel;
            state.setChunkSelected(cx, cz, this.dragTargetSelect);
            this.isDraggingGrid = true;
            return true;
        }

        // 2. Quick Preset Column (1x1, 3x3, 5x5, Here, Clear)
        int rightColX = mapX + mapSize + 6;
        int rightColW = sidebarX + sidebarW - rightColX - 6;
        if (rightColW >= 44 && mx >= rightColX && mx < rightColX + rightColW) {
            int py = mapY;
            int bh = 16;
            if (my >= py && my < py + bh) {
                state.setTestingArea(state.getCenterChunk(), 0);
                return true;
            }
            if (my >= py + 18 && my < py + 18 + bh) {
                state.setTestingArea(state.getCenterChunk(), 1);
                return true;
            }
            if (my >= py + 36 && my < py + 36 + bh) {
                state.setTestingArea(state.getCenterChunk(), 2);
                return true;
            }
            if (my >= py + 54 && my < py + 54 + bh) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    BlockPos bp = mc.player.blockPosition();
                    ChunkPos playerChunk = new ChunkPos(bp.getX() >> 4, bp.getZ() >> 4);
                    state.centerOnPlayer(playerChunk);
                }
                return true;
            }
            if (my >= py + 72 && my < py + 72 + bh) {
                state.clearSelectedChunks();
                return true;
            }
        }

        // 3. Bottom Sidebar Controls
        int botY = mapY + mapSize + 18;
        int fullW = sidebarW - 16;
        int btnH = 18;

        // 3D Highlight Toggle Button
        if (mx >= mapX && mx < mapX + fullW && my >= botY && my < botY + btnH) {
            state.toggleChunkHighlight();
            return true;
        }
        botY += btnH + 4;

        // Confirm & Edit Biome Graph Button
        int confH = Math.min(20, Math.max(16, (sidebarY + sidebarH) - botY - 4));
        if (confH >= 14 && mx >= mapX && mx < mapX + fullW && my >= botY && my < botY + confH) {
            state.confirmChunks();
            if (onConfirmSwitchToEditor != null) {
                onConfirmSwitchToEditor.run();
            }
            return true;
        }

        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!isDraggingGrid) return false;

        double mx = event.x();
        double my = event.y();
        StudioState state = StudioState.get();

        int sidebarW = Math.min(218, Math.max(174, width * 45 / 100));
        int sidebarX = x + 4;
        int sidebarY = y + 4;
        int sidebarH = height - 8;

        int maxMapSize = Math.min(sidebarW - 68, Math.max(72, sidebarH - 88));
        int cellSize = Math.max(9, maxMapSize / gridDim);
        int mapSize = cellSize * gridDim;
        int mapX = sidebarX + 8;
        int mapY = sidebarY + 16;

        if (mx >= mapX && mx < mapX + mapSize && my >= mapY && my < mapY + mapSize) {
            int c = (int) ((mx - mapX) / cellSize);
            int r = (int) ((my - mapY) / cellSize);
            int half = gridDim / 2;
            int cx = state.getCenterChunk().x() - half + c;
            int cz = state.getCenterChunk().z() - half + r;
            state.setChunkSelected(cx, cz, this.dragTargetSelect);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingGrid = false;
        return false;
    }
}
