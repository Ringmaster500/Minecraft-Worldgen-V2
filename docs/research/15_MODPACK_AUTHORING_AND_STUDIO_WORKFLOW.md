# WorldGen V2: Modpack Authoring Architecture & In-Game Studio Workflow

## 1. Executive Summary & Design Vision

In modern Minecraft modpacks, custom content authoring must be fast, visual, and painless. Popular frameworks like **FTB Quests** proved that when modpack creators have an in-game "Edit Mode" that directly saves human-readable files into `config/`, the entire ecosystem flourishes. Modpack authors do not have to write compiled Java, package complex `.zip` datapacks, or restart their game 200 times a day to tune a single mountain curve.

WorldGen V2 adopts this exact paradigm:
1. **Zero-Friction Modpack Configuration**: All custom worlds, biomes, and structure prefabs live in `config/worldgenv2/`, which is auto-mounted by the engine as a dynamic global datapack.
2. **In-Game Authoring (WYSIWYG)**: Designers build, inspect, and adjust biomes directly inside the Minecraft client using the **WorldGen V2 Studio** companion mod.
3. **Sub-Second Hot Reloading (`/worldgenv2 viewport`)**: Designers iterate within a localized chunk viewport that invalidates and regenerates in milliseconds when sliders or materials are adjusted.
4. **Architectural Separation**: The lightweight generation engine (`worldgenv2`) is separate from the creator tools (`worldgenv2-studio`). Normal players never download or run dormant editor code.

---

## 2. Directory Architecture & Modpack Integration

Modpack export tools (CurseForge, Modrinth, Prism, FTB App) automatically bundle everything inside the instance `config/` directory by default. 

### 2.1 File System Hierarchy
```
minecraft-instance/
├── config/
│   └── worldgenv2/
│       ├── worlds/
│       │   └── default_world.json         # Master world configuration (Voronoi cell scale, seed rules, default world type)
│       ├── biomes/
│       │   ├── autumn_forest.json         # Biome density graphs, transition distances, materials, props
│       │   ├── crystal_chasm.json
│       │   └── alpine_peaks.json
│       ├── prefabs/
│       │   ├── ancient_shrine.prefab.json # Custom structure prefabs with adaptive foundation plinths
│       │   └── watchtower_01.prefab.json
│       └── presets/                       # Built-in, read-only or template biomes available for duplication
```

### 2.2 The Auto-Mounting Synthetic Datapack Engine
- When Minecraft boots, `worldgenv2` inspects `config/worldgenv2/`.
- It dynamically injects an in-memory **Synthetic Datapack** (`PackType.SERVER_DATA`) into Minecraft's `PackRepository`.
- **Result for the Creator**: No need to manually create `data/modid/worldgen/...` folder mazes or zip files. Simply dropping or editing JSON files in `config/worldgenv2/` registers them seamlessly into Minecraft's registry.
- **Priority Cascade**:
  1. User/Pack overrides in `config/worldgenv2/` (Highest Priority).
  2. Mod datapacks (`data/worldgenv2/biomes/...`).
  3. Default fallback biomes compiled into the jar (`DefaultWorldFactory`).

---

## 3. Two-Tier Modular Distribution

To ensure peak performance, minimal memory footprints, and server security, the project is structured into two companion modules:

```
┌────────────────────────────────────────────────────────┐
│            worldgenv2 (Core Runtime Engine)            │
│  - Shipped with all modpacks (Client & Dedicated Server)│
│  - Pure chunk generator & biome source adapters        │
│  - JSON deserializer & config/worldgenv2 auto-mounter  │
│  - Zero GUI code, zero editor overhead, secure         │
└──────────────────────────┬─────────────────────────────┘
                           │ (API dependency)
┌──────────────────────────▼─────────────────────────────┐
│       worldgenv2-studio (Creator Companion Mod)        │
│  - Client-only, developer-environment mod              │
│  - In-game visual editor, inspector HUD, gizmos        │
│  - `/worldgenv2 viewport` instant re-render engine     │
│  - One-click "Save to Config / Datapack" exporter      │
│  - Excluded from public player downloads               │
└────────────────────────────────────────────────────────┘
```

### Why This Separation Matters:
- **No Player Overhead**: Players exploring a survival world do not load heavy GUI assets, graph canvas renderers, or dormant editing keybinds.
- **Server Security**: Public servers running `worldgenv2` are completely immune to any malicious editor packets or unauthorized client-side worldgen overrides.
- **Modpack Creator Simplicity**: A creator adds `worldgenv2-studio` to their local test client, tunes their biomes, clicks "Save", and when exporting the final modpack zip for release, they simply untick `worldgenv2-studio.jar`.

---

## 4. In-Game Creator Tooling & Iteration Loop

### 4.1 The Live Viewport (`/worldgenv2 viewport`)
Traditional worldgen debugging requires flying thousands of blocks or rebooting. WorldGen V2 solves this via localized viewport generation:
- The command `/worldgenv2 viewport [radius]` (default: $3 \times 3$ chunks) locks a testing volume centered on the creator.
- Edits made in the in-game editor trigger a non-destructive rebuild of only the active viewport chunks.
- Chunks rebuild in $\sim 50\text{ms}$ using `ThreadLocal<ColumnScratch>` scratchpads, giving instant visual feedback.

### 4.2 Crosshair & Inspector HUD
An in-game diagnostic HUD (toggled via hotkey or `/worldgenv2 inspect`) displays real-time mathematical telemetry:
- **Active Voronoi Cell**: Cell coordinate, assigned `TileBiome`, and cell seed.
- **Boundary Blend Telemetry**: Euclidean distance to adjacent cells, calculated neighbor blend factor $[0.0, 0.5]$, and active transition distance.
- **Voxel Density**: Exact continuous scalar density $D(x, y, z) \in [-1, 1]$ under the player's crosshair.
- **Surface Pattern Evaluation**: Displays which `MaterialProvider` and `Pattern` matched (e.g. `SurfacePattern (Floor, Slope 12°)` or `WallPattern (Cliff, Slope 78°)`).

### 4.3 In-Game Visual Editor Screen
The Studio companion provides interactive screens:
- **Terrain Sculptor**: Sliders for `baseHeight`, `heightScale`, `warpStrength`, `frequency`, and cave density with live mini-map preview curves.
- **Strata & Material Palette**:
  - Drag-and-drop layer builder for `SpaceAndDepthMaterialProvider`.
  - Block search bar with **modded block autocompletion** (e.g., typing `slate` lists `minecraft:deepslate`, `create:cut_deepslate`, `biomesoplenty:blackstone_stalagmite`).
  - Fallback tag assignment (`#c:stones`, `#minecraft:dirt`).
- **Prop & Foliage Spreader**:
  - Live adjustment of flora density, slope angle filters ($\theta_{\text{min}} \dots \theta_{\text{max}}$), and foundation plinth depth for prefabs.

### 4.4 Hot-Reload Engine (`/worldgenv2 reload`)
For creators who prefer editing JSON files in external IDEs (VS Code) on a second screen:
- Saving any file in `config/worldgenv2/` triggers a file-watcher notification.
- Running `/worldgenv2 reload` recompiles the AST graphs in memory without restarting Minecraft.
- Active viewport chunks immediately refresh to match the new JSON.

---

## 5. End-Player Experience & Modpack Shipping

1. **Creator Exports Pack**: The creator publishes the modpack via CurseForge/Modrinth. The export tool bundles `config/worldgenv2/` and `mods/worldgenv2-<version>.jar`.
2. **Player Downloads & Plays**:
   - The player launches the modpack.
   - The player selects **Singleplayer $\rightarrow$ Create New World**.
   - `default_world.json` is set as the default world generator.
   - The player enters the world with zero setup, zero technical knowledge required, experiencing the modpack author's custom terrain seamlessly.
