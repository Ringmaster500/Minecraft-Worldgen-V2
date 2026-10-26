package com.worldgenv2.worldstructure;

/**
 * Position of a {@link TileBiome} inside the macro climate space used by {@link BiCarta}.
 * All axes are normalized to [-1, 1].
 *
 * @param continentalness -1 = deep ocean basin, 0 = coast / lowlands, +1 = high mountain interior
 * @param temperature     -1 = frozen, +1 = scorching
 * @param humidity        -1 = arid, +1 = soaked
 */
public record ClimateTarget(float continentalness, float temperature, float humidity) {
}
