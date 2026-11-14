package com.worldgenv2.studio.ui;

/** Studio design tokens: one place for every colour and metric. */
public final class Ui {
    private Ui() {}

    // Surfaces (darkest -> lightest)
    public static final int BG_APP      = 0xFF0B0C10;
    public static final int BG_PANEL    = 0xFF111319;
    public static final int BG_RAISED   = 0xFF171A22;
    public static final int BG_INPUT    = 0xFF0D0F14;
    public static final int BG_HOVER    = 0xFF1D212C;
    public static final int BG_ACTIVE   = 0xFF242A3A;
    public static final int LINE        = 0xFF1F2330;
    public static final int LINE_STRONG = 0xFF2E3447;

    // Text
    public static final int TEXT        = 0xFFE4E7ED;
    public static final int TEXT_DIM    = 0xFF9097A8;
    public static final int TEXT_FAINT  = 0xFF5C6275;

    // Accents / semantic
    public static final int ACCENT      = 0xFFFFAA00;
    public static final int ACCENT_DIM  = 0xFF8A5C00;
    public static final int BLUE        = 0xFF4FA3E3;
    public static final int GREEN       = 0xFF5FBF6A;
    public static final int RED         = 0xFFE5534B;
    public static final int YELLOW      = 0xFFE3B341;
    public static final int PURPLE      = 0xFFB37BE8;
    public static final int CYAN        = 0xFF3FC5FF;

    // Metrics (virtual px)
    public static final int ROW_H       = 16;
    public static final int FIELD_H     = 16;
    public static final int PAD         = 6;
    public static final int GAP         = 4;
    public static final int TOPBAR_H    = 26;
    public static final int STATUS_H    = 14;
    public static final int TAB_H       = 20;

    // GLFW key codes used by widgets
    public static final int KEY_ESC = 256, KEY_ENTER = 257, KEY_TAB = 258, KEY_BACKSPACE = 259,
            KEY_INSERT = 260, KEY_DELETE = 261, KEY_RIGHT = 262, KEY_LEFT = 263, KEY_DOWN = 264,
            KEY_UP = 265, KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267, KEY_HOME = 268, KEY_END = 269,
            KEY_KP_ENTER = 335;
    public static final int MOD_SHIFT = 1, MOD_CTRL = 2, MOD_ALT = 4;
}
