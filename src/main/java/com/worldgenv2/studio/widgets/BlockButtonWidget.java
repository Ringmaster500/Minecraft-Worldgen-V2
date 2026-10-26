package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/**
 * Dedicated UI pill button displaying a selected block's live 3D item model,
 * localized name, and registry ID. Clicking it opens the {@link VisualBlockPickerModal}.
 */
public class BlockButtonWidget {

    private int x;
    private int y;
    private int width;
    private int height;

    private Identifier currentBlockId;
    private ItemStack cachedStack = ItemStack.EMPTY;
    private String cachedName = "";

    private final Consumer<BlockButtonWidget> onClickAction;

    public BlockButtonWidget(int x, int y, int width, int height, Identifier initialBlockId, Consumer<BlockButtonWidget> onClick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onClickAction = onClick;
        setBlock(initialBlockId);
    }

    public Identifier getBlockId() {
        return currentBlockId;
    }

    public void setBlock(Identifier id) {
        this.currentBlockId = id;
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block != null && block != Blocks.AIR) {
            this.cachedStack = block.asItem().getDefaultInstance();
            this.cachedName = cachedStack.getHoverName().getString();
        } else {
            this.cachedStack = ItemStack.EMPTY;
            this.cachedName = id != null ? id.toString() : "None";
        }
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int bg = hovered ? 0xFF2E2E3C : 0xFF22222E;

        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, bg);

        int contentX = x + 4;
        int textY = y + (height - 8) / 2;

        // Render live 3D item icon
        if (!cachedStack.isEmpty()) {
            graphics.fakeItem(cachedStack, contentX, y + (height - 16) / 2);
            contentX += 18;
        }

        // Render block name
        int maxTextW = width - (contentX - x) - 14;
        String nameStr = cachedName;
        if (font.width(nameStr) > maxTextW) {
            while (nameStr.length() > 3 && font.width(nameStr + "...") > maxTextW) {
                nameStr = nameStr.substring(0, nameStr.length() - 1);
            }
            nameStr += "...";
        }

        graphics.text(font, nameStr, contentX, textY, hovered ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE, true);

        // Chevron icon
        graphics.text(font, "▼", x + width - 11, textY, hovered ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY, true);

        if (hovered && currentBlockId != null) {
            graphics.setTooltipForNextFrame(Component.literal("§e" + currentBlockId.toString()), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= x && event.x() < x + width && event.y() >= y && event.y() < y + height) {
            if (onClickAction != null) {
                onClickAction.accept(this);
            }
            return true;
        }
        return false;
    }
}
