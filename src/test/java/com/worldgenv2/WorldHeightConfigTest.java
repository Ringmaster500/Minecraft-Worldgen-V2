package com.worldgenv2;

import com.worldgenv2.minecraft.WorldHeightConfig;

public final class WorldHeightConfigTest {

    public static void main(String[] args) {
        System.out.println("=== WorldHeightConfig Verification ===");

        // Test Standard Preset
        WorldHeightConfig std = WorldHeightConfig.fromPreset(WorldHeightConfig.Preset.STANDARD);
        assert std.preset() == WorldHeightConfig.Preset.STANDARD : "Preset mismatch";
        assert std.minY() == -64 : "MinY mismatch";
        assert std.genDepth() == 384 : "GenDepth mismatch";
        assert std.maxY() == 320 : "MaxY mismatch";
        assert std.seaLevel() == 63 : "SeaLevel mismatch";
        System.out.println("[PASS] Standard preset: minY=-64, depth=384, maxY=320, seaLevel=63");

        // Test Hytale 512 Preset
        WorldHeightConfig h512 = WorldHeightConfig.fromPreset(WorldHeightConfig.Preset.HYTALE_512);
        assert h512.preset() == WorldHeightConfig.Preset.HYTALE_512 : "Preset mismatch";
        assert h512.minY() == 0 : "MinY mismatch";
        assert h512.genDepth() == 512 : "GenDepth mismatch";
        assert h512.maxY() == 512 : "MaxY mismatch";
        assert h512.seaLevel() == 128 : "SeaLevel mismatch";
        System.out.println("[PASS] Hytale 512 preset: minY=0, depth=512, maxY=512, seaLevel=128");

        // Test Hytale Extended Preset
        WorldHeightConfig hExt = WorldHeightConfig.fromPreset(WorldHeightConfig.Preset.HYTALE_EXTENDED);
        assert hExt.preset() == WorldHeightConfig.Preset.HYTALE_EXTENDED : "Preset mismatch";
        assert hExt.minY() == -64 : "MinY mismatch";
        assert hExt.genDepth() == 576 : "GenDepth mismatch";
        assert hExt.maxY() == 512 : "MaxY mismatch";
        assert hExt.seaLevel() == 128 : "SeaLevel mismatch";
        System.out.println("[PASS] Hytale Extended preset: minY=-64, depth=576, maxY=512, seaLevel=128");

        // Test Epic 1024 Preset
        WorldHeightConfig epic = WorldHeightConfig.fromPreset(WorldHeightConfig.Preset.EPIC_1024);
        assert epic.preset() == WorldHeightConfig.Preset.EPIC_1024 : "Preset mismatch";
        assert epic.minY() == -64 : "MinY mismatch";
        assert epic.genDepth() == 1024 : "GenDepth mismatch";
        assert epic.maxY() == 960 : "MaxY mismatch";
        System.out.println("[PASS] Epic 1024 preset: minY=-64, depth=1024, maxY=960");

        // Test Custom Manual Numbers with 16-block alignment
        WorldHeightConfig custom = WorldHeightConfig.custom(-50, 450, 70);
        assert custom.preset() == WorldHeightConfig.Preset.CUSTOM : "Preset mismatch";
        assert custom.minY() == -64 : "MinY alignment failed: " + custom.minY();
        assert custom.genDepth() == 464 : "GenDepth alignment failed: " + custom.genDepth();
        assert custom.maxY() == 400 : "MaxY calculation failed: " + custom.maxY();
        assert custom.seaLevel() == 70 : "SeaLevel mismatch";
        System.out.println("[PASS] Custom alignment: raw(-50, 450) -> aligned(minY=-64, depth=464, maxY=400)");

        // Test string identifier factory
        WorldHeightConfig strPreset = WorldHeightConfig.of("hytale_extended", -64, 384, 63);
        assert strPreset.preset() == WorldHeightConfig.Preset.HYTALE_EXTENDED : "String factory preset mismatch";
        assert strPreset.genDepth() == 576 : "String factory depth mismatch";

        System.out.println("ALL WORLD HEIGHT CONFIG TESTS PASSED!");
    }
}
