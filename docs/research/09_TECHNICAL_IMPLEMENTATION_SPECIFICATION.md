# Hytale WorldGen V2: Technical Implementation Specification & Architecture

## 1. Modular Architecture Overview

To achieve industrial stability, long-term maintainability, and peak performance, our implementation is partitioned into two clean layers:

```
                      Minecraft Worldgen V2 Project
                                    │
       ┌────────────────────────────┴───────────────────────────┐
       ▼                                                        ▼
[ core/ ]                                                [ mod/ ]
hytale-worldgen-core                                     hytale-worldgen-minecraft
(Pure Java 25 Foundation Engine)                         (Minecraft 26.1 Adapter Mod)
──────────────────────────────────                       ───────────────────────────
• Zero Minecraft Dependencies                            • NeoForge 26.1 / Fabric 26.1
• Fast 3D Density Engine (FastNoiseLite)                 • HytaleChunkGenerator
• Staged Chunk Pipeline (StagedChunkGenerator)           • HytaleBiomeSource
• BiCarta & Voronoi Cellular Grid                        • Voxel Palette Adapter
• SpaceAndDepth Material System                          • In-Game Commands:
• Pattern Matchers (Slope, Surface, Normal)                - /hytale reload
• Single-Pass Prop & Socket Prefab Engine                  - /hytale viewport
• JSON Biome AST Compiler & Watcher                      • DataPack & Config Loaders
```

---

## 2. Directory & Package Structure

```
Minecraft Worldgen V2/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── docs/
│   └── research/
│       ├── 01_PHILOSOPHY_AND_PARADIGM_SHIFT.md
│       ├── 02_STAGED_CHUNK_GENERATION_PIPELINE.md
│       ├── 03_MACRO_WORLD_AND_BIOME_ARCHITECTURE.md
│       ├── 04_DENSITY_FUNCTIONS_AND_3D_VOLUMETRIC_MATH.md
│       ├── 05_MATERIAL_PROVIDERS_AND_SURFACE_RULES.md
│       ├── 06_PROP_SYSTEM_AND_PREFAB_DISTRIBUTION.md
│       ├── 07_ASSET_SCHEMA_AND_NODE_GRAPH_SPECIFICATION.md
│       ├── 08_MINECRAFT_26_1_DEOBFUSCATED_ADAPTATION.md
│       └── 09_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md
└── src/
    └── main/
        ├── java/
        │   └── com/hypixel/hytale/builtin/hytalegenerator/
        │       ├── api/
        │       │   ├── VoxelSpace.java
        │       │   ├── ChunkCoord.java
        │       │   └── WorldContext.java
        │       ├── density/
        │       │   ├── DensityFunction.java
        │       │   ├── ConstantDensity.java
        │       │   ├── SumDensity.java
        │       │   ├── ProductDensity.java
        │       │   ├── MinDensity.java
        │       │   ├── MaxDensity.java
        │       │   ├── Noise2dDensity.java
        │       │   ├── Noise3dDensity.java
        │       │   ├── YSampledDensity.java
        │       │   └── FastNoiseLite.java
        │       ├── worldstructure/
        │       │   ├── WorldStructure.java
        │       │   ├── BiCarta.java
        │       │   ├── TileBiome.java
        │       │   ├── CustomBiome.java
        │       │   └── VoronoiPointGenerator.java
        │       ├── material/
        │       │   ├── MaterialProvider.java
        │       │   ├── ConstantMaterialProvider.java
        │       │   ├── WeightedMaterialProvider.java
        │       │   ├── SpaceAndDepthMaterialProvider.java
        │       │   └── patterns/
        │       │       ├── Pattern.java
        │       │       ├── SurfacePattern.java
        │       │       ├── WallPattern.java
        │       │       ├── CeilingPattern.java
        │       │       ├── SlopePattern.java
        │       │       └── AndPattern.java
        │       ├── props/
        │       │   ├── Prop.java
        │       │   ├── DensitySelectorProp.java
        │       │   ├── MaskProp.java
        │       │   ├── OrienterProp.java
        │       │   └── PrefabProp.java
        │       ├── engine/
        │       │   └── chunkgenerator/
        │       │       ├── StagedChunkGenerator.java
        │       │       └── stages/
        │       │           ├── BiomeStage.java
        │       │           ├── BiomeDistanceStage.java
        │       │           ├── TerrainStage.java
        │       │           └── PropStage.java
        │       └── minecraft/
        │           ├── HytaleWorldGenMod.java
        │           ├── HytaleChunkGenerator.java
        │           ├── HytaleBiomeSource.java
        │           └── MinecraftVoxelSpace.java
        └── resources/
            ├── META-INF/
            │   └── neoforge.mods.toml
            └── data/
                └── hytale/
                    └── biomes/
                        └── verdant_forest.json
```

---

## 3. Core Class Contracts

### 3.1 Density Function Interface
```java
package com.hypixel.hytale.builtin.hytalegenerator.density;

@FunctionalInterface
public interface DensityFunction {
    /**
     * Evaluates the 3D scalar density field at the given continuous coordinate.
     * 
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return Scalar density value, where > 0 is solid, < 0 is air
     */
    float compute(float x, float y, float z);
}
```

### 3.2 Material Provider Interface
```java
package com.hypixel.hytale.builtin.hytalegenerator.material;

import com.hypixel.hytale.builtin.hytalegenerator.api.VoxelSpace;

public interface MaterialProvider<T> {
    /**
     * Resolves the material block state at the given coordinate.
     */
    T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef);
}
```

### 3.3 Prop Interface
```java
package com.hypixel.hytale.builtin.hytalegenerator.props;

import com.hypixel.hytale.builtin.hytalegenerator.api.VoxelSpace;

public interface Prop<T> {
    record Context<T>(
        int worldX, int worldY, int worldZ,
        float normalX, float normalY, float normalZ,
        VoxelSpace<T> voxelSpace,
        long seed
    ) {}

    boolean generate(Context<T> context);
}
```

---

## 4. Minecraft 26.1 Adapter Contract

In `HytaleChunkGenerator.java`:
* Extends `net.minecraft.world.level.chunk.ChunkGenerator`.
* Overrides `fillFromNoise()`: Executes `StagedChunkGenerator.executeTerrainStage()`.
* Overrides `buildSurface()`: Executes `HytaleMaterialPipeline`.
* Overrides `applyBiomeDecoration()`: Executes `StagedChunkGenerator.executePropStage()`.

This cleanly maps the entire Hytale WorldGen V2 engine into Minecraft 26.1 with optimal performance, native deobfuscation, and zero legacy bloat!
