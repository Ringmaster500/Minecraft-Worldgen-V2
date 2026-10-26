package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.function.Consumer;

/**
 * Creative Inventory / JEI styled visual block browser modal.
 * Displays actual in-game 3D/2D block item icons, supporting modded blocks and fluids from all installed mods.
 * Allows instant search by display name, mod namespace (@modid), and tag (#tag).
 */
public class VisualBlockPickerModal {

    public record BlockEntry(Identifier id, Block block, ItemStack itemStack, String displayName, String modId, boolean isFluid) {}

    private static final List<BlockEntry> ALL_BLOCKS = new ArrayList<>();
    private static boolean initialized = false;

    private boolean visible = false;
    private int modalX;
    private int modalY;
    private int modalWidth = 280;
    private int modalHeight = 220;

    private String searchFilter = "";
    private final List<BlockEntry> filteredBlocks = new ArrayList<>();
    private int scrollRow = 0;
    private final int columns = 9;
    private final int visibleRows = 6;
    private final int slotSize = 20;

    private Consumer<Identifier> onSelectCallback;

    public VisualBlockPickerModal() {
        ensureCatalogInitialized();
        updateFilter();
    }

    private static synchronized void ensureCatalogInitialized() {
        if (initialized) return;
        try {
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null || block == Blocks.AIR) continue;

                boolean isFluid = (block instanceof net.minecraft.world.level.block.LiquidBlock)
                        || (block == Blocks.WATER)
                        || (block == Blocks.LAVA)
                        || BuiltInRegistries.FLUID.containsKey(id);

                ItemStack stack = block.asItem().getDefaultInstance();
                if (stack.isEmpty()) {
                    if (block == Blocks.WATER || "water".equals(id.getPath())) {
                        stack = new ItemStack(Items.WATER_BUCKET);
                    } else if (block == Blocks.LAVA || "lava".equals(id.getPath())) {
                        stack = new ItemStack(Items.LAVA_BUCKET);
                    } else if (BuiltInRegistries.FLUID.containsKey(id)) {
                        var fluid = BuiltInRegistries.FLUID.getValue(id);
                        var bucket = fluid.getBucket();
                        if (bucket != null && bucket != Items.AIR) {
                            stack = new ItemStack(bucket);
                        }
                    }
                }
                if (stack.isEmpty()) continue; // Skip technical blocks without inventory items

                String name = (isFluid && (block == Blocks.WATER || block == Blocks.LAVA))
                        ? (block == Blocks.WATER ? "Water (Fluid)" : "Lava (Fluid)")
                        : stack.getHoverName().getString();
                String mod = id.getNamespace();
                ALL_BLOCKS.add(new BlockEntry(id, block, stack, name, mod, isFluid));
            }

            // Also ensure registered fluids (e.g. modded fluids) are available
            for (Identifier fid : BuiltInRegistries.FLUID.keySet()) {
                var fluid = BuiltInRegistries.FLUID.getValue(fid);
                if (fluid == null || fluid.defaultFluidState().isEmpty()) continue;
                boolean alreadyAdded = false;
                for (BlockEntry be : ALL_BLOCKS) {
                    if (be.id().equals(fid)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    var bucket = fluid.getBucket();
                    ItemStack fStack = (bucket != null && bucket != Items.AIR)
                            ? new ItemStack(bucket)
                            : new ItemStack(Items.WATER_BUCKET);
                    Block legacyBlock = fluid.defaultFluidState().createLegacyBlock().getBlock();
                    String name = fStack.getHoverName().getString() + " (Fluid)";
                    ALL_BLOCKS.add(new BlockEntry(fid, legacyBlock, fStack, name, fid.getNamespace(), true));
                }
            }

            ALL_BLOCKS.sort(Comparator.comparing(b -> b.id().toString()));
            initialized = true;
        } catch (Exception e) {
            // Fallback
        }
    }

    public void open(Consumer<Identifier> onSelect) {
        this.onSelectCallback = onSelect;
        this.visible = true;
        this.searchFilter = "";
        this.scrollRow = 0;
        updateFilter();
    }

    public void close() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void updateFilter() {
        filteredBlocks.clear();
        String query = searchFilter.trim().toLowerCase(Locale.ROOT);

        for (BlockEntry entry : ALL_BLOCKS) {
            if (query.isEmpty()) {
                filteredBlocks.add(entry);
                continue;
            }

            if (query.startsWith("@")) {
                // Mod namespace search
                String modQuery = query.substring(1);
                if (entry.modId().toLowerCase(Locale.ROOT).contains(modQuery)) {
                    filteredBlocks.add(entry);
                }
            } else if (query.startsWith("#")) {
                // Tag search
                String tagQuery = query.substring(1);
                if (entry.id().getPath().toLowerCase(Locale.ROOT).contains(tagQuery)) {
                    filteredBlocks.add(entry);
                }
            } else {
                // Display name, ID, or fluid/liquid keyword search
                boolean isFluidSearch = entry.isFluid() && (
                    "fluid".contains(query) || query.contains("fluid") ||
                    "liquid".contains(query) || query.contains("liquid")
                );
                if (entry.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || entry.id().toString().toLowerCase(Locale.ROOT).contains(query)
                        || isFluidSearch) {
                    filteredBlocks.add(entry);
                }
            }
        }
        this.scrollRow = 0;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        this.modalX = (screenWidth - modalWidth) / 2;
        this.modalY = (screenHeight - modalHeight) / 2;

        // Dim background backdrop
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Modal frame
        StudioTheme.drawBeveledPanel(graphics, modalX, modalY, modalWidth, modalHeight, 0xF5181820);

        // Header Title
        graphics.text(font, "Block Palette Browser", modalX + 10, modalY + 8, StudioTheme.TEXT_GOLD, true);
        String countStr = filteredBlocks.size() + " blocks";
        graphics.text(font, countStr, modalX + modalWidth - font.width(countStr) - 10, modalY + 8, StudioTheme.TEXT_GRAY, true);

        // Search Input Box
        int searchY = modalY + 22;
        StudioTheme.drawInsetWell(graphics, modalX + 10, searchY, modalWidth - 20, 16, 0xFF101014);
        String searchDisplay = searchFilter.isEmpty() ? "§7Search name, @mod, or #tag..." : searchFilter + "_";
        graphics.text(font, searchDisplay, modalX + 14, searchY + 4, StudioTheme.TEXT_WHITE, false);

        // Grid Well
        int gridX = modalX + 10;
        int gridY = searchY + 22;
        int gridW = columns * slotSize + 4;
        int gridH = visibleRows * slotSize + 4;
        StudioTheme.drawInsetWell(graphics, gridX, gridY, gridW, gridH, 0xFF121216);

        int maxScrollRows = Math.max(0, (int) Math.ceil((double) filteredBlocks.size() / columns) - visibleRows);
        scrollRow = Math.max(0, Math.min(maxScrollRows, scrollRow));

        BlockEntry hoveredEntry = null;
        int hoveredX = 0, hoveredY = 0;

        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < columns; c++) {
                int index = (r + scrollRow) * columns + c;
                if (index >= filteredBlocks.size()) break;

                BlockEntry entry = filteredBlocks.get(index);
                int slotX = gridX + 2 + (c * slotSize);
                int slotY = gridY + 2 + (r * slotSize);

                boolean slotHovered = mouseX >= slotX && mouseX < slotX + slotSize && mouseY >= slotY && mouseY < slotY + slotSize;

                if (slotHovered) {
                    graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x50FFAA00);
                    hoveredEntry = entry;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }

                // Render live 3D/2D block item model
                graphics.fakeItem(entry.itemStack(), slotX + 2, slotY + 2);
            }
        }

        // Scrollbar
        int scrollbarX = gridX + gridW + 4;
        int scrollbarW = 6;
        graphics.fill(scrollbarX, gridY, scrollbarX + scrollbarW, gridY + gridH, 0xFF101014);
        if (maxScrollRows > 0) {
            float frac = (float) scrollRow / (float) maxScrollRows;
            int thumbH = Math.max(12, gridH * visibleRows / ((filteredBlocks.size() / columns) + 1));
            int thumbY = gridY + (int) ((gridH - thumbH) * frac);
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, StudioTheme.PANEL_BORDER_LIGHT);
        }

        // Close / Cancel Button at bottom
        int btnW = 80;
        int btnH = 18;
        int btnX = modalX + (modalWidth - btnW) / 2;
        int btnY = modalY + modalHeight - 26;
        boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        StudioTheme.drawButton(graphics, font, Component.literal("Cancel"), btnX, btnY, btnW, btnH, btnHovered, true);

        // Tooltip for hovered block
        if (hoveredEntry != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredEntry.displayName()));
            tooltip.add(Component.literal("§7ID: §e" + hoveredEntry.id().toString()));
            tooltip.add(Component.literal("§8Mod: §6" + hoveredEntry.modId()));
            graphics.setTooltipForNextFrame(tooltip.getFirst(), hoveredX, hoveredY);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible) return false;

        double mx = event.x();
        double my = event.y();

        int gridX = modalX + 10;
        int searchY = modalY + 22;
        int gridY = searchY + 22;

        // Check block slot click
        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < columns; c++) {
                int index = (r + scrollRow) * columns + c;
                if (index >= filteredBlocks.size()) break;

                int slotX = gridX + 2 + (c * slotSize);
                int slotY = gridY + 2 + (r * slotSize);

                if (mx >= slotX && mx < slotX + slotSize && my >= slotY && my < slotY + slotSize) {
                    BlockEntry chosen = filteredBlocks.get(index);
                    this.visible = false;
                    if (onSelectCallback != null) {
                        onSelectCallback.accept(chosen.id());
                    }
                    return true;
                }
            }
        }

        // Check Cancel button
        int btnW = 80;
        int btnH = 18;
        int btnX = modalX + (modalWidth - btnW) / 2;
        int btnY = modalY + modalHeight - 26;
        if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
            this.visible = false;
            return true;
        }

        // Clicking outside closes the modal
        if (mx < modalX || mx >= modalX + modalWidth || my < modalY || my >= modalY + modalHeight) {
            this.visible = false;
            return true;
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;

        int maxScrollRows = Math.max(0, (int) Math.ceil((double) filteredBlocks.size() / columns) - visibleRows);
        if (maxScrollRows > 0) {
            this.scrollRow = (int) Math.max(0, Math.min(maxScrollRows, scrollRow - scrollY));
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!visible) return false;

        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            this.visible = false;
            return true;
        }
        if (event.key() == 259) { // GLFW_KEY_BACKSPACE
            if (!searchFilter.isEmpty()) {
                searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
                updateFilter();
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!visible) return false;

        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) {
            searchFilter += c;
            updateFilter();
            return true;
        }
        return false;
    }
}
