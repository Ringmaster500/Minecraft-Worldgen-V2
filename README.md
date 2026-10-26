# Minecraft Worldgen V2 for Minecraft 26.1

> [!IMPORTANT]
> **Architectural Separation Notice**: This project is the standalone **Minecraft Worldgen V2** (`com.worldgenv2` / `worldgenv2`) procedural world generation engine for deobfuscated Minecraft 26.1 (NeoForge).
> 
> **Hytale Compatibility is DEFERRED and SET ASIDE**: Everything related to Hytale-specific translation (`hytale:*` block mapping dictionaries, Hytale Orbis Zone 1–4 biome presets, and `.prefab.json` Hytale structure translators) has been extracted out of the main mod and placed into [`addons/hytale-compat/`](addons/hytale-compat/README.md). **We are NOT working on the Hytale compatibility addon right now.** The active development focus is 100% on building a clean, native, standalone volumetric Minecraft world generator.

---

> 📋 **[View the Master Implementation Roadmap & Progress Tracker (`docs/ROADMAP_AND_PROGRESS.md`)](docs/ROADMAP_AND_PROGRESS.md)** for live phase-by-phase status, completed classes, and upcoming milestones.

---

## 📚 Technical Research Suite (`docs/research/`)

The core engine is inspired by advanced volumetric world generation principles (cellular Voronoi biomes, continuous 3D density fields, single-pass contextual props, and adaptive foundations). The research specifications in `docs/research/` are categorized into active core specifications and deferred addon references:

### Active Core Specifications (Minecraft Worldgen V2 Engine)
| Document | Focus & Key Insights |
| :--- | :--- |
| [**01. Philosophy & Paradigm Shift**](docs/research/01_PHILOSOPHY_AND_PARADIGM_SHIFT.md) | Intentional Design, why terrain is derived *from* biome structure rather than biomes painted onto terrain, live authoring workflow (`/worldgenv2 viewport`, `/worldgenv2 reload`). |
| [**02. Staged Chunk Generation Pipeline**](docs/research/02_STAGED_CHUNK_GENERATION_PIPELINE.md) | Deep breakdown of `StagedChunkGenerator`, `BiomeStage`, `BiomeDistanceStage`, `TerrainStage`, `PropStage`, and zero-allocation halo buffers. |
| [**03. Macro World & Biome Architecture**](docs/research/03_MACRO_WORLD_AND_BIOME_ARCHITECTURE.md) | `WorldStructure`, `BiCarta`, Voronoi cellular point generators, `TileBiomes` vs `CustomBiomes`, weight, size modifiers, and boundary distance blending. |
| [**04. Density Functions & 3D Volumetric Math**](docs/research/04_DENSITY_FUNCTIONS_AND_3D_VOLUMETRIC_MATH.md) | Scalar density fields $D(x, y, z) \in [-1, 1]$, complete catalog of math nodes (`AbsDensity`, `ClampDensity`, `SumDensity`, `ProductDensity`, `NormalizerDensity`, etc.), FastNoiseLite integration, `YSampledDensity` vertical subsampling, 3D overhangs, arches, and organic caves. |
| [**05. Material Providers & Surface Rules**](docs/research/05_MATERIAL_PROVIDERS_AND_SURFACE_RULES.md) | Material providers (`ConstantMaterialProvider`, `WeightedMaterialProvider`, `SpaceAndDepthMaterialProvider`, `QueueMaterialProvider`), structural pattern matchers (`SurfacePattern`, `WallPattern`, `CeilingPattern`, `SlopePattern`), vector normal calculation ($\nabla D$). |
| [**06. Prop System & Prefab Distribution**](docs/research/06_PROP_SYSTEM_AND_PREFAB_DISTRIBUTION.md) | Single-pass `generate(Prop.Context)` pipeline, prop hierarchy (`BlockProp`, `CuboidProp`, `DensitySelectorProp`, `MaskProp`, `OrienterProp`, `StaticRotatorProp`, `LocatorProp`, `PondFillerProp`, `PrefabProp`), sockets, and adaptive foundation plinths. |
| [**07. Asset Schemas & Node Graph Specification**](docs/research/07_ASSET_SCHEMA_AND_NODE_GRAPH_SPECIFICATION.md) | Native JSON schema for biomes and node graphs, DAG execution model, constant folding, and runtime reloading. |
| [**08. Minecraft 26.1 Deobfuscated Integration**](docs/research/08_MINECRAFT_26_1_DEOBFUSCATED_ADAPTATION.md) | Minecraft 26.1 official deobfuscation benefits, Java 25 runtime, bridging `WorldGenV2BiomeSource` to `BiomeSource` and `WorldGenV2ChunkGenerator` to `ChunkGenerator`. |
| [**09. Technical Implementation Specification**](docs/research/09_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md) | Concrete project blueprint, modular directory layout, class contracts, and API definitions. |
| [**10. Contextual Awareness, Spawn & Relational Landmarks**](docs/research/10_CONTEXTUAL_AWARENESS_SPAWN_AND_RELATIONAL_LANDMARKS.md) | Macro Topological Feature Graphs, conditional island spawn predicates, relational landmark hierarchies, and procedural spline-based road networks. |
| [**13. Mod Compatibility & Biome Auto-Synthesis**](docs/research/13_MOD_COMPATIBILITY_AND_VANILLA_BIOME_INGESTION.md) | Universal mod ecosystem integration: `ModdedBlockResolver` with fallback chains, `BiomeAutoSynthesizer` for auto-ingesting vanilla and modded biomes (*Terralith*, *Biomes O' Plenty*), and `FeatureCompatibilityBridge` for modded ores and trees. |
| [**16. World Fundamentals: Caves, Rivers, Oceans & 3D Biomes**](docs/research/16_WORLD_FUNDAMENTALS_CAVES_RIVERS_OCEANS_3D_BIOMES.md) | How Hytale WorldGen V2 handles caves (density CSG), rivers (corridor carving), oceans (continent tier) and 3D biomes, plus our implementation: climate-space biome selection, CaveCarver, RiverCarver, UndergroundBiome volumes, and the 39-biome default roster. |
| [**15. Modpack Authoring & Studio Workflow**](docs/research/15_MODPACK_AUTHORING_AND_STUDIO_WORKFLOW.md) | FTB Quests-style modpack integration: `config/worldgenv2/` auto-mounted synthetic datapack, separate `worldgenv2-studio` companion mod, in-game inspector HUD, WYSIWYG editor screen, and sub-second `/viewport` hot-reloading. |

### Deferred Specifications (Set Aside in `addons/hytale-compat/` — Not in Active Development)
| Document | Deferred Content |
| :--- | :--- |
| [**11. Prefab & Blueprint Translator Architecture**](docs/research/11_HYTALE_PREFAB_TRANSLATOR_AND_BLUEPRINT_ADAPTER.md) | External `hytale:*` to `minecraft:*` block name translation and legacy Hytale blueprint importing. (Set aside in `addons/hytale-compat/`). |
| [**12. Official Biome Settings Translator & Presets**](docs/research/12_HYTALE_BIOME_SETTINGS_TRANSLATOR_AND_OFFICIAL_PRESETS.md) | Ingestion of Hytale Orbis Zone 1–4 asset files. (Set aside in `addons/hytale-compat/`). |
| [**14. Hytale Modding Ecosystem & DataPack Parity**](docs/research/14_HYTALE_MODDING_ECOSYSTEM_AND_DATA_PACK_PARITY.md) | Hytale `manifest.json` parity. (Set aside in `addons/hytale-compat/`). |

---

## 🛠 Standalone Project Architecture

The active repository is cleanly partitioned into:

1. **`com.worldgenv2` (Core Mathematical & Generation Engine)**:
   - Pure Java 25 implementation with zero Minecraft runtime coupling:
     - `com.worldgenv2.density`: Volumetric 3D density scalar fields & FastNoiseLite.
     - `com.worldgenv2.worldstructure`: Cellular BiCarta Voronoi evaluator & CustomBiome overlays.
     - `com.worldgenv2.material`: Stratified geological providers and structural pattern matchers.
     - `com.worldgenv2.props`: Single-pass contextual props with 3D normal alignment.
     - `com.worldgenv2.prefab`: Native structure prefab parser, adaptive foundation plinths, and socket expansion.
     - `com.worldgenv2.engine.chunkgenerator`: Pipelined chunk generator with thread-local zero-allocation halo scratchpads.
2. **`com.worldgenv2.minecraft` (Minecraft 26.1 NeoForge Runtime Adapter — `worldgenv2.jar`)**:
   - Bridges the core engine into Minecraft's native deobfuscated APIs (`WorldGenV2ChunkGenerator`, `WorldGenV2BiomeSource`, `MinecraftVoxelSpace`, `WorldGenV2Mod`).
   - Automatically mounts the instance `config/worldgenv2/` folder as a synthetic global datapack so modpack creators never have to compile JARs or zip datapacks.
3. **`worldgenv2-studio` (Optional In-Game Creator Companion Mod — Client-Only)**:
   - Dedicated WYSIWYG editor suite for world and modpack authors (omitted from player survival releases).
   - In-game crosshair inspector HUD (`/worldgenv2 inspect`), visual terrain & strata editor GUI with modded block autocompletion, and sub-second `/worldgenv2 viewport` chunk re-rendering.
4. **`addons/hytale-compat` (Set Aside / Deferred Addon)**:
   - Isolated directory housing all Hytale-specific translators, block maps, and Orbis presets. **Dormant and excluded from the main build.**
