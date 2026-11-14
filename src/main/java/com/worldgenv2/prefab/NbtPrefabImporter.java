package com.worldgenv2.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts standard Minecraft Structure Template {@code .nbt} files into Worldgen V2 {@code .prefab.json}
 * objects, preserving block states, palette indices, and container {@code LootTable} tags.
 */
public final class NbtPrefabImporter {
    private NbtPrefabImporter() {}

    public static JsonObject importFile(Path nbtPath, String prefabName) throws IOException {
        try (InputStream in = Files.newInputStream(nbtPath)) {
            return importStream(in, prefabName);
        }
    }

    public static JsonObject importStream(InputStream in, String prefabName) throws IOException {
        CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        return fromNbt(root, prefabName);
    }

    public static JsonObject fromNbt(CompoundTag root, String prefabName) {
        JsonObject out = new JsonObject();
        out.addProperty("name", prefabName != null && !prefabName.isBlank() ? prefabName : "worldgenv2:imported_nbt");
        out.addProperty("version", 2);

        ListTag sizeTag = root.getListOrEmpty("size");
        int sx = sizeTag.size() > 0 ? sizeTag.getIntOr(0, 1) : 1;
        int sy = sizeTag.size() > 1 ? sizeTag.getIntOr(1, 1) : 1;
        int sz = sizeTag.size() > 2 ? sizeTag.getIntOr(2, 1) : 1;
        out.add("size", vec(sx, sy, sz));
        out.add("anchor", vec(sx / 2, 0, sz / 2));

        JsonArray palette = new JsonArray();
        ListTag palTag = root.getListOrEmpty("palette");
        if (palTag.isEmpty()) {
            ListTag palettesTag = root.getListOrEmpty("palettes");
            if (!palettesTag.isEmpty()) {
                palTag = palettesTag.getListOrEmpty(0);
            }
        }
        int airIndex = -1;
        for (int i = 0; i < palTag.size(); i++) {
            CompoundTag entry = palTag.getCompoundOrEmpty(i);
            String blockName = entry.getStringOr("Name", "minecraft:air");
            if ("minecraft:air".equals(blockName) || "minecraft:structure_void".equals(blockName)) {
                if ("minecraft:structure_void".equals(blockName)) {
                    airIndex = airIndex < 0 ? i : airIndex;
                }
            }
            JsonObject pe = new JsonObject();
            pe.addProperty("id", i);
            pe.addProperty("name", "minecraft:structure_void".equals(blockName) ? "minecraft:air" : blockName);
            CompoundTag props = entry.getCompoundOrEmpty("Properties");
            if (!props.isEmpty()) {
                JsonObject pj = new JsonObject();
                for (String key : props.keySet()) {
                    Tag t = props.get(key);
                    if (t != null) pj.addProperty(key, props.getStringOr(key, ""));
                }
                pe.add("properties", pj);
            }
            palette.add(pe);
        }
        out.add("palette", palette);

        JsonArray blocks = new JsonArray();
        ListTag blocksTag = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag b = blocksTag.getCompoundOrEmpty(i);
            int state = b.getIntOr("state", 0);
            if (state == airIndex) continue;
            ListTag pos = b.getListOrEmpty("pos");
            if (pos.size() < 3) continue;
            JsonObject bj = new JsonObject();
            bj.addProperty("x", pos.getIntOr(0, 0));
            bj.addProperty("y", pos.getIntOr(1, 0));
            bj.addProperty("z", pos.getIntOr(2, 0));
            bj.addProperty("paletteIndex", state);
            CompoundTag nbt = b.getCompoundOrEmpty("nbt");
            if (!nbt.isEmpty()) {
                String loot = nbt.getStringOr("LootTable", "");
                if (!loot.isBlank()) {
                    bj.addProperty("lootTable", loot);
                }
            }
            blocks.add(bj);
        }
        out.add("blocks", blocks);
        return out;
    }

    private static JsonObject vec(int x, int y, int z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }
}
