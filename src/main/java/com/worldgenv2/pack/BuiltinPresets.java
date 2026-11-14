package com.worldgenv2.pack;

import com.google.gson.JsonObject;
import com.worldgenv2.pack.WorldGenV2Addons.Preset;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.project.Templates;

/**
 * The one built-in starting point: <b>Default</b>. A few example biomes, the Overworld / Nether / End, and every optional
 * feature left at its default or off (ores use the built-in distribution; zones, structures, shapes are off).
 * Worlds with no project chosen generate from this, so the mod works with zero configuration.
 */
public final class BuiltinPresets {
    private BuiltinPresets() {}

    public static final String DEFAULT_ID = "worldgenv2:default";

    static void register() {
        WorldGenV2Addons.registerPreset(new Preset(DEFAULT_ID, "Default",
                "Example biomes and the three dimensions with default ores. Everything else is off until you turn it on.", "world",
                BuiltinPresets::defaultBundle));
    }

    public static JsonObject defaultBundle() {
        StudioProject p = StudioProject.fromBundle("Default", new JsonObject());
        Templates.populateStarter(p);
        return p.toBundle(DEFAULT_ID, "Default", "", "world");
    }
}
