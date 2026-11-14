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

## Known limits / next
- Heights are fixed by the world's dimension type (Standard -64..320, or the Hytale 512 type); `world.json` heights do not yet change the in-game dimension type.
- Only the Overworld is project-driven; Nether/End stay vanilla. Landmark prefabs are not rotated (`yaw` ignored); spawn-lock (`lockSpawn`) is not yet wired to the world spawn point, so the player can spawn inside terrain/crater.
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
