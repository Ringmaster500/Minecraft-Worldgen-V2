package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asset Shelf Widget (Studio Bottom Pane).
 * Provides a slide-up shelf for registered Prefabs, Block Palettes,
 * and Reusable Sub-Graph Macros.
 */
public class StudioAssetShelfWidget {

    public enum ShelfTab {
        PREFABS("Structures & Prefabs"),
        PALETTES("Block Palettes"),
        MACROS("Sub-Graph Macros");

        public final String title;
        ShelfTab(String title) {
            this.title = title;
        }
    }

    public record AssetItem(String id, String label, String category, int color) {}

    private int x;
    private int y;
    private int width;
    private int height;

    private ShelfTab activeTab = ShelfTab.PREFABS;
    private final List<AssetItem> prefabs = new ArrayList<>();
    private final List<AssetItem> palettes = new ArrayList<>();
    private final List<AssetItem> macros = new ArrayList<>();

    private Consumer<AssetItem> onAssetSelectedCallback;

    public StudioAssetShelfWidget() {
        // Registered Prefabs
        prefabs.add(new AssetItem("watchtower_01", "Watchtower", "Prefab", 0xFF33CCFF));
        prefabs.add(new AssetItem("stone_walkway", "Stone Walkway", "Prefab", 0xFFAAAAAA));

        // Registered Palettes
        palettes.add(new AssetItem("alpine_cliff", "Granite & Calcite", "Slope", 0xFFDDDDFF));
        palettes.add(new AssetItem("lush_podzol", "Podzol & Roots", "Surface", 0xFF44AA33));
        palettes.add(new AssetItem("sandstone_strata", "Desert Strata", "Strata", 0xFFE6C619));

        // Reusable Macros
        macros.add(new AssetItem("macro_alpine_cliff", "Alpine Cliff Gate", "Macro", 0xFFA044DD));
        macros.add(new AssetItem("macro_canopy_scatter", "Tree Canopy Density", "Macro", 0xFFE6C619));
        macros.add(new AssetItem("macro_smooth_coast", "Coastline Warp", "Macro", 0xFF33AAEE));
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setOnAssetSelectedCallback(Consumer<AssetItem> cb) {
        this.onAssetSelectedCallback = cb;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xEE14141C);

        // Tab bar at top of shelf
        int tabX = x + 6;
        int tabY = y + 4;
        for (ShelfTab tab : ShelfTab.values()) {
            boolean isSel = (tab == activeTab);
            int tw = font.width(tab.title) + 12;
            int bg = isSel ? 0xFF2A2A3A : 0xFF181820;

            graphics.fill(tabX, tabY, tabX + tw, tabY + 14, bg);
            graphics.text(font, tab.title, tabX + 6, tabY + 3, isSel ? StudioTheme.TEXT_GOLD : StudioTheme.TEXT_GRAY, true);
            tabX += tw + 4;
        }

        // Shelf Items row
        List<AssetItem> activeList = switch (activeTab) {
            case PREFABS -> prefabs;
            case PALETTES -> palettes;
            case MACROS -> macros;
        };

        int itemX = x + 8;
        int itemY = y + 22;
        int cardW = 110;
        int cardH = height - 28;

        for (AssetItem item : activeList) {
            if (itemX + cardW > x + width - 6) break;

            boolean hover = mouseX >= itemX && mouseX <= itemX + cardW && mouseY >= itemY && mouseY <= itemY + cardH;
            int cardBg = hover ? 0xFF282838 : 0xFF1C1C24;

            graphics.fill(itemX, itemY, itemX + cardW, itemY + cardH, cardBg);
            graphics.horizontalLine(itemX, itemX + cardW - 1, itemY, item.color);

            graphics.text(font, item.label, itemX + 6, itemY + 5, StudioTheme.TEXT_WHITE, true);
            graphics.text(font, "[" + item.category + "]", itemX + 6, itemY + 16, StudioTheme.TEXT_DARK_GRAY, true);

            itemX += cardW + 6;
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.x() < x || event.x() > x + width || event.y() < y || event.y() > y + height) {
            return false;
        }

        // Tab click
        int tabX = x + 6;
        int tabY = y + 4;
        for (ShelfTab tab : ShelfTab.values()) {
            int tw = 70;
            if (event.x() >= tabX && event.x() <= tabX + tw && event.y() >= tabY && event.y() <= tabY + 14) {
                activeTab = tab;
                return true;
            }
            tabX += tw + 4;
        }

        // Item click
        List<AssetItem> activeList = switch (activeTab) {
            case PREFABS -> prefabs;
            case PALETTES -> palettes;
            case MACROS -> macros;
        };

        int itemX = x + 8;
        int itemY = y + 22;
        int cardW = 110;
        int cardH = height - 28;

        for (AssetItem item : activeList) {
            if (event.x() >= itemX && event.x() <= itemX + cardW && event.y() >= itemY && event.y() <= itemY + cardH) {
                if (onAssetSelectedCallback != null) {
                    onAssetSelectedCallback.accept(item);
                }
                return true;
            }
            itemX += cardW + 6;
        }

        return false;
    }
}
