# Studio Mod Rework: Interaction Flow & Architecture

## 1. Executive Summary & Philosophy
The Studio Companion in **Worldgen V2** is already equipped with an extraordinary technical foundation: a ~295-node AST schema (`StudioNodeCatalog`), matrix-transformed zoomable canvas (`NodeCanvasTab`), live integrated-server compilation (`StudioLiveCompiler`), and in-world 3D chunk gizmos (`StudioChunkHighlightRenderer`).

However, its current user interaction model is **tab-fragmented**: users must navigate disjointed top tabs (`World Pack`, `Dimensions`, `Biomes`, `Test Chunks`, `Wiki`) and drill through multiple nested sub-screens (`DIMENSION_LIST` -> `DIMENSION_MENU` -> `DIMENSION_ZONES` -> `BIOME_LIST` -> `NODE_EDITOR`). This creates friction, breaks creator flow, and obscures the immediate cause-and-effect relationship between node graphs and the generated world.

This rework outlines the transformation of the Studio into a **unified, 4-pane visual worldgen workstation** (inspired by modern digital content creation tools like Blender and Unreal Engine), centered around an intuitive pipeline hierarchy, live real-time visual feedback, and frictionless node ergonomics.

---

## 2. Current Codebase vs. Target Workflow Gap Analysis

| Feature Area | Current Implementation (`com.worldgenv2.studio`) | Target Rework Workflow |
| :--- | :--- | :--- |
| **Top-Level Navigation** | 5 top tabs in `WorldGenStudioScreen`: `World Pack`, `Dimensions`, `Biomes`, `Test Chunks`, `Wiki`. | **Unified 4-Pane Studio Workspace** with persistent pipeline tree on the left, central canvas/viewport, property inspector on the right, and asset shelf on the bottom. |
| **Biome & Dimension Workflow** | `WorldExplorerTab` switches between disjointed enum views (`DIMENSION_LIST`, `DIMENSION_SETTINGS`, `BIOME_LIST`). Clicking a biome switches to `NodeCanvasTab`. | Selecting any stage, dimension, or biome in the left **Pipeline Tree** immediately scopes the center canvas and right inspector without page-flipping. |
| **Node Canvas Ergonomics** | Flat node cards in `NodeCanvasTab` with text parameters, numeric step buttons, and Bezier wires. No live thumbnails. | **Smart Node Cards**: Embedded 64x64 real-time noise/density heatmaps on node headers; visual cubic/bezier spline curves; collapsible sub-graph macros. |
| **Visual Feedback & Testing** | In-world 3D view is locked to tab 3 (`ChunkViewportTab`) or requires looking at the game world behind a semi-transparent screen. | **Dockable / Split Viewport**: Toggleable side-by-side or picture-in-picture 2D topographic slice and 3D chunk previewer with real-time seed scrubbing. |
| **Inspection & Debugging** | Only displays chunk coordinates and primary biome name (`queryChunkPrimaryBiome`). | **Interactive Probe System**: Hovering a wire previews its output mask across the map; clicking any preview voxel displays the full node evaluation stack. |
| **Reusability** | Duplicate node graphs must be created per-biome; no sub-graph macro system. | **"Collapse to Sub-Graph"**: Package node groups into reusable macros with exposed inputs/outputs (e.g., `Macro_Alpine_Cliff_Face`). |
| **Access Points** | In-game keybinding (`G`), `/worldgenv2 studio`, or `/studio`. Requires active player and loaded world (`mc.player != null`). | Openable directly from the **Main Menu**, the **World Creation screen**, or in-game via `F10` / `G`. |

---

## 3. The 4-Pane Studio Workspace Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ [📁 Project: Odyssey_Modpack]  [Dimension: Overworld ▼]  [Seed: 4892019] [🎲]  [💾 Save] [📦 Export]  │
├──────────────────────┬─────────────────────────────────────────────────┬───────────────────────────────┤
│ PIPELINE HIERARCHY   │ CENTRAL WORKSPACE (NODE CANVAS / VIEWPORT)      │ CONTEXT INSPECTOR             │
│                      │                                                 │                               │
│ ▼ Overworld          │  [Mode: Node Canvas | Topo Slice | Split]       │ Selected: [SimplexNoise_01]   │
│   ├── Base Terrain   │                                                 │ • Frequency: 0.0050           │
│   ├── Biome Canvas   │   ┌──────────────┐     ┌──────────────┐         │ • Octaves: 4                  │
│   │   ├── Redwoods   │   │ SimplexNoise │ ──> │ SplineCurve  │ ──┐     │ • Lacunarity: 2.0             │
│   │   └── Lavender   │   │ [64x64 Heat] │     │ [Spline Map] │   │     ├───────────────────────────────┤
│   ├── Spawn Anchor   │   └──────────────┘     └──────────────┘   ▼     │ SPLINE / CURVE EDITOR         │
│   │   └── Colony Ship│                               ┌───────────────┐ │     /\                        │
│   ├── Finite POIs    │   ┌──────────────┐            │ BiomeCluster  │ │    /  \                       │
│   │   └── Cities 4-6 │   │ ClimateInput │ ─────────> │ (Multi-Noise) │ │ __/    \______                │
│   └── Strata & Soil  │   └──────────────┘            └───────────────┘ │ [Add Point] [Smooth] [Clamp]  │
├──────────────────────┴─────────────────────────────────────────────────┴───────────────────────────────┤
│ ASSET SHELF: [Prefabs / NBTs] [Jigsaws] [Loot Tables] [Block Palettes] [Sub-Graph Library]             │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Pane 1: The Pipeline Tree (Left Panel)
* Replaces the multi-page drill-downs in `WorldExplorerTab.java`.
* Displays the dimensional execution pipeline in deterministic order:
  1. **Base Height & Continentalness** (Noise generators, height curves)
  2. **Biome Climate Canvas** (Multi-noise Voronoi cluster, temperature, humidity)
  3. **Fixed Anchors** (Spawn crashsite, world origin structures)
  4. **Zoned & Budgeted POIs** (Finite cities, dungeons, radial band structures)
  5. **Surface Strata Rules** (Rock banding, slope cliff gates, soil depth)
  6. **Foliage & Micro-Decorators** (Tree jigsaw placement, undergrowth scatter)
* Clicking any item switches the central workspace context without resetting canvas zoom or navigation.

### Pane 2: The Infinite Node Canvas & Integrated Viewport (Center Panel)
* **Dual-View Modes**:
  * **Graph Mode**: Enhanced `NodeCanvasTab` with infinite pan/zoom.
  * **Viewport Mode**: 2D topographic contour slice or 3D voxel preview.
  * **Split Mode**: Canvas on the left, live preview on the right.
* **Embedded Node Previews**:
  * Noise and mask nodes render a 64x64 grayscale or heatmap texture directly on the card header.
  * Allows creators to see the impact of frequency or octave changes instantly without looking away.
* **Sub-Graphs & Reusable Macros**:
  * Ability to box-select nodes, right-click, and select **"Group into Sub-Graph"**.
  * Saves the sub-graph into a project library drawer (`Macro_Alpine_Cliff`, `Macro_Spruce_Canopy`).

### Pane 3: Context Inspector & Interactive Spline Editor (Right Panel)
* Shows precise properties for the currently selected node card or pipeline rule.
* **Interactive Spline Curve Widget**:
  * Replaces manual number typing in `ManualCurve` / `DistanceSCurve`.
  * Visual Bezier curve editor with draggable control points, tangent handles, and slope clamping.

### Pane 4: Asset Shelf & Sub-Graph Drawer (Bottom Panel)
* A tabbed drawer that slides up from the bottom:
  * **NBT / Prefab Shelf**: Previews registered structure NBTs (`crashed_colony_ship.nbt`, `city_center.nbt`) with 3D isometric thumbnails.
  * **Block Palettes**: Quick access to block states and gradient ramps.
  * **Loot Tables**: Drag-and-drop loot table references for structure chests.
  * **Macro Library**: Reusable sub-graph nodes that can be dragged into any open graph.

---

## 4. File-by-File Implementation Plan

### 1. `com.worldgenv2.studio.gui.WorldGenStudioScreen`
* **Changes**:
  * Retire the rigid 5-tab top bar (`TAB_TITLES = {"World Pack", "Dimensions", ...}`).
  * Implement the 4-pane dockable layout (Pipeline Tree, Central Viewport/Canvas, Inspector, Asset Shelf).
  * Add support for opening the screen without an active integrated server/player (using client-side preview evaluation).
  * Add top header controls: Active Project selector, Dimension dropdown, Seed scrubber, Save (`Ctrl+S`), and Export wizard.

### 2. `com.worldgenv2.studio.tabs.NodeCanvasTab`
* **Changes**:
  * **Node Card Rendering**: Add live 64x64 texture thumbnail rendering for density/noise/mask nodes using a cached lightweight software rasterizer.
  * **Sub-Graph Architecture**:
    * Add `SubGraphNodeCard` representing collapsed macros.
    * Implement navigation breadcrumb bar (`Root > Biomes > Redwood_Taiga > Surface_Strata`).
  * **Wire Probing**: Add click-to-probe on Bezier wires; send probed value buffer to the preview viewport.
  * **Auto-Conversion Hints**: When dragging a float wire to a vector socket or curve socket, pop up contextual adapter nodes (e.g., `CurveMapper`, `ScalarMultiplier`).

### 3. `com.worldgenv2.studio.tabs.WorldExplorerTab`
* **Changes**:
  * Refactor into the **Pipeline Tree Widget** (`StudioPipelineTreeWidget`).
  * Replace the static list-drill-down model with an expandable hierarchical tree structure.
  * Allow dragging items in the tree to reorder feature evaluation priority.

### 4. `com.worldgenv2.studio.tabs.ChunkViewportTab`
* **Changes**:
  * Decouple the minimap renderer into a standalone reusable component: `StudioViewportWidget`.
  * Support multiple rendering modes:
    * *Topographic Heightmap* (contour color gradients).
    * *Biome Voronoi Map* (color-coded biome cells).
    * *Temperature / Humidity Heatmap*.
    * *POI Beacon Radar* (plots fixed spawn anchors and finite structure rings).
  * Add mouse-click voxel inspection that traces the node evaluation stack for the clicked block.

### 5. `com.worldgenv2.studio.widgets.SplineEditorWidget` (New Class)
* **Purpose**:
  * Provide an interactive 2D cubic/bezier curve editor for all `Curve` and `CurveMapperDensity` nodes.
  * Supports adding points, moving tangent handles, setting min/max boundaries, and evaluating live curve output.

### 6. `com.worldgenv2.studio.StudioState`
* **Changes**:
  * Add state tracking for:
    * Active sub-graph hierarchy (breadcrumbs).
    * Active preview seed and viewport camera offset.
    * Active probed wire / node socket.
    * Project metadata and export profile configuration.

### 7. `com.worldgenv2.studio.StudioLiveCompiler`
* **Changes**:
  * Support partial graph compilation (evaluating just the selected node or sub-branch for thumbnail generation).
  * Support offline headless compilation (running noise pipelines on a separate worker thread for the preview viewport without blocking the render thread).

---

## 5. Creator User Experience Walkthrough (The "Golden Path")

1. **Launch**: Modpack creator hits `F10` or clicks "Worldgen Studio" on the title screen.
2. **Dimension Selection**: Selects `Overworld` from the header bar. The Pipeline Tree lists the generation stages.
3. **Terrain Tuning**: Clicks `Base Terrain` in the tree. The center pane shows the height graph; clicking the height spline opens the curve editor in the right pane. Creator drags the plateau handle and sees the live 2D topographic slice update instantly.
4. **Spawn Anchor Setup**: Clicks `Spawn Anchor` in the tree, drags `crashed_colony_ship.nbt` from the Asset Shelf into the inspector, and checks `[x] Crater Excavation`. The preview map immediately pins a gold beacon at `(0, 0)` with crater contours.
5. **Finite Cities**: Clicks `Zoned POIs`, adds a rule for `Ruined Cities`, sets `Min Radius: 1200`, `Max Radius: 5000`, and `Quota Cap: 4-6`. The POI Radar displays simulated placement dots across 10 random seeds.
6. **Sub-Graph Creation**: In the `Redwood_Taiga` biome, creator groups 5 slope and strata nodes into `Macro_Alpine_Cliff`, exposing `BaseBlock` and `SlopeAngle`. It appears in the Asset Shelf and is dropped into three other mountain biomes.
7. **Instant Verification**: Creator drops a probe pin onto the tree density mask wire to see groves highlighted in green across the map, then clicks **"Drop Player Here"** to fly around the live world in creative mode.
8. **Save & Export**: Creator hits `Ctrl+S`, clicks **Export for Production**, and receives a validated, production-ready datapack bundle in `kubejs/data/`.
