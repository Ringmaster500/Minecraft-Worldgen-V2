# Main Mod Rework: World Creation, Addons & Runtime Engine

## 1. Executive Summary & Philosophy
While the Studio provides the visual node authoring workstation, the Main Mod is the runtime foundation. It powers chunk generation (`WorldGenV2ChunkGenerator`), biome resolution (`WorldGenV2BiomeSource`), world preset tags, and spatial structure layouts (`DimensionLayoutConfig`, `MacroFeatureGraph`).

However, from a modpack creator's and player's perspective, the current world setup flow is **opaque**:
* World presets are registered via static JSON tags (`minecraft:worldgen/world_preset/normal.json`), but clicking **Customize** in Minecraft's "Create New World" screen is inactive or delegates to vanilla.
* There is no in-game UI to inspect, toggle, reorder, or tune installed Worldgen V2 Addons/presets.
* Structure generation relies on standard procedural density/landmarks without guaranteed spawn anchors or strict world-wide quota caps (finite budgets).

This rework outlines the implementation of the **World Setup & Preset Orchestrator Screen**, the **Finite & Budgeted Structure Engine**, and the **Seamless Addon & Export Pipeline**.

---

## 2. Current Codebase vs. Target Workflow Gap Analysis

| Feature Area | Current Implementation (`com.worldgenv2.minecraft`, `worldstructure`) | Target Rework Workflow |
| :--- | :--- | :--- |
| **World Creation UI** | Only static JSON presets (`volumetric.json`, `spawn_island_landmarks.json`, etc.) in `data/.../world_preset/`. No custom screen. | Custom **"Worldgen V2: Preset & Addon Orchestrator"** screen triggered when selecting Worldgen V2 world type and clicking **[ Customize ]**. |
| **Addon & Preset Management** | Hardcoded or static datapack files. No runtime toggle or stacking order in the menu. | **Interactive Addon Shelf**: Automatically discovers presets and addons from `mods/`, `config/worldgen_v2/`, and `datapacks/` with checkboxes and drag-to-reorder stacking priority. |
| **Pre-World Seed Preview** | None; players must generate a world and fly around to see what it looks like. | **Live Seed Radar**: Real-time 2D continent, biome, and POI beacon map rendered directly inside the customize screen before world creation. |
| **Macro Slider Tuning** | None in GUI; requires editing density JSONs or AST node parameters. | **Preset Tuning Sliders**: Addons expose macro sliders for players/pack makers (Biome scale, mountain height multiplier, city density budget, sea level offset). |
| **Spawn Placement** | Vanilla spawn search algorithm on `WorldGenV2ChunkGenerator`. | **Guaranteed Spawn Anchor**: Fixed origin structure placement (e.g., `crashed_colony_ship.nbt` at `0, 0`), crater excavation, terrain smoothing, and player spawn lock. |
| **Structure Budgets** | `MacroFeatureGraph` and `RingLandmarkRule` support radii and angles, but no global finite instance quotas. | **Finite & Budgeted POIs**: Placement rules supporting strict world caps (e.g. "exactly 4 to 6 ruined cities between radius 1200 and 5000, then halt globally"). |
| **Studio Bridge** | Studio can only be opened once inside a loaded singleplayer level via `G` or `/studio`. | **Direct Studio Bridge**: `[⚙ Open Node Studio]` button directly on the World Creation customize screen to fine-tune rules prior to generating the world. |
| **Distribution / Export** | Manual copying of config and JSON files. | **One-Click Export Wizard**: Exports validated, clean datapacks directly to `kubejs/data/` or standalone zip with pre-flight linting diagnostics. |

---

## 3. The "Worldgen V2: Customize" Orchestrator Screen

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  WORLDGEN V2: WORLD SETUP & PRESET CONFIGURATION                  [? Help] [X Close]   │
├───────────────────────────────┬────────────────────────────────────────────────────────┤
│ INSTALLED PRESETS & ADDONS    │               LIVE SEED RADAR & PREVIEW                │
│                               │                                                        │
│ ┌───────────────────────────┐ │ Seed: [ 4892019482019482 ] [🎲 Randomize] [Zoom: 1:8] │
│ │ ★ Odyssey: Reborn (Default)│ │                                                        │
│ │   [Core Worldgen Rules]   │ │         ~ ~ ~ ~ ~ ~ ~ ░░░░░░░░ ~ ~ ~ ~ ~               │
│ └───────────────────────────┘ │       ~ ~ ~ ~ ▒▒▒▒▒▒▒ ░░░░░░░░░░░ ~ ~ ~ ~              │
│ ┌───────────────────────────┐ │     ~ ~ ~ ▒▒▒▒▒ [★ SPAWN: SHIP] ░░░░ ~ ~              │
│ │ [x] Biomes Expansion Pack │ │     ~ ~ ▒▒▒▒▒▒▒▒▒▒▒▒ ░░░░░░░░░░░░░░ ~ ~               │
│ │   "Adds 48 new biomes"    │ │       ~ ~ ~ ~ ▓▓▓▓▓ [🏙️ CITY #1] ~ ~ ~                 │
│ └───────────────────────────┘ │                                                        │
│ ┌───────────────────────────┐ │ Hover: [X: 64, Z: 128] Biome: "Redwood Taiga"        │
│ │ [x] Crashsite & Cities    │ ├────────────────────────────────────────────────────────┤
│ │   "Spawn ship + 5 cities" │ │ ACTIVE PRESET TUNING SLIDERS                           │
│ └───────────────────────────┘ │                                                        │
│ ┌───────────────────────────┐ │ Biome Scale:     [━━━●━━━━━━━] 1.5x (Large)            │
│ │ [ ] Nether & End Overhaul │ │ Mountain Scale:  [━━━━━━●━━━━] Extreme (Y: 280)        │
│ │   (Disabled)              │ │ City Density:    [━●━━━━━━━━━] Capped (4–6 Total)      │
│ └───────────────────────────┘ │ Spawn Ship:      [✔ Guaranteed at (0, 0)]              │
├───────────────────────────────┴────────────────────────────────────────────────────────┤
│ [📁 Open Presets Folder]   [⚙ Open Node Studio]      [Import Code]   [Done]  [Cancel]   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Screen Behaviors:
1. **Installed Addons & Presets**:
   - Discovers `.json` and `.zip` presets located in `config/worldgen_v2/presets/` and registered through mod addons (e.g. `addons/hytale-compat`).
   - Checkboxes toggle addons on/off.
   - Dragging an addon changes its priority in the compilation stack (top-most addons override biomes, noise splines, and POIs of lower addons).
2. **Interactive Live Seed Radar**:
   - Renders a multi-resolution 2D continent map driven by the active preset's `FastNoiseLite` and `BiCarta` climate sampler.
   - Calculates in milliseconds on a background thread—moving the seed slider or clicking `[🎲 Randomize]` redraws the map without freezing the UI.
   - Overlays **POI Beacons**:
     - Gold Star: Guaranteed spawn crashsite at `(0, 0)`.
     - City Icons: Distance-zoned cities plotted across the landscape.
3. **Macro Tuning Sliders**:
   - Presets define configurable sliders in their JSON manifest (`min`, `max`, `default`, `step`, `target_parameter`).
   - Dragging the sliders adjusts the live preview in real time (e.g., expanding biome scale immediately widens continents on the radar).
4. **Bridge to Node Studio**:
   - Clicking `[⚙ Open Node Studio]` opens `WorldGenStudioScreen` preloaded with the current preset, allowing immediate node editing.

---

## 4. Runtime Generation Architecture Upgrades

### A. Guaranteed Spawn Anchor Engine
Currently, `WorldGenV2ChunkGenerator` relies on vanilla height queries and standard spawn logic. For narrative modpacks, creators need guaranteed origin features.

* **Anchor Placement Rule (`SpawnAnchorRule`)**:
  * Targets absolute coordinate `(0, 0)` or first valid surface column within `[0, searchRadius]`.
  * **Terrain Excavation & Crater Sculpting**:
    * Injects an inverted paraboloid or cubic crater mask into the terrain density function around the anchor coordinate.
    * Warps crater edges with noise to create realistic scorch/impact gouges.
  * **Surface Palette Blending**:
    * Replaces topsoil around the impact perimeter with transition blocks (e.g., scorched dirt, basalt, magma, cracked stone).
  * **Player Spawn Lock**:
    * Hooks into `WorldData` / `ServerLevel#setDefaultSpawnPos` to place the initial player spawn point directly inside the designated coordinates (e.g. Cryo Pod interior).

### B. Finite & Budgeted POI Engine (`StructureBudgetManager`)
Currently, `MacroFeatureGraph` defines topological landmarks, but lacks strict global instance limits across endless chunk generation.

* **World Quota Engine**:
  * Introduces `BudgetedFeatureRule`:
    ```json
    {
      "id": "ruined_cities",
      "structure": "modpack:ruined_city",
      "min_radius": 1200,
      "max_radius": 5000,
      "min_separation": 800,
      "min_quota": 4,
      "max_quota": 6,
      "allowed_biomes": ["modpack:wasteland", "modpack:cracked_basin"],
      "loot_override": "modpack:chests/tier2_military"
    }
    ```
  * During world initialization, the engine deterministically rolls candidate chunk coordinates for the budget (using world seed + feature salt).
  * Chunks outside the candidate set immediately skip structure checks, preventing infinite procedural spam and drastically reducing chunk generation lag.

### C. Dimension Parity & Multi-Dimension Pipelines
* `DimensionLayoutConfig` already supports radial rings, void moats, and archipelago zones.
* Extend the layout system to support **Dimension Pipeline Inheritance**:
  * Dimensions can inherit base rules from Overworld while swapping out noise generators and block palettes (e.g., Nether ceiling/floor inversion, End floating void shells).

---

## 5. File-by-File Implementation Plan

### 1. `com.worldgenv2.minecraft.gui.WorldGenCustomizeScreen` (New Class)
* **Purpose**:
  * Implements the custom world creation GUI.
  * Extends `Screen`, hooks into `ScreenEvent.Init.Post` when `CreateWorldScreen` is active.
  * Renders the Addons shelf, Live Seed Radar, Macro Sliders, and action buttons.

### 2. `com.worldgenv2.minecraft.gui.LiveSeedRadarWidget` (New Class)
* **Purpose**:
  * High-performance 2D preview canvas.
  * Samples `BiCarta` and `FastNoiseLite` directly to render continent shapes, biome colors, and POI markers.
  * Supports zoom (`1:2` to `1:64`), panning, and seed scrubbing.

### 3. `com.worldgenv2.minecraft.WorldGenV2Mod`
* **Changes**:
  * Register client screen events to inject the **[ Customize ]** button into `CreateWorldScreen` for `worldgenv2:volumetric` presets.
  * Add configuration directories for `config/worldgen_v2/presets/` and `config/worldgen_v2/addons/`.
  * Register new command `/worldgenv2 export` for headless modpack bundling.

### 4. `com.worldgenv2.worldstructure.SpawnAnchorManager` (New Class)
* **Purpose**:
  * Manages fixed-coordinate spawn anchor placement, crater density carving, and player spawn point synchronization.
  * Serializes placed anchor bounds to level storage (`SavedData`).

### 5. `com.worldgenv2.worldstructure.StructureBudgetManager` (New Class)
* **Purpose**:
  * Pre-computes finite landmark locations within specified radial bands (`min_radius` to `max_radius`).
  * Enforces strict min/max quotas across the world, halting generation once the cap is satisfied.
  * Intersects candidate locations with `TileBiome` queries to ensure valid biome placement.

### 6. `com.worldgenv2.minecraft.WorldGenV2ChunkGenerator`
* **Changes**:
  * Integrate `SpawnAnchorManager` into `applyCarvers` and `buildSurface`.
  * Consult `StructureBudgetManager` during structure placement stages to guarantee finite features and eliminate runaway structure generation.

### 7. `com.worldgenv2.compat.ModpackExportService` (New Class)
* **Purpose**:
  * Compiles active world settings, node graphs, and addon presets into a standardized modpack distribution package (`kubejs/data/` or standalone zip).
  * Runs automated pre-flight diagnostics (validating NBT files, block states, and loot table identifiers).

---

## 6. Modpack Setup Flow (The "Golden Path")

1. **Install Mod & Addons**: Modpack creator places `worldgenv2.jar` and their custom biome/structure addons in `mods/`.
2. **Configure Defaults**: Sets `default_preset = "modpack:crashland_odyssey"` in `config/worldgen_v2/main.json`.
3. **Player First World Creation**:
   * Player clicks "Singleplayer" -> "Create New World".
   * World Type automatically defaults to **Worldgen V2: Odyssey**.
   * Player can click **[ Customize ]** to view the **Live Seed Radar**, see the crashsite beacon at `(0, 0)`, tune biome scale if desired, or click "Done".
4. **Game Launch**:
   * World generates instantly with guaranteed crashsite ship, 4–6 ruined cities in the 1200–5000 block ring, and tailored dimension terrain across Nether and End.
