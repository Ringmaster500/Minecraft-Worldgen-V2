package com.worldgenv2.studio.shell;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every block (and water and lava) the game knows, with its item icon, built once on first use. Vanilla and modded alike. */
final class BlockCatalog {
    private BlockCatalog() {}

    /** The icon is made on first use: item components are not bound until a world is loaded, so it cannot be built up front. */
    static final class Entry {
        private final String id, name, mod, search;
        private final Item item;
        private ItemStack stack;

        Entry(String id, String name, String mod, Item item, String search) { this.id = id; this.name = name; this.mod = mod; this.item = item; this.search = search; }

        String id() { return id; }
        String name() { return name; }
        String mod() { return mod; }
        String search() { return search; }

        /** The block's item picture, or an empty stack while the game cannot make one yet (the title screen). */
        ItemStack stack() {
            if (stack != null) return stack;
            try { stack = new ItemStack(item); } catch (RuntimeException e) { return ItemStack.EMPTY; }
            return stack;
        }
    }

    private static List<Entry> all;
    private static final Map<String, Entry> BY_ID = new HashMap<>();

    static synchronized List<Entry> all() {
        if (all != null) return all;
        List<Entry> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            if (id == null || b == Blocks.AIR) continue;
            Item item = b.asItem();
            Item icon;
            if (b == Blocks.WATER) icon = Items.WATER_BUCKET;
            else if (b == Blocks.LAVA) icon = Items.LAVA_BUCKET;
            else if (item == Items.AIR) continue;      // technical blocks without an item have no icon to show
            else icon = item;
            String name = b.getName().getString();
            Entry e = new Entry(id.toString(), name, id.getNamespace(), icon, (name + " " + id).toLowerCase(Locale.ROOT));
            out.add(e);
            BY_ID.put(e.id(), e);
        }
        out.sort((a, c) -> {
            boolean av = a.mod().equals("minecraft"), cv = c.mod().equals("minecraft");
            if (av != cv) return av ? -1 : 1;
            return a.name().compareToIgnoreCase(c.name());
        });
        all = out;
        return out;
    }

    /** The entry for a block id such as {@code minecraft:stone} (a trailing {@code [state]} is ignored), or null. */
    static Entry of(String id) {
        if (id == null || id.isEmpty()) return null;
        all();
        int br = id.indexOf('[');
        return BY_ID.get(br < 0 ? id.trim() : id.substring(0, br).trim());
    }

    /** Best matches first: names or ids starting with the query, then those containing it. */
    static List<Entry> search(String q) {
        List<Entry> src = all();
        String s = q.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return src;
        List<Entry> starts = new ArrayList<>(), has = new ArrayList<>();
        for (Entry e : src) {
            String n = e.name().toLowerCase(Locale.ROOT);
            if (n.startsWith(s) || e.id().substring(e.id().indexOf(':') + 1).startsWith(s)) starts.add(e);
            else if (e.search().contains(s)) has.add(e);
        }
        starts.addAll(has);
        return starts;
    }
}
