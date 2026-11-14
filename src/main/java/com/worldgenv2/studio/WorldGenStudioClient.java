package com.worldgenv2.studio;

import com.mojang.blaze3d.platform.InputConstants;
import com.worldgenv2.studio.gui.WorldGenStudioScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side lifecycle and event controller for WorldGen V2 Studio.
 * Registers the Studio key mapping (Default: 'G'), the 3D Dev Chunk Highlight toggle ('H'),
 * 3D in-world chunk gizmo renderer, client commands (/studio), and HUD status overlays.
 */
public final class WorldGenStudioClient {

    private static KeyMapping studioKey;
    private static KeyMapping toggleHighlightKey;

    public static boolean isStudioKey(net.minecraft.client.input.KeyEvent event) {
        return studioKey != null && studioKey.matches(event);
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(WorldGenStudioClient::onRegisterKeyMappings);
        modEventBus.addListener(WorldGenStudioClient::onRegisterDebugRenderers);
        NeoForge.EVENT_BUS.addListener(WorldGenStudioClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(WorldGenStudioClient::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(WorldGenStudioClient::onScreenInit);

        registerPresetEditor();
        com.worldgenv2.studio.dev.DevBridge.startIfEnabled();
    }

    public static void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen pauseScreen) {
            int btnW = 84;
            int btnH = 20;
            int btnX = pauseScreen.width - btnW - 8;
            int btnY = 8;
            event.addListener(net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.literal("⚙ Studio"),
                    btn -> Minecraft.getInstance().setScreen(WorldGenStudioScreen.getInstance())
            ).bounds(btnX, btnY, btnW, btnH).build());
        }
    }

    private static void registerPresetEditor() {
        try {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> presetKey =
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.WORLD_PRESET,
                    net.minecraft.resources.Identifier.parse("worldgenv2:volumetric")
                );
            net.minecraft.client.gui.screens.worldselection.PresetEditor.EDITORS.put(
                java.util.Optional.of(presetKey),
                (createWorldScreen, creationContext) -> new com.worldgenv2.minecraft.gui.WorldGenCustomizeScreen(createWorldScreen)
            );
        } catch (Throwable t) {
            // Silently fall back if PresetEditor is unavailable
        }
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        studioKey = new KeyMapping(
                "key.worldgenv2.open_studio",
                InputConstants.KEY_G,
                KeyMapping.Category.MISC
        );
        toggleHighlightKey = new KeyMapping(
                "key.worldgenv2.toggle_chunk_highlight",
                InputConstants.KEY_H,
                KeyMapping.Category.MISC
        );
        event.register(studioKey);
        event.register(toggleHighlightKey);
    }

    public static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new StudioChunkHighlightRenderer());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos bpos = mc.player.blockPosition();
            StudioState.get().updatePlayerChunk(new ChunkPos(bpos.getX() >> 4, bpos.getZ() >> 4));
        } else {
            // Player exited world/level -> reset studio session so next world begins cleanly at spawn
            WorldGenStudioScreen.resetSession();
            StudioState.get().resetForNewWorld();
        }

        if (studioKey != null) {
            while (studioKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(WorldGenStudioScreen.getInstance());
                } else if (mc.screen instanceof WorldGenStudioScreen) {
                    mc.setScreen(null);
                }
            }
        }

        if (toggleHighlightKey != null) {
            while (toggleHighlightKey.consumeClick()) {
                if (mc.screen == null) {
                    StudioState.get().toggleChunkHighlight();
                }
            }
        }
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("studio")
                        .executes(ctx -> {
                            Minecraft.getInstance().schedule(() -> {
                                Minecraft.getInstance().setScreen(WorldGenStudioScreen.getInstance());
                            });
                            return 1;
                        })
                        .then(Commands.literal("highlight")
                                .executes(ctx -> {
                                    StudioState.get().toggleChunkHighlight();
                                    return 1;
                                }))
                        .then(Commands.literal("setup")
                                .executes(ctx -> {
                                    Minecraft.getInstance().schedule(() -> {
                                        Minecraft.getInstance().setScreen(new com.worldgenv2.minecraft.gui.WorldGenCustomizeScreen(null));
                                    });
                                    return 1;
                                }))
        );
    }
}
