# Hytale WorldGen V2: Asset Schemas & Node Graph Specification

## 1. Directory & Asset Layout

In Minecraft Worldgen V2, assets are structured to support both standard datapack locations and an auto-mounted instance `config/worldgenv2/` hierarchy tailored for modpack creators:

```
# Modpack Customization Directory (Auto-Mounted Synthetic Datapack)
minecraft-instance/config/worldgenv2/
├── worlds/
│   └── default_world.json         # Master world configuration & Voronoi settings
├── biomes/
│   ├── temperate_forest.json      # Biome density graphs, materials & props
│   ├── alpine_peaks.json
│   └── crystal_chasm.json
├── prefabs/
│   ├── watchtower_01.prefab.json  # Native structure prefabs with adaptive foundation plinths
│   └── ancient_shrine.prefab.json
└── presets/                       # Seeded templates (template_mountain.json, etc.)

# Built-in Mod Resources (Fallback)
src/main/resources/data/worldgenv2/
├── biomes/
└── prefabs/
```

> [!NOTE]
> When Minecraft boots, `worldgenv2` auto-mounts `config/worldgenv2/` as a dynamic synthetic datapack with top override priority. Modpack creators can tweak, duplicate, or delete these JSON files directly without ever needing to compile a mod or package a zip archive.

---

## 2. The Biome JSON Asset Schema

Every biome asset is a declarative Directed Acyclic Graph (DAG) serialized to JSON. Below is the exact structural specification representing a production-grade Hytale V2 biome.

```json
{
  "$schema": "https://hytale.com/schemas/worldgen/v2/biome.json",
  "name": "VerdantForest",
  "zone": "Zone1_EmeraldGrove",
  "type": "TileBiome",
  "weight": 15.0,
  "sizeModifier": 1.25,
  "defaultTransitionDistance": 48.0,
  
  "terrain": {
    "type": "SumDensity",
    "inputs": [
      {
        "type": "VerticalGradient",
        "baseHeight": 64.0,
        "scale": 32.0
      },
      {
        "type": "ProductDensity",
        "inputs": [
          {
            "type": "Noise2dDensity",
            "noiseType": "OpenSimplex2",
            "frequency": 0.005,
            "octaves": 3,
            "amplitude": 18.0
          },
          {
            "type": "ConstantDensity",
            "value": 1.0
          }
        ]
      },
      {
        "type": "YSampledDensity",
        "yInterval": 8,
        "input": {
          "type": "Noise3dDensity",
          "noiseType": "OpenSimplex2",
          "frequency": 0.012,
          "octaves": 4,
          "amplitude": 12.0
        }
      }
    ]
  },

  "materialProvider": {
    "type": "QueueMaterialProvider",
    "providers": [
      {
        "pattern": {
          "type": "AndPattern",
          "patterns": [
            { "type": "SurfacePattern" },
            { "type": "SlopePattern", "maxAngleDegrees": 42.0 },
            { "type": "SpaceCheckPattern", "minAirAbove": 3 }
          ]
        },
        "provider": {
          "type": "SpaceAndDepth",
          "layers": [
            { "material": "minecraft:grass_block", "thickness": 1 },
            { "material": "minecraft:dirt", "thickness": 4 },
            { "material": "minecraft:stone", "thickness": 999 }
          ]
        }
      },
      {
        "pattern": {
          "type": "AndPattern",
          "patterns": [
            { "type": "SurfacePattern" },
            { "type": "SlopePattern", "minAngleDegrees": 42.0 }
          ]
        },
        "provider": {
          "type": "WeightedMaterialProvider",
          "weights": [
            { "material": "minecraft:stone", "weight": 70 },
            { "material": "minecraft:andesite", "weight": 20 },
            { "material": "minecraft:gravel", "weight": 10 }
          ]
        }
      },
      {
        "pattern": { "type": "CeilingPattern" },
        "provider": {
          "type": "ConstantMaterialProvider",
          "material": "minecraft:stone"
        }
      }
    ],
    "fallback": {
      "type": "ConstantMaterialProvider",
      "material": "minecraft:stone"
    }
  },

  "props": [
    {
      "type": "DensitySelectorProp",
      "threshold": 0.55,
      "densitySource": {
        "type": "Noise2dDensity",
        "noiseType": "Cellular",
        "frequency": 0.02
      },
      "child": {
        "type": "MaskProp",
        "requiredBlock": "minecraft:grass_block",
        "child": {
          "type": "StaticRotatorProp",
          "child": {
            "type": "PrefabProp",
            "prefab": "hytale:trees/oak_large_01"
          }
        }
      }
    },
    {
      "type": "DensitySelectorProp",
      "threshold": 0.3,
      "densitySource": {
        "type": "Noise2dDensity",
        "noiseType": "OpenSimplex2",
        "frequency": 0.08
      },
      "child": {
        "type": "WeightedProp",
        "props": [
          { "type": "BlockProp", "block": "minecraft:poppy", "weight": 30 },
          { "type": "BlockProp", "block": "minecraft:dandelion", "weight": 20 },
          { "type": "BlockProp", "block": "minecraft:short_grass", "weight": 50 }
        ]
      }
    }
  ]
}
```

---

## 3. Node Graph Execution Model

When a biome asset is loaded by the engine:
1. **JSON Parsing & Validation:** The JSON is parsed into a strongly typed AST (Abstract Syntax Tree).
2. **Graph Optimization & Constant Folding:**
   * Nodes like `ConstantDensity` multiplied by zero are pruned.
   * Consecutive `OffsetDensity` or `ScaleDensity` nodes are collapsed into a single affine transform matrix.
3. **Bytecode Compilation / JIT Execution:**
   * In performance-critical execution, the AST is converted into a flat bytecode stack or compiled into direct JVM bytecode using `MethodHandles` / `LambdaMetafactory`.
   * Density evaluation becomes a tight loop of direct CPU instructions without heap allocations or object traversing.

---

## 4. Live Hot-Reloading & Viewport Architecture

To support the designer-led iterative workflow, WorldGen V2 implements a reactive file-watcher and memory cache invalidation loop:

```
Designer Edits Biome.json In Editor
            │
            ▼
File Watcher Triggers Asset Reload
            │
            ▼
HytaleGenerator Re-compiles Node AST
            │
            ▼
Active Viewport Chunks Flagged as DIRTY
            │
            ▼
StagedChunkGenerator Re-evaluates [TerrainStage -> PropStage]
            │
            ▼
Updated Voxel Palettes Sent to Client Mesh Renderer (Hot Reload Complete!)
```

### Key Commands:
* `/worldgen reload`: Recompiles all biome, material, and prop JSON definitions in memory.
* `/viewport --radius <chunks>`: Locks world generation into a bounded cylindrical volume around the designer, instantly recalculating chunks when assets change.
