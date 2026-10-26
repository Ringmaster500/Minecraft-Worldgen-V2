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
