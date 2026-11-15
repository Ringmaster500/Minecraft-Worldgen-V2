package com.worldgenv2.worldstructure;

/** Something that reshapes a terrain density column after the biomes are blended: river valleys, caves. */
public interface ColumnCarver {
    /** Edits the density column for block column (x, z), Y from minY (inclusive) to maxY (exclusive); positive is solid. */
    void carveColumn(float x, float z, int minY, int maxY, float[] col);
}