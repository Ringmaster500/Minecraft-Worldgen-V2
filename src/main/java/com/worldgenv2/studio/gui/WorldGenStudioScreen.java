package com.worldgenv2.studio.gui;

import com.worldgenv2.studio.WorldGenStudioClient;
import com.worldgenv2.studio.shell.Shell;
import com.worldgenv2.studio.shell.StudioApp;
import com.worldgenv2.studio.ui.Gfx;
import com.worldgenv2.studio.ui.Ui;
import com.worldgenv2.studio.ui.UiRoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Minecraft Screen that hosts the Studio widget tree. It only does three jobs:
 * render the {@link Shell} on the Studio's own pixel grid, translate input into that grid, and
 * route global shortcuts. All state lives in {@link StudioApp}; all layout lives in {@link Shell}.
 * <p>
 * The Studio ignores Minecraft's GUI scale: it picks its own (default 2 device pixels per virtual pixel)
 * so the canvas is the same useful size whether the game's GUI scale is 2, 3 or 4.
 */
public class WorldGenStudioScreen extends Screen {

    /** Kept for existing callers (keybind, /studio, pause button). Each call yields a fresh screen over shared state. */
    public static WorldGenStudioScreen getInstance() {
        return new WorldGenStudioScreen();
    }

    /** Studio state is project-scoped, not world-scoped, so nothing needs resetting between worlds. */
    public static void resetSession() {}

    private final StudioApp app = StudioApp.get();
    private final UiRoot ui = new UiRoot();
    private final Shell shell;
    private double toVirtual = 1;

    public WorldGenStudioScreen() {
        super(Component.literal("WorldGen Studio"));
        shell = new Shell(app, this::onClose);
        ui.content.add(shell);
    }

    @Override
    protected void init() {
        super.init();
        refreshScale();
    }

    private void refreshScale() {
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        toVirtual = guiScale / (double) app.uiScale;
        int vw = (int) Math.ceil(width * toVirtual), vh = (int) Math.ceil(height * toVirtual);
        ui.setSize(vw, vh);
        shell.setBounds(0, 0, vw, vh);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        refreshScale();
        graphics.fill(0, 0, width, height, Ui.BG_APP);
        float f = (float) (1.0 / toVirtual);
        graphics.pose().pushMatrix();
        graphics.pose().scale(f, f);
        ui.render(new Gfx(graphics, font), (int) (mouseX * toVirtual), (int) (mouseY * toVirtual));
        graphics.pose().popMatrix();
    }

    private double vx(double x) { return x * toVirtual; }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        return ui.mouseClicked(vx(e.x()), vx(e.y()), e.button(), dbl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return ui.mouseReleased(vx(e.x()), vx(e.y()), e.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return ui.mouseDragged(vx(e.x()), vx(e.y()), e.button(), dx * toVirtual, dy * toVirtual);
    }

    @Override
    public void mouseMoved(double x, double y) {
        ui.mouseMoved(vx(x), vx(y));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        return ui.mouseScrolled(vx(x), vx(y), sx, sy);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return ui.charTyped(e.codepoint());
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key(), mods = e.modifiers();
        if (ui.keyPressed(key, mods)) return true;

        boolean ctrl = (mods & Ui.MOD_CTRL) != 0, shift = (mods & Ui.MOD_SHIFT) != 0;
        if (ctrl) {
            switch (key) {
                case 83 -> { app.save(); return true; }                              // S
                case 90 -> { if (shift) app.redo(); else app.undo(); return true; }  // Z
                case 89 -> { app.redo(); return true; }                              // Y
                case 87 -> { if (app.activeDoc != null) app.closeDoc(app.activeDoc); return true; } // W
                case 74 -> { app.problemsOpen = !app.problemsOpen; app.uiRevision++; return true; } // J
                case 61, 334 -> { app.uiScale = Math.min(4, app.uiScale + 1); return true; }        // + : bigger UI
                case 45, 333 -> { app.uiScale = Math.max(1, app.uiScale - 1); return true; }        // - : smaller UI
                default -> {}
            }
        }
        if (key == Ui.KEY_ESC || (!ui.textInputActive() && WorldGenStudioClient.isStudioKey(e))) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
