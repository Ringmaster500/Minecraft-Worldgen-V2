# Studio Architecture & Dev Loop

The Studio is an **asset IDE**. A project is a folder of JSON assets; the UI edits them through one model; a compiler
turns them into the engine's `WorldStructure`; the same compiler drives both the Studio preview and the real world.

```
 Studio UI  ──edit──▶  StudioProject (JSON assets, undo, validation)
                              │
                              ▼
                      ProjectCompiler  ──▶  WorldStructure  ──▶  MapPreview (Studio)
                              │                              └─▶  chunk generation (game world)
                              └── ProjectRuntime (config/worldgenv2/active.json) ◀── "WorldGen V2: Active Studio Project" world type
```

## Layers

| Package | Role |
| :-- | :-- |
| `studio.gui.WorldGenStudioScreen` | Minecraft `Screen` host. Renders the widget tree on the Studio's own pixel grid (default 2 device px per virtual px, `Ctrl +/-`), converts input, routes shortcuts. No layout, no state. |
| `studio.shell` | Application: `StudioApp` (session state + commands), `Shell` (the one place that lays out panes), `Explorer`, `DocPane`, `TopBar`, `StatusBar`, `ProblemsPanel`, `HomeView`, `Dialogs`; editors `WorldEditor`, `DimensionEditor` (+`CardList`), `BiomeEditor`, `GraphEditor` (+`NodePalette`), `JsonView`; `MapPreview` + `PreviewEngine`; `LiveWorld` (apply / regen / teleport / probe). |
| `studio.graph` | `NodeSchemas` (what the engine accepts, generated), `GraphOps` (structural edits on a biome's JSON AST). |
| `studio.ui` | Widget toolkit: `Widget` (owns bounds; one `layout()` pass), `UiRoot` (focus, drag, overlays), `TextField`, `Button`, `Dropdown`, `Toggle`, `Tree`, `TabBar`, `Form`, `PopupMenu`, `Checklist`, `Modal`, `Splitter`, `Pair`. |
| `studio.project` | Model: `StudioProject` (assets on disk, undo/redo, dirty, validation, soft-delete to `.trash/`), `Asset`, `Validator`, `Templates`, `ProjectStore`, `J`. **UI code never touches files.** |
| `pack` | `ProjectCompiler` (project JSON → `WorldStructure`, server-safe) and `ProjectRuntime` (loads the active project for the biome source). |

Rules that keep it from becoming a mess again:
1. Widgets never compute coordinates for other widgets; parents assign bounds in `layout()`.
2. Editors write through `StudioProject.edit(asset, label, mutator)` only (undo, dirty, validation for free).
3. Editors bind widgets to JSON paths; never copy values into parallel state.
4. Anything the preview shows must come from `ProjectCompiler`, so preview == game.
5. State that must outlive the Screen lives in `StudioApp` or `StudioProject`.

## Project on disk (`config/worldgenv2/projects/<name>/`)

```
world.json              preset, minY, genDepth, seaLevel
dimensions/*.json       id, generator (worldgenv2|vanilla), layout, seed, seedMode (world|fixed), cellSize, biomes[] pool,
                        spawn{prefab,x,z,craterRadius,craterDepth,rimHeight,lockSpawn}, landmarks[], budgets[], zones[], rings[], bounds{}
biomes/*.json           the engine's own JSON AST (name, category, minecraftBiome, climate, terrain{}, materialProvider{}, props[])
prefabs/*.json          engine prefab format
.trash/                 deleted assets
```
`config/worldgenv2/active.json` = `{"project":"<name>"}` selects the project worlds generate from.

## Node graph
`tools/GenNodeSchema.java` scans `JsonAstCompiler.java` and writes `assets/worldgenv2/node_schema.json` (349 node types: params with defaults,
child slots, kinds). Re-run it after adding engine node types. The old `StudioNodeCatalog` did **not** match the compiler (different type names, different socket
model) and is no longer used by the Studio. The graph edits the biome JSON directly; the whole biome is one graph (Biome output → terrain / materials / props / placement).

## What generates in game
- World type **WorldGen V2: Active Studio Project** (`worldgenv2:project`): Overworld uses `layout_preset: "project:overworld"`.
  `WorldGenV2BiomeSource` calls `ProjectRuntime.build`, which compiles the project and maps each project biome onto a Minecraft biome (`minecraftBiome`, any registered id incl. mods).
- Placed in chunks: biomes, terrain, materials, props, rivers/caves, zones, spawn crater, roads (existing) **and now prefab structures**
  (`StructurePlacement` stamped in `StagedChunkGenerator` stage 4b): spawn structure, landmarks, budgeted POIs.
- Studio **Apply** saves, writes `active.json`, and `reload()`s every project-driven generator in the open world; **Regen** rebuilds chunks around the player; map right-click → **Teleport here**.

### Nothing from vanilla runs
`WorldGenV2ChunkGenerator` overrides `applyBiomeDecoration`, `createStructures` and `createReferences` as no-ops, so vanilla trees/ores/flowers/lakes and villages/strongholds
never generate (the mapped Minecraft biome only supplies sky/foliage colour, mobs and music). Verified with block censuses: an old chunk had andesite/tuff/diorite/granite/gravel/dirt
blobs and glow lichen; fresh chunks contain only stone, bedrock, what our materials paint, and our rules.

### Systems, in generation order
1. Terrain: biome density graphs blended by `BiCarta`, then rivers, caves, terrain shapes (`shapes`), spawn crater.
2. Materials: each biome's `materialProvider` (strata, surface), river bed, underground biomes.
3. Props (`props` in each biome): Hytale-style **positions → scanner → assignments → prop**, run **once per chunk** (`AssignmentsProp.generateInChunk`), only on columns the biome owns; per-column props (`BlockProp`, `MaskProp`...) still run per surface column.
4. **Ores** (`ores` in a dimension): `OreRule` veins, chunk-order independent; restrict by Y, spread, biome or zone.
5. **Structures**: spawn structure, landmarks, budgeted POIs (`StructurePlacement`), then roads.

### Zones (Hytale-style macro regions)
A dimension's `zones` array: each zone has a `kind` (`patch` weighted macro Voronoi cells, `radial` distance band, `noise` range, `everywhere` fallback, or an addon kind) and a **biome list**.
First matching zone wins. Zone is decided at each biome cell's centre, so zone borders follow biome-cell borders and blend normally. Landmarks, POIs and ores can be restricted to a zone.
(Radial terrain sculpting - islands, moats - is separate and called `shapes`; old projects are migrated automatically.) The map's **Zones** button tints regions; hover shows the zone.

### Freedom model: Default, and Off / Default / Custom per feature
There is exactly one built-in starting point, **Default** (`BuiltinPresets`): example biomes, the three dimensions, nothing else turned on.
With no setup chosen, worlds generate from it, so the mod works with zero configuration.

Each optional feature of a dimension has a mode in `features.<name>`:
`off` (does nothing, your data is kept), `default` (built in, no setup; currently ores only), `custom` (the setup's own data).
Features: `zones`, `spawn`, `landmarks`, `budgets`, `ores`, `shapes`, `rings`; rivers and caves are plain on/off toggles.
When `features.<name>` is absent the mode is inferred (has data -> custom; ores with no data -> default), so older setups keep working.
"Default ores" is `WorldGenV2Addons.defaultOres()`; an addon/modpack can replace what Default means with `setDefaultOres(...)`.
The Studio collapses a feature that is not Custom to one line; "Copy the default ores here so I can edit them" turns Default into Custom.

### Addons
Addons use the same bundle registry (`pack.WorldGenV2Addons`; see `docs/ADDONS.md`) to offer extra starting content; players see it under
"Import from addon..." only if an addon is installed. "Export as shareable file" turns any setup into a bundle an addon can ship.

## Placement, terrain and water features
- **Zones are assets** (`zones/<id>.json`): `kind` patch / radial / noise / **graph** / everywhere, `biomes`, and for graph zones a `mask` density graph (where; matched between `min` and `max`, soft edge via `softness`) and an optional `terrain` graph scaled by `terrainStrength`. A dimension lists zone ids; old inline zone objects still load.
- **Rotation**: `rotation` (0/90/180/270 or `random`) on spawn, landmarks, budgets. `Prefab.rotated(n)` turns voxels, sockets, foundations, anchor and direction-dependent block-state properties. Prefab block states (stairs facing, log axis) are applied when stamped.
- **Placement rules** (`placements/<id>.json`, listed by a dimension): a Finder (how a spot is found: `Point`, `Span` = a stretch of water between two banks) + Conditions (Biome, Zone, Surface, Wet, Density graph, StructureNear, Expression) + Actions (`Prefab`: chosen by an expression, turned, offset, set on the ground or a computed height, can refer to earlier placements and their sockets). Built as nodes in the Studio and extensible by addons through `Placement.registerFinder/Condition/Action` (see ADDONS.md); deterministic per grid cell, so chunk-order independent. The shipped *Fallen tree across a river* is just such a rule (log fitted to the gap, then standing trees at the log's `root` socket whose height must be at least the log's length). Old `spans` entries still load and are converted to rules. Sea water fills downward from sea level, so water stays under logs, arches and piers.
- **Buried structures**: `depth` on spawn/landmarks/budgets sinks a structure below the surface. The `StructureNearProp` node (wrap any prop) runs its child only when a placed structure is near (`structure` id filter, `radius`, `yMin`/`yMax` relative to the prop) or, with `invert`, when none is: "a tree only where a vault lies beneath".
- **Rivers follow the land**: a river's water level comes from the local valley floor at its centerline (`RiverCarver.levelAt`, using the terrain's own heights via a cached height sampler), so it sits flush with the land at any elevation and never digs a canyon to sea level. The channel is a shallow trough (`depth`, default 3), banks blend flush into the ground over `valleyWidth` (bank to bank), and rivers fade out before mountains (water level 96-124). Chunk generation fills river columns up to their own level. Settings: `riverSettings` {spacing, channelWidth, valleyWidth, depth, meander, widthVariation}. **Oceans**: `ocean.depthScale` (land untouched); Default ships an Open Sea biome.
- Dev routes: `/spans?x&z&r` lists bridges near a point, `/cmd?c=<server command>` (time, tp with rotation, gamemode).

## Known limits / next
- Do not use `/regen` on chunks that were already saved: vanilla's light engine can throw and stop the server. Test on fresh chunks (tp somewhere new).
- Props at chunk borders are clipped: a prop is placed only from anchors inside its own chunk and writes outside the chunk are dropped, so a tree on a chunk edge can lose part of its canopy. Fix is a two-pass/halo scheme (Hytale's "context precision").
- Heights are fixed by the world's dimension type (Standard -64..320, or the Hytale 512 type); `world.json` heights do not yet change the in-game dimension type.
- Only the Overworld is project-driven; Nether/End stay vanilla. (Rotation and spawn-lock are now implemented.)
- No node thumbnails (live density previews on graph nodes), spline editor, asset shelf, macros, or prefab editor yet. Terrain material/prop slots are editable in the graph but have no form view.
- Landmarks connect only parent→child one level deep (engine limit); parent can be `spawn`.
- `WorldExplorerTab`, `NodeCanvasTab`, `ChunkViewportTab`, `CodexWikiTab`, `StudioState`, `StudioNodeCatalog` are dead code kept for reference; delete once nothing is wanted from them.

## Dev loop (no manual game restarts for most edits)
`tools/dev.ps1`:
```
.\tools\dev.ps1 start                 # build + launch the client, wait for the title screen
.\tools\dev.ps1 call "/open?screen=studio"
.\tools\dev.ps1 shot name             # run\devbridge\name.png (1920x1080)
.\tools\dev.ps1 reload                # compile + hot-swap changed classes (method bodies only) over JDWP 127.0.0.1:5005
.\tools\dev.ps1 stop
```
`DevBridge` (loopback only, `-Dworldgenv2.devbridge=5006`) routes: `/state /screenshot /open /close /world?name= /click /move /drag /scroll /key /type /guiscale`
(coordinates in GUI units `x,y` or screenshot pixels `px,py`) and Studio drivers `/openproject?name= /doc?path=&view=form|graph|json /save /apply /regen /tp?x&z&up /probe?x&z&r /patch?path=&json=`.
`/probe` reports the real terrain column and how many solid blocks exist at each structure placement, which is how placement was verified.
- `reload` cannot add/remove fields or methods (or lambdas): it prints `RESTART_REQUIRED`; then `stop` + `start`. New classes need no action.
- PowerShell `Set-Content -Encoding UTF8` writes a BOM javac rejects; write sources with `[IO.File]::WriteAllText(path, text, New-Object Text.UTF8Encoding($false))`.
