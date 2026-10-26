package com.worldgenv2.studio;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Visual design system and styling constants for WorldGen V2 Studio.
 * Enforces an authentic vanilla Minecraft aesthetic styled after Advancements
 * and Settings screens.
 */
public final class StudioTheme {

    // Backgrounds & Framing
    public static final int PANEL_BG            = 0xEE141418; // Translucent dark slate
    public static final int CANVAS_BG           = 0xFF18181C; // Deep background for node canvas
    public static final int GRID_LINE           = 0xFF282830; // Grid line color
    public static final int GRID_LINE_MAJOR     = 0xFF353540; // Major grid line (every 5 cells)
    public static final int PANEL_BORDER_LIGHT  = 0xFF585868; // Highlight border
    public static final int PANEL_BORDER_DARK   = 0xFF1A1A20; // Shadow border
    public static final int PANEL_BORDER_MID    = 0xFF3A3A48; // Neutral frame border
    public static final int HOVER_OVERLAY       = 0x40FFFFFF; // White hover tint
    public static final int SELECTION_OUTLINE   = 0xFFFFAA00; // Gold selection outline

    // Typography
    public static final int TEXT_GOLD           = 0xFFFFAA00; // Header gold
    public static final int TEXT_YELLOW         = 0xFFFFFF55; // Vanilla yellow
    public static final int TEXT_WHITE          = 0xFFFFFFFF; // Primary label
    public static final int TEXT_GRAY           = 0xFFAAAAAA; // Subtitle / secondary
    public static final int TEXT_DARK_GRAY      = 0xFF666677; // Disabled / muted
    public static final int TEXT_CYAN           = 0xFF55FFFF; // Informational / tags

    // Node Categories & Socket Domains
    public static final int COLOR_DENSITY       = 0xFF44CC44; // Green (Volumetric Density)
    public static final int COLOR_CURVE         = 0xFF33AAEE; // Blue (1D Curves)
    public static final int COLOR_VECTOR        = 0xFFFF9922; // Orange (3D Vectors)
    public static final int COLOR_MATERIAL      = 0xFFA044DD; // Purple (Materials & Strata)
    public static final int COLOR_PROP          = 0xFFE6C619; // Yellow (Props & Foliage)
    public static final int COLOR_POSITIONS     = 0xFF19D2B5; // Cyan (Positions & Scanners)
    public static final int COLOR_GRAPH         = 0xFFEE4444; // Red (Topological Graph)

    // Diagnostics & Warnings
    public static final int COLOR_WARNING       = 0xFFFF9900; // Warning amber
    public static final int COLOR_ERROR         = 0xFFFF3333; // Error crimson
    public static final int COLOR_SUCCESS       = 0xFF44DD44; // Success green

    private StudioTheme() {}

    /**
     * Draws an authentic beveled Minecraft stone panel with 3D bevel edges.
     */
    public static void drawBeveledPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor) {
        // Base fill
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Top and Left light highlight
        graphics.horizontalLine(x, x + width - 1, y, PANEL_BORDER_LIGHT);
        graphics.verticalLine(x, y, y + height - 1, PANEL_BORDER_LIGHT);

        // Bottom and Right dark shadow
        graphics.horizontalLine(x, x + width - 1, y + height - 1, PANEL_BORDER_DARK);
        graphics.verticalLine(x + width - 1, y, y + height - 1, PANEL_BORDER_DARK);
    }

    /**
     * Draws an inset beveled well (e.g. For text inputs, canvas viewports, and slots).
     */
    public static void drawInsetWell(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int bgColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Top and Left dark shadow
        graphics.horizontalLine(x, x + width - 1, y, PANEL_BORDER_DARK);
        graphics.verticalLine(x, y, y + height - 1, PANEL_BORDER_DARK);

        // Bottom and Right light edge
        graphics.horizontalLine(x, x + width - 1, y + height - 1, PANEL_BORDER_LIGHT);
        graphics.verticalLine(x + width - 1, y, y + height - 1, PANEL_BORDER_LIGHT);
    }

    /**
     * Draws a clean vanilla button frame.
     */
    public static void drawButton(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int height, boolean hovered, boolean active) {
        int bg = active ? (hovered ? 0xFF3C3C4C : 0xFF2A2A36) : 0xFF1E1E24;
        drawBeveledPanel(graphics, x, y, width, height, bg);

        int textColor = active ? (hovered ? TEXT_YELLOW : TEXT_WHITE) : TEXT_DARK_GRAY;
        int textWidth = font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, text, textX, textY, textColor, true);
    }
}
