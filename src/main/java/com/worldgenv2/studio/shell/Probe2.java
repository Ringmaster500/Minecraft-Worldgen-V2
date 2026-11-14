package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.ui.Gfx;
import net.minecraft.client.Minecraft;

/** Font-only Gfx for measuring text during layout (no drawing). */
final class Probe2 {
    private static Gfx cached;

    private Probe2() {}

    static Gfx g() {
        if (cached == null) cached = new Gfx(null, Minecraft.getInstance().font);
        return cached;
    }
}
