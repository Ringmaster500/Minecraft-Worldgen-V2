package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import java.util.List;
import java.util.Objects;

/**
 * Places an explicit, manually configured list of offset block positions relative to the anchor.
 */
public final class ManualProp<T> implements Prop<T> {

    public record Entry<T>(int offsetX, int offsetY, int offsetZ, T block) {
        public Entry {
            Objects.requireNonNull(block, "block");
        }
    }

    private final List<Entry<T>> entries;

    public ManualProp(List<Entry<T>> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    @Override
    public boolean generate(Context<T> context) {
        if (entries.isEmpty()) {
            return false;
        }
        VoxelSpace<T> space = context.voxelSpace();
        int cx = context.worldX();
        int cy = context.worldY();
        int cz = context.worldZ();

        boolean placed = false;
        for (Entry<T> entry : entries) {
            int y = cy + entry.offsetY();
            if (y >= space.getMinY() && y < space.getMaxY()) {
                space.setBlock(cx + entry.offsetX(), y, cz + entry.offsetZ(), entry.block());
                placed = true;
            }
        }
        return placed;
    }
}
