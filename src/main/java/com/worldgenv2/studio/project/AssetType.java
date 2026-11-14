package com.worldgenv2.studio.project;

/** Kinds of asset a Studio project contains. One folder per type (WORLD is a single fixed file). */
public enum AssetType {
    WORLD("World", "", 0xFFFFAA00),
    DIMENSION("Dimension", "dimensions", 0xFF4FA3E3),
    ZONE("Zone", "zones", 0xFFE89A3C),
    BIOME("Biome", "biomes", 0xFF5FBF6A),
    PREFAB("Prefab", "prefabs", 0xFFB37BE8);

    public final String label;
    public final String folder;
    public final int color;

    AssetType(String label, String folder, int color) {
        this.label = label;
        this.folder = folder;
        this.color = color;
    }

    public String plural() {
        return this == WORLD ? "World" : label + "s";
    }

    public static AssetType ofPath(String path) {
        if (path.equals(Asset.WORLD_PATH)) return WORLD;
        for (AssetType t : values()) if (!t.folder.isEmpty() && path.startsWith(t.folder + "/")) return t;
        return null;
    }
}
