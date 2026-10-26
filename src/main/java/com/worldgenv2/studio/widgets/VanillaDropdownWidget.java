package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Authentic vanilla-styled dropdown widget for Minecraft WorldGen Studio.
 * Replaces modern flat dropdowns with beveled stone framing, vanilla typography,
 * optional block/item icons, smooth scrollbars, and chevron indicator.
 */
public class VanillaDropdownWidget<T> {

    public record DropdownOption<T>(String id, Component label, ItemStack icon, T value) {}

    private int x;
    private int y;
    private int width;
    private int height;

    private final List<DropdownOption<T>> options = new ArrayList<>();
    private int selectedIndex = 0;
    private boolean expanded = false;
    private int scrollOffset = 0;
    private final int maxVisibleRows = 8;
    private final int rowHeight = 18;

    private Consumer<DropdownOption<T>> onSelectListener;

    public VanillaDropdownWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void addOption(String id, Component label, ItemStack icon, T value) {
        options.add(new DropdownOption<>(id, label, icon, value));
    }

    public void clearOptions() {
        options.clear();
        selectedIndex = 0;
        scrollOffset = 0;
    }

    public void setOnSelect(Consumer<DropdownOption<T>> listener) {
        this.onSelectListener = listener;
    }

    public DropdownOption<T> getSelectedOption() {
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            return options.get(selectedIndex);
        }
        return null;
    }

    public void setSelectedById(String id) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(id)) {
                this.selectedIndex = i;
                return;
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int bg = hovered ? 0xFF2D2D3A : 0xFF22222C;

        // Draw collapsed button box
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, bg);

        DropdownOption<T> selected = getSelectedOption();
        int contentX = x + 3;
        int textY = y + (height - 8) / 2;

        if (selected != null) {
            // Render block/item icon if present and box is wide enough
            if (selected.icon() != null && !selected.icon().isEmpty() && width >= 72) {
                graphics.fakeItem(selected.icon(), contentX, y + (height - 16) / 2);
                contentX += 17;
            }

            // Render label (truncate if too long)
            int maxTextWidth = Math.max(12, width - (contentX - x) - 12);
            String textStr = font.plainSubstrByWidth(selected.label().getString(), maxTextWidth);
            graphics.text(font, textStr, contentX, textY, StudioTheme.TEXT_WHITE, true);
        } else {
            graphics.text(font, "Select...", contentX, textY, StudioTheme.TEXT_GRAY, true);
        }

        // Draw chevron indicator
        String chevron = expanded ? "▲" : "▼";
        int chevronColor = hovered ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY;
        graphics.text(font, chevron, x + width - 9, textY, chevronColor, true);
    }

    /**
     * Renders the expanded dropdown popout overlay.
     * Should be called AFTER main screen widgets to ensure popout appears on top.
     */
    public void renderOverlay(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (!expanded || options.isEmpty()) {
            return;
        }

        int popW = Math.max(width, 130);
        int visibleCount = Math.min(options.size(), maxVisibleRows);
        int popoutHeight = visibleCount * rowHeight + 4;
        int popoutY = y + height + 1;

        // Popout frame
        StudioTheme.drawBeveledPanel(graphics, x, popoutY, popW, popoutHeight, 0xF5181822);

        int maxScroll = Math.max(0, options.size() - maxVisibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        for (int i = 0; i < visibleCount; i++) {
            int optionIndex = i + scrollOffset;
            if (optionIndex >= options.size()) break;

            DropdownOption<T> opt = options.get(optionIndex);
            int rowY = popoutY + 2 + (i * rowHeight);
            boolean rowHovered = mouseX >= x + 2 && mouseX < x + popW - 2 && mouseY >= rowY && mouseY < rowY + rowHeight;

            if (rowHovered) {
                graphics.fill(x + 2, rowY, x + popW - 2, rowY + rowHeight, 0x50FFFFFF);
            } else if (optionIndex == selectedIndex) {
                graphics.fill(x + 2, rowY, x + popW - 2, rowY + rowHeight, 0x30FFAA00);
            }

            int itemX = x + 4;
            if (opt.icon() != null && !opt.icon().isEmpty()) {
                graphics.fakeItem(opt.icon(), itemX, rowY + 1);
                itemX += 18;
            }

            int textColor = (optionIndex == selectedIndex) ? StudioTheme.TEXT_GOLD : (rowHovered ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE);
            int maxOptW = Math.max(20, popW - (itemX - x) - 10);
            String labelStr = font.plainSubstrByWidth(opt.label().getString(), maxOptW);
            graphics.text(font, labelStr, itemX, rowY + (rowHeight - 8) / 2, textColor, true);
        }

        // Scrollbar if necessary
        if (options.size() > maxVisibleRows) {
            int scrollbarX = x + popW - 6;
            int scrollbarHeight = popoutHeight - 4;
            graphics.fill(scrollbarX, popoutY + 2, scrollbarX + 4, popoutY + 2 + scrollbarHeight, 0xFF101014);

            float scrollFrac = (float) scrollOffset / (float) maxScroll;
            int thumbHeight = Math.max(8, scrollbarHeight * maxVisibleRows / options.size());
            int thumbY = popoutY + 2 + (int) ((scrollbarHeight - thumbHeight) * scrollFrac);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, StudioTheme.PANEL_BORDER_LIGHT);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Click on the collapsed box
        if (mx >= x && mx < x + width && my >= y && my < y + height) {
            this.expanded = !this.expanded;
            return true;
        }

        // Click inside the expanded popout
        if (expanded) {
            int popW = Math.max(width, 130);
            int visibleCount = Math.min(options.size(), maxVisibleRows);
            int popoutHeight = visibleCount * rowHeight + 4;
            int popoutY = y + height + 1;

            if (mx >= x && mx < x + popW && my >= popoutY && my < popoutY + popoutHeight) {
                int clickedRow = (int) ((my - popoutY - 2) / rowHeight);
                int targetIndex = clickedRow + scrollOffset;
                if (targetIndex >= 0 && targetIndex < options.size()) {
                    this.selectedIndex = targetIndex;
                    this.expanded = false;
                    if (onSelectListener != null) {
                        onSelectListener.accept(options.get(targetIndex));
                    }
                    return true;
                }
            } else {
                // Click outside closes popout
                this.expanded = false;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (expanded) {
            int popW = Math.max(width, 130);
            int visibleCount = Math.min(options.size(), maxVisibleRows);
            int popoutHeight = visibleCount * rowHeight + 4;
            int popoutY = y + height + 1;

            if (mouseX >= x && mouseX < x + popW && mouseY >= popoutY && mouseY < popoutY + popoutHeight) {
                int maxScroll = Math.max(0, options.size() - maxVisibleRows);
                scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
                return true;
            }
        }
        return false;
    }
}
