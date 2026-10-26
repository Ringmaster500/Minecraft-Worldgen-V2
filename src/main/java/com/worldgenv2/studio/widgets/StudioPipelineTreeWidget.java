package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pipeline Hierarchy Tree Widget (Studio Left Pane).
 * Displays the core world generation stages in the authoring canvas:
 * <ul>
 *   <li>1. Base Terrain & Density</li>
 *   <li>2. Biome Climate Canvas (Drillable into biomes)</li>
 *   <li>3. Surface Strata & Materials (Slope gates & rock layers)</li>
 *   <li>4. Features & Foliage (Scatter rules & jigsaw props)</li>
 *   <li>5. Dimensions & Spatial Bounds</li>
 * </ul>
 */
public class StudioPipelineTreeWidget {

    public enum PipelineStage {
        BASE_TERRAIN("Base Terrain & Density", "Noise generators, height curves & sea level", 0xFF33AAEE),
        BIOME_CANVAS("Biome Climate Canvas", "Multi-noise Voronoi & climate space", 0xFF44CC44),
        SURFACE_STRATA("Surface Strata & Materials", "Slope angle gates & rock banding", 0xFFA044DD),
        DECORATORS("Features & Foliage", "Tree placement, rocks & flora scatter", 0xFFE6C619),
        DIMENSIONS("Dimensions & Bounds", "Overworld, Nether, End & custom bounds", 0xFFFFAA00);

        public final String title;
        public final String subtitle;
        public final int color;

        PipelineStage(String title, String subtitle, int color) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
        }
    }

    private int x;
    private int y;
    private int width;
    private int height;

    private PipelineStage selectedStage = PipelineStage.BASE_TERRAIN;
    private String selectedBiomeName = "TemperateForest";
    private boolean biomesExpanded = true;

    private final List<String> availableBiomes = new ArrayList<>(List.of(
        "TemperateForest", "SunlitPlains", "AridDunes", "AlpinePeaks", "OldGrowthGrove"
    ));

    private Consumer<PipelineStage> onStageSelectCallback;
    private Consumer<String> onBiomeSelectCallback;

    public StudioPipelineTreeWidget() {}

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setCallbacks(Consumer<PipelineStage> stageCb, Consumer<String> biomeCb) {
        this.onStageSelectCallback = stageCb;
        this.onBiomeSelectCallback = biomeCb;
    }

    public PipelineStage getSelectedStage() {
        return selectedStage;
    }

    public String getSelectedBiomeName() {
        return selectedBiomeName;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xEE14141A);

        // Header Title
        graphics.text(font, "PIPELINE STAGES", x + 8, y + 6, StudioTheme.TEXT_GOLD, true);
        graphics.horizontalLine(x + 6, x + width - 6, y + 17, StudioTheme.PANEL_BORDER_MID);

        int itemY = y + 22;
        int itemH = 28;

        for (PipelineStage stage : PipelineStage.values()) {
            boolean isSelected = (stage == selectedStage);
            int bg = isSelected ? 0xFF2A2A38 : 0x00000000;

            if (isSelected) {
                graphics.fill(x + 4, itemY, x + width - 4, itemY + itemH, bg);
                graphics.verticalLine(x + 4, itemY, itemY + itemH, stage.color);
            }

            // Bullet icon
            graphics.fill(x + 8, itemY + 6, x + 12, itemY + 10, stage.color);
            graphics.text(font, stage.title, x + 16, itemY + 4, isSelected ? StudioTheme.TEXT_WHITE : 0xFFCCCCCC, true);
            graphics.text(font, stage.subtitle, x + 16, itemY + 15, StudioTheme.TEXT_DARK_GRAY, true);

            itemY += itemH + 2;

            // Expand biomes list under BIOME_CANVAS
            if (stage == PipelineStage.BIOME_CANVAS && biomesExpanded) {
                for (String biome : availableBiomes) {
                    boolean isBiomeSelected = (selectedStage == PipelineStage.BIOME_CANVAS && biome.equalsIgnoreCase(selectedBiomeName));
                    int bBg = isBiomeSelected ? 0xFF223322 : 0x00000000;

                    if (isBiomeSelected) {
                        graphics.fill(x + 18, itemY, x + width - 6, itemY + 14, bBg);
                    }

                    graphics.text(font, "└ " + biome, x + 20, itemY + 3, isBiomeSelected ? 0xFF55FF55 : 0xFFAAAAAA, true);
                    itemY += 15;
                }
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.x() < x || event.x() > x + width || event.y() < y || event.y() > y + height) {
            return false;
        }

        int itemY = y + 22;
        int itemH = 28;

        for (PipelineStage stage : PipelineStage.values()) {
            if (event.y() >= itemY && event.y() <= itemY + itemH) {
                selectedStage = stage;
                if (stage == PipelineStage.BIOME_CANVAS) {
                    biomesExpanded = !biomesExpanded;
                }
                if (onStageSelectCallback != null) {
                    onStageSelectCallback.accept(stage);
                }
                return true;
            }

            itemY += itemH + 2;

            if (stage == PipelineStage.BIOME_CANVAS && biomesExpanded) {
                for (String biome : availableBiomes) {
                    if (event.y() >= itemY && event.y() <= itemY + 14) {
                        selectedStage = PipelineStage.BIOME_CANVAS;
                        selectedBiomeName = biome;
                        if (onBiomeSelectCallback != null) {
                            onBiomeSelectCallback.accept(biome);
                        }
                        return true;
                    }
                    itemY += 15;
                }
            }
        }

        return false;
    }
}
