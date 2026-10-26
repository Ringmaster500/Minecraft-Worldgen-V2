package com.worldgenv2.minecraft;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.slf4j.Logger;

/**
 * World height and vertical scale configuration supporting standard presets
 * and arbitrary user-defined manual numbers.
 */
public final class WorldHeightConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Preset {
        STANDARD("standard", -64, 384, 63, "Standard Minecraft scale (384 blocks, Y = -64 to 320)"),
        HYTALE_512("hytale_512", 0, 512, 128, "Hytale Orbis canonical scale (512 blocks, Y = 0 to 512)"),
        HYTALE_EXTENDED("hytale_extended", -64, 576, 128, "Hytale Extended scale (576 blocks, Y = -64 to 512)"),
        EPIC_1024("epic_1024", -64, 1024, 128, "Epic Mega scale (1024 blocks, Y = -64 to 960)"),
        CUSTOM("custom", -64, 384, 63, "Manual user-defined vertical range");

        private final String id;
        private final int defaultMinY;
        private final int defaultGenDepth;
        private final int defaultSeaLevel;
        private final String description;

        Preset(String id, int defaultMinY, int defaultGenDepth, int defaultSeaLevel, String description) {
            this.id = id;
            this.defaultMinY = defaultMinY;
            this.defaultGenDepth = defaultGenDepth;
            this.defaultSeaLevel = defaultSeaLevel;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public int getDefaultMinY() {
            return defaultMinY;
        }

        public int getDefaultGenDepth() {
            return defaultGenDepth;
        }

        public int getDefaultSeaLevel() {
            return defaultSeaLevel;
        }

        public String getDescription() {
            return description;
        }

        public static Preset fromId(String id) {
            for (Preset preset : values()) {
                if (preset.id.equalsIgnoreCase(id)) {
                    return preset;
                }
            }
            return CUSTOM;
        }
    }

    public static final Codec<WorldHeightConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.optionalFieldOf("preset", "custom").forGetter(c -> c.preset.getId()),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(WorldHeightConfig::minY),
            Codec.INT.optionalFieldOf("gen_depth", 384).forGetter(WorldHeightConfig::genDepth),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(WorldHeightConfig::seaLevel)
        ).apply(instance, WorldHeightConfig::of)
    );

    private final Preset preset;
    private final int minY;
    private final int genDepth;
    private final int seaLevel;

    private WorldHeightConfig(Preset preset, int minY, int genDepth, int seaLevel) {
        this.preset = preset;
        this.minY = alignMinY(minY);
        this.genDepth = alignGenDepth(genDepth);
        this.seaLevel = seaLevel;

        validateAndWarn();
    }

    public static WorldHeightConfig of(String presetId, int minY, int genDepth, int seaLevel) {
        Preset preset = Preset.fromId(presetId);
        if (preset != Preset.CUSTOM && presetId != null && !presetId.equalsIgnoreCase("custom")) {
            return new WorldHeightConfig(preset, preset.defaultMinY, preset.defaultGenDepth, preset.defaultSeaLevel);
        }
        return new WorldHeightConfig(Preset.CUSTOM, minY, genDepth, seaLevel);
    }

    public static WorldHeightConfig fromPreset(Preset preset) {
        return new WorldHeightConfig(preset, preset.defaultMinY, preset.defaultGenDepth, preset.defaultSeaLevel);
    }

    public static WorldHeightConfig custom(int minY, int genDepth, int seaLevel) {
        return new WorldHeightConfig(Preset.CUSTOM, minY, genDepth, seaLevel);
    }

    public Preset preset() {
        return preset;
    }

    public int minY() {
        return minY;
    }

    public int genDepth() {
        return genDepth;
    }

    public int maxY() {
        return minY + genDepth;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Minecraft chunk sections must align to 16 blocks.
     */
    public static int alignMinY(int rawMinY) {
        return Math.floorDiv(rawMinY, 16) * 16;
    }

    /**
     * Generation depth must be a positive multiple of 16.
     */
    public static int alignGenDepth(int rawDepth) {
        int aligned = (Math.max(16, rawDepth + 15) / 16) * 16;
        return Math.min(2048, aligned); // Clamp to 2048 as maximum practical height
    }

    private void validateAndWarn() {
        int sections = genDepth / 16;
        if (genDepth > 576) {
            LOGGER.warn(
                "[Worldgen V2] Massive world height configured! minY={}, genDepth={} ({} sections, maxY={}). " +
                "Extreme heights will increase chunk meshing latency, network packet size, and RAM consumption.",
                minY, genDepth, sections, maxY()
            );
        } else {
            LOGGER.info(
                "[Worldgen V2] World height active: minY={}, genDepth={} ({} sections, maxY={}, seaLevel={}, preset={})",
                minY, genDepth, sections, maxY(), seaLevel, preset.getId()
            );
        }
    }
}
