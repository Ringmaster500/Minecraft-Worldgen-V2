package com.worldgenv2.studio.ui;

import net.minecraft.client.Minecraft;

/**
 * Provides a {@link Gfx} for text measurement outside of a render pass (e.g. mapping a click to a caret
 * position). Measurement only needs the Font, so graphics is left null-safe.
 */
final class Probe {
    private static Gfx cached;

    private Probe() {}

    static Gfx gfx() {
        if (cached == null) cached = new Gfx(null, Minecraft.getInstance().font);
        return cached;
    }
}
