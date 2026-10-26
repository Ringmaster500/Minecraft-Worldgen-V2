package com.worldgenv2.minecraft.gui;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Clean, spacious Worldgen V2 World Setup & Seed Preview Screen.
 * Opened when clicking [ Customize ] on Worldgen V2 in Minecraft's Create World screen.
 * Displays a clean centered terrain radar with seed and scaling controls.
 */
public class WorldGenCustomizeScreen extends Screen {

    private final Screen parentScreen;
    private final LiveSeedRadarWidget radarWidget;

    private long currentSeed = 133742L;
    private float biomeScale = 1.0f;

    public WorldGenCustomizeScreen(Screen parentScreen) {
        super(Component.literal("Worldgen V2: World Options"));
        this.parentScreen = parentScreen;
        this.radarWidget = new LiveSeedRadarWidget();
        this.currentSeed = new Random().nextLong() & 0x7FFFFFFFFFFFL;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;

        // Clean centered preview radar
        int previewW = Math.min(320, width - 40);
        int previewH = Math.min(150, height - 110);
        int previewX = centerX - previewW / 2;
        int previewY = 32;

        this.radarWidget.setBounds(previewX, previewY, previewW, previewH);
        this.radarWidget.updateSeed(currentSeed, biomeScale);

        // Control row directly beneath the preview
        int controlY = previewY + previewH + 8;

        this.addRenderableWidget(Button.builder(Component.literal("🎲 Randomize Seed"), btn -> {
            this.currentSeed = new Random().nextLong() & 0x7FFFFFFFFFFFL;
            this.radarWidget.updateSeed(currentSeed, biomeScale);
        }).bounds(centerX - 105, controlY, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Scale: " + String.format("%.1fx", biomeScale)), btn -> {
            // Cycle scale: 0.5x -> 1.0x -> 1.5x -> 2.0x -> 0.5x
            if (biomeScale >= 2.0f) biomeScale = 0.5f;
            else biomeScale += 0.5f;
            btn.setMessage(Component.literal("Scale: " + String.format("%.1fx", biomeScale)));
            this.radarWidget.updateSeed(currentSeed, biomeScale);
        }).bounds(centerX + 5, controlY, 100, 20).build());

        // Footer Done and Cancel buttons safely at bottom
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            Minecraft.getInstance().setScreen(parentScreen);
        }).bounds(centerX - 105, height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            Minecraft.getInstance().setScreen(parentScreen);
        }).bounds(centerX + 5, height - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractMenuBackground(graphics);

        // Header Title (Clean & Centered)
        graphics.centeredText(font, "Worldgen V2 Configuration", width / 2, 12, StudioTheme.TEXT_GOLD);

        // Render Centered Live Seed Radar Widget
        this.radarWidget.render(graphics, font, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }
}
