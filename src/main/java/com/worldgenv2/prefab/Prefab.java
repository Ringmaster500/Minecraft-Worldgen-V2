package com.worldgenv2.prefab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Native in-memory structure prefab blueprint for Minecraft Worldgen V2.
 * Stores sparse voxel coordinates, native block state palettes, modular attachment sockets,
 * and adaptive foundation rules without any external game translation layer.
 */
public final class Prefab {

    public record Vec3i(int x, int y, int z) {}

    public enum Facing {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0),
        UP(0, 0),
        DOWN(0, 0);

        private final int stepX;
        private final int stepZ;

        Facing(int stepX, int stepZ) {
            this.stepX = stepX;
            this.stepZ = stepZ;
        }

        public int stepX() {
            return stepX;
        }

        public int stepZ() {
            return stepZ;
        }

        public Facing opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }

        public static Facing fromString(String value) {
            if (value == null) return NORTH;
            return switch (value.trim().toUpperCase()) {
                case "EAST" -> EAST;
                case "SOUTH" -> SOUTH;
                case "WEST" -> WEST;
                case "UP" -> UP;
                case "DOWN" -> DOWN;
                default -> NORTH;
            };
        }
    }

    public record PaletteEntry(
        int id,
        String blockId,
        String formattedBlockState,
        Map<String, String> properties
    ) {
        public PaletteEntry {
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(formattedBlockState, "formattedBlockState");
            properties = properties == null ? Map.of() : Map.copyOf(properties);
        }

        public static PaletteEntry of(int id, String blockId, Map<String, String> properties) {
            Map<String, String> safeProps = properties == null ? Map.of() : Map.copyOf(properties);
            String formatted = formatBlockState(blockId, safeProps);
            return new PaletteEntry(id, blockId, formatted, safeProps);
        }

        public boolean isEmpty() {
            return "minecraft:air".equals(blockId) || "air".equals(blockId);
        }

        private static String formatBlockState(String blockId, Map<String, String> properties) {
            if (properties == null || properties.isEmpty()) {
                return blockId;
            }
            StringJoiner joiner = new StringJoiner(",", blockId + "[", "]");
            properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> joiner.add(e.getKey() + "=" + e.getValue()));
            return joiner.toString();
        }
    }

    public record VoxelEntry(int x, int y, int z, int paletteIndex) {}

    public record PrefabSocket(
        String name,
        String type,
        String gender,
        Vec3i position,
        Facing facing,
        List<String> compatibleTags
    ) {
        public PrefabSocket {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(gender, "gender");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(facing, "facing");
            compatibleTags = compatibleTags == null ? List.of() : List.copyOf(compatibleTags);
        }

        public boolean isCompatibleWith(PrefabSocket other) {
            if (!this.type.equalsIgnoreCase(other.type)) {
                return false;
            }
            if (!this.gender.equalsIgnoreCase("ANY") && !other.gender.equalsIgnoreCase("ANY")) {
                if (this.gender.equalsIgnoreCase(other.gender)) {
                    return false;
                }
            }
            if (this.compatibleTags.isEmpty() || other.compatibleTags.isEmpty()) {
                return true;
            }
            for (String tag : this.compatibleTags) {
                if (other.compatibleTags.contains(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record AdaptiveFoundation(
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        String fillMaterial,
        int maxDepth
    ) {}

    private final String name;
    private final int version;
    private final Vec3i size;
    private final Vec3i anchor;
    private final List<PaletteEntry> palette;
    private final Map<Integer, PaletteEntry> paletteById;
    private final List<VoxelEntry> blocks;
    private final List<PrefabSocket> sockets;
    private final List<AdaptiveFoundation> adaptiveFoundations;

    public Prefab(
        String name,
        int version,
        Vec3i size,
        Vec3i anchor,
        List<PaletteEntry> palette,
        List<VoxelEntry> blocks,
        List<PrefabSocket> sockets,
        List<AdaptiveFoundation> adaptiveFoundations
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = version;
        this.size = Objects.requireNonNull(size, "size");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.palette = List.copyOf(palette);
        Map<Integer, PaletteEntry> map = new HashMap<>();
        for (PaletteEntry entry : this.palette) {
            map.put(entry.id(), entry);
        }
        this.paletteById = Map.copyOf(map);
        this.blocks = List.copyOf(blocks);
        this.sockets = List.copyOf(sockets);
        this.adaptiveFoundations = List.copyOf(adaptiveFoundations);
    }

    /**
     * A copy turned clockwise (seen from above) by {@code quarterTurns} * 90 degrees around the vertical axis.
     * Voxels, sockets, foundations and the anchor are turned together, and block-state properties that depend on
     * direction ({@code facing}, {@code axis}, {@code rotation}, fence/wall sides) are turned to match.
     */
    public Prefab rotated(int quarterTurns) {
        int t = Math.floorMod(quarterTurns, 4);
        if (t == 0) return this;
        Prefab cur = this;
        for (int i = 0; i < t; i++) cur = cur.turnOnce();
        return cur;
    }

    private Prefab turnOnce() {
        int sx = size.x(), sz = size.z();
        Vec3i newSize = new Vec3i(sz, size.y(), sx);
        Vec3i newAnchor = new Vec3i(sz - 1 - anchor.z(), anchor.y(), anchor.x());
        List<PaletteEntry> pal = new java.util.ArrayList<>();
        for (PaletteEntry e : palette) pal.add(PaletteEntry.of(e.id(), e.blockId(), turnProperties(e.properties())));
        List<VoxelEntry> vox = new java.util.ArrayList<>(blocks.size());
        for (VoxelEntry v : blocks) vox.add(new VoxelEntry(sz - 1 - v.z(), v.y(), v.x(), v.paletteIndex()));
        List<PrefabSocket> soc = new java.util.ArrayList<>();
        for (PrefabSocket s : sockets) {
            Vec3i p = s.position();
            soc.add(new PrefabSocket(s.name(), s.type(), s.gender(), new Vec3i(sz - 1 - p.z(), p.y(), p.x()),
                    turnFacing(s.facing()), s.compatibleTags()));
        }
        List<AdaptiveFoundation> fnd = new java.util.ArrayList<>();
        for (AdaptiveFoundation f : adaptiveFoundations) {
            fnd.add(new AdaptiveFoundation(sz - 1 - f.maxZ(), f.minX(), sz - 1 - f.minZ(), f.maxX(), f.fillMaterial(), f.maxDepth()));
        }
        return new Prefab(name, version, newSize, newAnchor, pal, vox, soc, fnd);
    }

    private static Facing turnFacing(Facing f) {
        return switch (f) {
            case NORTH -> Facing.EAST;
            case EAST -> Facing.SOUTH;
            case SOUTH -> Facing.WEST;
            case WEST -> Facing.NORTH;
            default -> f;
        };
    }

    private static String turnCompass(String v) {
        return switch (v) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> v;
        };
    }

    private static Map<String, String> turnProperties(Map<String, String> props) {
        if (props.isEmpty()) return props;
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            switch (k) {
                case "facing" -> out.put(k, turnCompass(v));
                case "axis" -> out.put(k, v.equals("x") ? "z" : v.equals("z") ? "x" : v);
                case "rotation" -> {
                    try { out.put(k, Integer.toString((Integer.parseInt(v) + 4) & 15)); } catch (NumberFormatException ex) { out.put(k, v); }
                }
                // fence / wall / vine style sides: the value that was on 'north' is now on 'east', etc.
                case "north" -> out.put("east", v);
                case "east" -> out.put("south", v);
                case "south" -> out.put("west", v);
                case "west" -> out.put("north", v);
                default -> out.put(k, v);
            }
        }
        return out;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public Vec3i size() {
        return size;
    }

    public Vec3i anchor() {
        return anchor;
    }

    public List<PaletteEntry> palette() {
        return palette;
    }

    public PaletteEntry resolvePalette(int paletteIndex) {
        return paletteById.get(paletteIndex);
    }

    public List<VoxelEntry> blocks() {
        return blocks;
    }

    public List<PrefabSocket> sockets() {
        return sockets;
    }

    public List<AdaptiveFoundation> adaptiveFoundations() {
        return adaptiveFoundations;
    }
}
