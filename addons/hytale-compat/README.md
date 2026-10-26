# Hytale Compatibility & Asset Translator Addon (Set Aside)

> [!WARNING]
> **DORMANT / NOT IN ACTIVE DEVELOPMENT**: We are **NOT** working on this Hytale compatibility addon right now.
> All code and assets in this directory have been extracted out of the main mod so that the core **Minecraft Worldgen V2** engine is 100% standalone and native to Minecraft.
> This addon is set aside and parked for potential future exploration as a completely decoupled optional addon.

---

## Purpose & Scope
This directory contains all **Hytale-specific** translation layers, block mappings (`hytale:*` $\leftrightarrow$ `minecraft:*`), Orbis Zone biome presets, and `.prefab.json` converters.

## Contents
- `src/main/java/.../compat/hytale/BlockMappingRegistry.java` — Bidirectional `hytale:*` $\leftrightarrow$ `minecraft:*` block & property mapper.
- `src/main/java/.../compat/hytale/HytaleBiomeDefinition.java` & `HytaleBiomeTranslator.java` — Translates Hytale Orbis biome JSONs into native `TileBiome` and `MaterialProvider` instances.
- `src/main/java/.../compat/hytale/HytalePrefabTranslator.java` — Translates Hytale `.prefab.json` palettes into native `Prefab` instances via `BlockMappingRegistry`.
- `src/main/resources/data/hytalecompat/biomes/` — Hytale Orbis biome presets (`verdant_forest.json`, `whispering_grove.json`, `howling_sands.json`, `boreal_tundra.json`).
- `src/main/resources/data/hytalecompat/prefabs/` — Hytale structure blueprints (`kweebec_shelter_01.prefab.json`, `corridor_path_01.prefab.json`).
