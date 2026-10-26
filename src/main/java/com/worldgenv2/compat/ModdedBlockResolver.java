package com.worldgenv2.compat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resilient, multi-tier block resolver supporting third-party mods, tags, and fallback cascades.
 */
public final class ModdedBlockResolver<T> {

    private final Function<String, T> blockLookup;
    private final Map<String, T> cache = new HashMap<>();
    private final T defaultFallback;

    public ModdedBlockResolver(Function<String, T> blockLookup, T defaultFallback) {
        this.blockLookup = blockLookup;
        this.defaultFallback = defaultFallback;
    }

    /**
     * Resolves a block identifier with an ordered list of fallbacks.
     * Useful for cross-mod compatibility (e.g., if biomesoplenty:fir_log is missing, use minecraft:spruce_log).
     */
    public T resolveWithFallback(String primaryId, List<String> fallbackChain) {
        T resolved = resolve(primaryId);
        if (resolved != null && !resolved.equals(defaultFallback)) {
            return resolved;
        }

        if (fallbackChain != null) {
            for (String fallbackId : fallbackChain) {
                resolved = resolve(fallbackId);
                if (resolved != null && !resolved.equals(defaultFallback)) {
                    return resolved;
                }
            }
        }

        return defaultFallback;
    }

    /**
     * Resolves a block identifier from cache or registry.
     */
    public T resolve(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return defaultFallback;
        }

        T cached = cache.get(blockId);
        if (cached != null) {
            return cached;
        }

        try {
            T resolved = blockLookup.apply(blockId);
            if (resolved != null) {
                cache.put(blockId, resolved);
                return resolved;
            }
        } catch (Exception ignored) {
            // Mod not present or invalid state - gracefully fall back
        }

        return defaultFallback;
    }

    public void clearCache() {
        cache.clear();
    }
}
