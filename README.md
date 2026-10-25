# Hytale WorldGen V2 for Minecraft 26.1: Research & Technical Blueprint

This repository is an engineering initiative to bring **Hytale WorldGen V2** into **Minecraft 26.1 (Deobfuscated / Java 25)** through a modern, high-performance modding foundation.

---

## 📚 Comprehensive Research Suite (`docs/research/`)

All findings, mathematical formulations, internal package architectures, and design trade-offs from Hypixel Studios' disclosures have been compiled into organized, deeply technical reference specifications:

| Document | Focus & Key Insights |
| :--- | :--- |
| [**01. Philosophy & Paradigm Shift**](docs/research/01_PHILOSOPHY_AND_PARADIGM_SHIFT.md) | V1 vs V2, Intentional Design, why terrain is derived *from* biome structure rather than biomes painted onto terrain, live authoring workflow (`/viewport`, `/worldgen reload`). |
| [**02. Staged Chunk Generation Pipeline**](docs/research/02_STAGED_CHUNK_GENERATION_PIPELINE.md) | Deep breakdown of `StagedChunkGenerator` (formerly `NStagedChunkGenerator`), `BiomeStage`, `BiomeDistanceStage`, `TerrainStage`, `PropStage`, `EnvironmentStage`, and memory spaces (`materialReadSpace` vs `materialWriteSpace`). |
| [**03. Macro World & Biome Architecture**](docs/research/03_MACRO_WORLD_AND_BIOME_ARCHITECTURE.md) | `WorldStructure`, `BiCarta<Integer>`, Voronoi cellular point generators, `TileBiomes` vs `CustomBiomes`, weight, size modifiers, parent biome masks, noise thresholds, and `DefaultTransitionDistance` blending. |
| [**04. Density Functions & 3D Volumetric Math**](docs/research/04_DENSITY_FUNCTIONS_AND_3D_VOLUMETRIC_MATH.md) | Scalar density fields $D(x, y, z) \in [-1, 1]$, catalog of all mathematical nodes (`AbsDensity`, `ClampDensity`, `SumDensity`, `ProductDensity`, `NormalizerDensity`), FastNoiseLite integration, `YSampledDensity` vertical subsampling, 3D overhangs, arches, and organic caves. |
| [**05. Material Providers & Surface Rules**](docs/research/05_MATERIAL_PROVIDERS_AND_SURFACE_RULES.md) | Material providers (`SolidMaterial`, `FluidMaterial`, `ConstantMaterialProvider`, `WeightedMaterialProvider`, `SpaceAndDepth`), structural pattern matchers (`SurfacePattern`, `WallPattern`, `CeilingPattern`, `SlopePattern`), vector normal calculation ($\nabla D$), and comparison with Minecraft's `SurfaceRules`. |
| [**06. Prop System & Prefab Distribution**](docs/research/06_PROP_SYSTEM_AND_PREFAB_DISTRIBUTION.md) | Single-pass `generate(Prop.Context)` pipeline, prop hierarchy (`CuboidProp`, `DensitySelectorProp`, `MaskProp`, `OrienterProp`, `PrefabProp`, `LocatorProp`), socket & attachment mechanics, procedural plinths, and density-driven clustering. |
| [**07. Asset Schemas & Node Graph Specification**](docs/research/07_ASSET_SCHEMA_AND_NODE_GRAPH_SPECIFICATION.md) | Complete JSON schema for V2 biomes, DAG execution model, constant folding, and the real-time hot-reloading architecture. |
| [**08. Minecraft 26.1 Deobfuscated Integration**](docs/research/08_MINECRAFT_26_1_DEOBFUSCATED_ADAPTATION.md) | Minecraft 26.1 official deobfuscation benefits, Java 25 runtime (virtual threads, Vector API, memory segments), bridging `HytaleBiomeSource` to `BiomeSource` and `HytaleChunkGenerator` to `ChunkGenerator`. |
| [**09. Technical Implementation Specification**](docs/research/09_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md) | Concrete project blueprint, modular directory layout, class contracts, and API definitions. |
| [**10. Contextual Awareness, Spawn & Relational Landmarks**](docs/research/10_CONTEXTUAL_AWARENESS_SPAWN_AND_RELATIONAL_LANDMARKS.md) | Macro Topological Feature Graphs, conditional island spawn predicates, relational landmark hierarchies (Landmark A $\implies$ Landmark B), and procedural spline-based road networks. |
| [**11. Prefab & Blueprint Translator Architecture**](docs/research/11_HYTALE_PREFAB_TRANSLATOR_AND_BLUEPRINT_ADAPTER.md) | Hytale `.prefab.json` parser, `BlockMappingRegistry` (hytale $\to$ minecraft), socket connection preservation, and adaptive foundation plinths for uneven terrain. |
| [**12. Official Biome Settings Translator & Presets**](docs/research/12_HYTALE_BIOME_SETTINGS_TRANSLATOR_AND_OFFICIAL_PRESETS.md) | Native ingestion of published Hytale V2 biome assets (`Server/HytaleGenerator/Biomes/`), parsing of `GradientWarp`, `DistanceToBiomeEdge`, `YSampled`, and official presets (*Verdant Forest*, *Whispering Grove*, *Howling Sands*, *Boreal Tundra*). |
| [**13. Mod Compatibility & Biome Auto-Synthesis**](docs/research/13_MOD_COMPATIBILITY_AND_VANILLA_BIOME_INGESTION.md) | Universal mod ecosystem integration: `ModdedBlockResolver` with fallback chains, `BiomeAutoSynthesizer` for auto-ingesting vanilla and modded biomes (*Terralith*, *Biomes O' Plenty*), and `FeatureCompatibilityBridge` for modded ores and trees. |

---

## 🛠 Project Architecture & Setup

The project is structured with strict separation between the **pure mathematical engine** and the **Minecraft 26.1 loader integration**:

1. **`com.hypixel.hytale.builtin.hytalegenerator`**: Pure Java 25 implementation of Hytale's WorldGen V2 engine (Density Functions, BiCarta, Staged Pipeline, Material Providers, Props). Zero Minecraft dependencies, ultra-fast and independently testable.
2. **`com.hypixel.hytale.builtin.hytalegenerator.minecraft`**: Adapter layer bridging Hytale's V2 engine with Minecraft 26.1's native deobfuscated APIs (`ChunkGenerator`, `BiomeSource`, `ChunkAccess`, `LevelChunkSection`).
