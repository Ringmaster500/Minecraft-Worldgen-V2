package com.hypixel.hytale.builtin.hytalegenerator.compat.hytale;

import com.hypixel.hytale.builtin.hytalegenerator.prefab.Prefab;
import com.hypixel.hytale.builtin.hytalegenerator.prefab.PrefabLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Addon translator that loads Hytale .prefab.json blueprints and translates their hytale:*
 * block palettes into native {@link Prefab} instances via {@link BlockMappingRegistry}.
 */
public final class HytalePrefabTranslator {

    private final PrefabLoader delegatedLoader;

    public HytalePrefabTranslator(BlockMappingRegistry blockMappingRegistry) {
        Objects.requireNonNull(blockMappingRegistry, "blockMappingRegistry");
        this.delegatedLoader = new PrefabLoader(blockMappingRegistry::toMinecraft);
    }

    public Prefab loadFromStream(InputStream stream) throws IOException {
        return delegatedLoader.loadFromStream(stream);
    }

    public Prefab parseJson(String jsonContent) {
        return delegatedLoader.parseJson(jsonContent);
    }
}
