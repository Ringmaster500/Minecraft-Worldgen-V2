package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;

/**
 * Remembers what a distribution's prop built at an anchor, so that the next chunk whose context contains the same anchor replays
 * the blocks instead of running the prop again. Within a stage a prop reads only the previous stage's blocks, so its result is a
 * function of the anchor alone; the memo only keeps results whose reads all stayed inside the context they were computed in.
 */
public interface AnchorMemo<T> {

    /** False when this memo only shares per-cell values and does not remember anchors. */
    default boolean remembers() { return true; }

    /** Writes the remembered blocks for this anchor into {@code space} and returns true, or returns false if there are none. */
    boolean replay(long seedBase, int x, int y, int z, Object prop, VoxelSpace<T> space);

    /** A space that passes everything to {@code space} and notes what the prop reads and writes. */
    Recorder<T> recorder(VoxelSpace<T> space);

    /** Keeps what the recorder saw, if every read stayed inside the context. */
    void remember(long seedBase, int x, int y, int z, Object prop, Recorder<T> recorder);

    /**
     * A value computed once per distribution ({@code owner}, compared by identity) and 16 x 16 cell and shared by every chunk (the positions a distribution yields in a cell
     * depend only on the cell). Implementations without a cache just compute it.
     */
    default Object cell(Object owner, long seedBase, int cellX, int cellZ, java.util.function.Supplier<Object> compute) { return compute.get(); }

    /** Dev check: recomputes a remembered anchor (writes discarded) and reports any difference from what was remembered. */
    default void verify(long seedBase, int x, int y, int z, Object prop, VoxelSpace<T> space, java.util.function.Consumer<VoxelSpace<T>> generate) {}

    interface Recorder<T> extends VoxelSpace<T> {}
}