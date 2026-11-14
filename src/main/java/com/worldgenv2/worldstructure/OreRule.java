package com.worldgenv2.worldstructure;

import java.util.List;
import java.util.Set;

/**
 * Declarative ore/vein rule evaluated by the chunk generator (nothing from vanilla feature generation is used).
 *
 * @param id            unique name
 * @param block         block id placed, e.g. {@code minecraft:coal_ore}
 * @param replaces      block ids a vein may overwrite (empty = the dimension's base rock family)
 * @param veinSize      approximate blocks per vein
 * @param count         vein attempts per chunk
 * @param minY          lowest vein centre
 * @param maxY          highest vein centre
 * @param triangle      true = density peaks at the middle of the Y range (vanilla "triangle"); false = uniform
 * @param allowedBiomes compiled biome names the vein centre must lie in (empty = anywhere)
 */
public record OreRule(
    String id,
    String block,
    List<String> replaces,
    int veinSize,
    int count,
    int minY,
    int maxY,
    boolean triangle,
    Set<String> allowedBiomes
) {
    public OreRule {
        replaces = replaces == null ? List.of() : List.copyOf(replaces);
        veinSize = Math.max(1, Math.min(64, veinSize));
        count = Math.max(0, Math.min(256, count));
        if (maxY < minY) { int t = minY; minY = maxY; maxY = t; }
        allowedBiomes = allowedBiomes == null ? Set.of() : Set.copyOf(allowedBiomes);
    }
}
