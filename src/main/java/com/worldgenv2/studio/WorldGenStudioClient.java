package com.worldgenv2.studio;

import com.mojang.blaze3d.platform.InputConstants;
import com.worldgenv2.studio.gui.WorldGenStudioScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side lifecycle for the Studio: the open-Studio key (default G), the /studio command, the Studio button on the pause
 * screen, and the Customize screen on Create World for the Worldgen V2 world types.
 */
public final class WorldGenStudioClient {

    private static KeyMapping studioKey;

    public static boolean isStudioKey(net.minecraft.client.input.KeyEvent event) {
        return studioKey != null && studioKey.matches(event);
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(WorldGenStudioClient::onRegisterKeyMappings);
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
            for (String id : new String[]{"worldgenv2:volumetric", "worldgenv2:project"}) {
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> presetKey =
                    net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.WORLD_PRESET,
                        net.minecraft.resources.Identifier.parse(id)
                    );
                net.minecraft.client.gui.screens.worldselection.PresetEditor.EDITORS.put(
                    java.util.Optional.of(presetKey),
                    (createWorldScreen, creationContext) -> new com.worldgenv2.minecraft.gui.WorldGenCustomizeScreen(createWorldScreen)
                );
            }
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
        event.register(studioKey);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (studioKey != null) {
            while (studioKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(WorldGenStudioScreen.getInstance());
                } else if (mc.screen instanceof WorldGenStudioScreen) {
                    mc.setScreen(null);
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
