# Minecraft Worldgen V2: the project file

One file for what this is, how it works, what is done, what is left, and where it is going. It replaces the old roadmap, phase, rework and architecture documents. Other documents that remain: `README.md` (short intro), `docs/ADDONS.md` (the addon API), `docs/research/` (background specifications, see section 9).

Last consolidated: 2026-11-15.

---

## 1. What this is

A NeoForge 26.1 Minecraft mod that replaces vanilla world generation with a generator modelled on **Hytale's World Generator V2**, plus an in-game **Studio** for building worlds with it. Nothing from vanilla generation runs in a Worldgen V2 dimension: terrain, materials, props, ores, structures, rivers and caves are all ours. The mapped vanilla biome only supplies sky and foliage colour, mob spawns and music.

Two goals, in this order:
1. **Parity with Hytale.** A node that exists in Hytale means exactly what it means there (same name, parameters, inputs and behaviour). Real Hytale assets (`Assets.zip`) are the ground truth.
2. **Our extras on top.** Placement rules, the Studio, addons, adjustable world height, Distant Horizons support and so on. Extras never change what a Hytale-named node does.

Standing rules:
- Say **"world setup"** (not "project" or "preset") in anything the player reads.
- Every optional feature offers **Off / Default / Custom**. Nothing is forced on the creator.
- **Speed matters, and optimisation must not change the generated blocks** (checked with block hashes, section 5).
- Never copy the user's Hytale files into the repository. They are read in place from the player's install (`hytale_local/` is git-ignored).
- The default world height is **-64 to 320** and is adjustable per world setup.

## 2. Architecture

```
 Studio UI ──edit──▶ StudioProject (JSON assets, undo, validation)
                           │
                           ▼
                     ProjectCompiler ──▶ WorldStructure ──▶ Studio map preview
                           │                           └─▶ chunk generation (the world)
                           └── ProjectRuntime ◀── the world's own setup (worldgenv2.json in the save)
```

| Package | Role |
| :-- | :-- |
| `density`, `curve`, `vector`, `material`, `pattern`, `props`, `prefab`, `worldstructure`, `engine.chunkgenerator` | The engine: pure Java, no Minecraft coupling. Density functions, materials, props, the staged chunk generator. |
| `biome.JsonAstCompiler` | Turns node JSON (ours and Hytale's) into engine objects. Keys are read without regard to case. |
| `hytale` | Hytale parity layer: `HDensity`, `HMaterials`, `HPositions`, `HDistributions`, `HProps`, `HytaleAssets` (reads the player's Assets.zip), `HytaleBlocks` (Hytale block name to Minecraft block), `HytaleRotation` (prefab rotations). |
| `minecraft` | NeoForge adapter: `WorldGenV2ChunkGenerator`, `WorldGenV2BiomeSource`, height (`HeightPack`, `WorldHeightDimensions`), `WorldProjectLink`, `compat/DistantHorizonsCompat`, the Create World `gui/`. |
| `pack` | `ProjectCompiler` (setup JSON to `WorldStructure`), `ProjectRuntime` (which setup a world uses), addons registry, built-in presets. |
| `placement` | Our placement rules: finders, conditions, actions. |
| `studio.project` | The model: `StudioProject` (assets on disk, undo/redo, validation), `Asset`, `Templates`, `Validator`. UI code never touches files. |
| `studio.ui` | The widget toolkit (own pixel grid, independent of Minecraft's GUI scale). |
| `studio.shell` | The application: `StudioApp`, `Shell`, `Explorer`, `DocPane`, `TopBar`, editors (`WorldEditor`, `DimensionEditor`, `BiomeEditor`, `GraphEditor`), pickers (`BlockPicker`, `ListPicker`, `NodePalette`), `MapPreview`, `LiveWorld`. |
| `studio.graph` | `NodeSchemas` (what the engine accepts), `GraphOps`. |
| `studio.dev` | `DevBridge`: a loopback HTTP control surface for development (section 7). |

Studio rules that keep it tidy: widgets never position other widgets (parents assign bounds in `layout()`); editors write only through `StudioProject.edit(...)`; editors bind to JSON paths, never parallel state; anything a preview shows must come from `ProjectCompiler`, so preview equals game; state that outlives the screen lives in `StudioApp` or `StudioProject`.

### A world setup on disk (`config/worldgenv2/projects/<name>/`)
```
world.json          preset, minY, genDepth, seaLevel
dimensions/*.json   id, generator (worldgenv2 | vanilla), layout, seed, seedMode, cellSize, biomes[] pool, features{}, hytale{assets, worldStructure}, ...
biomes/*.json       the engine's JSON node tree (also accepts Hytale biome files as written)
zones/ placements/ prefabs/   optional assets
.trash/             deleted assets
```
Each **world** remembers its setup in `<save>/worldgenv2.json`. `config/worldgenv2/active.json` only says what **new** worlds start from. Loading a world always uses its own setup; the Studio opens and edits the open world's setup.

### How generation runs
1. **Terrain**: each biome's density tree, blended through the biome map (Voronoi/BiCarta, or a Hytale World Structure density with delimiters), then rivers, caves, terrain shapes, spawn crater. Chunk terrain is computed once as a shared run-length tile.
2. **Materials**: each biome's material provider paints strata and surface.
3. **Props**: Hytale style (positions, scanner, assignments, prop) evaluated over a context region so props at chunk borders agree from both sides.
4. **Ores**, then **structures** (spawn structure, landmarks, budgeted points of interest, roads, placement rules).
Nothing from vanilla generation runs (`applyBiomeDecoration`, `createStructures`, `createReferences` are no-ops).

### Features of a dimension
- **Zones**: Hytale-style macro regions, each with its own biome set (`patch`, `radial`, `noise`, `graph`, `everywhere`). First match wins.
- **Biome map**: either your biome pool in random regions, or a **Hytale zone** (`hytale.worldStructure`, for example `Zone1_Plains1`) whose own map places Hytale's biomes. While a zone is chosen, the pool is not used.
- **Spawn structure, landmarks, budgeted POIs**, with rotation and buried depth; **roads** between landmarks.
- **Placement rules** (`placements/*.json`): a finder, conditions and actions, built as nodes; addons add more. Example: fallen trees across rivers.
- **Rivers and caves**: classic noise or Hytale-style graphs; rivers follow the land (flush water level, shallow trough, banks blend); oceans have a depth scale.
- **Ores**, **terrain shapes**, **pillar rings**.
- **Adjustable world height** (section 4).
- Rotation of prefabs: `rotation` (0/90/180/270 or random); block rotations from Hytale prefabs become Minecraft state (log axis, stair facing and half, slab half, vine face, dripstone direction).

## 3. The Studio (how to use it, in short)
- Open with **G**, `/studio`, or the Studio button on the pause screen. Inside a world it opens that world's setup.
- **Explorer** (left): world settings, dimensions, zones, placement rules, biomes, prefabs. **+** adds one. New biome offers Blank, a preset's biome, or a Hytale biome. New dimension lists every dimension the game has.
- **Editors**: Form (the common settings), Graph (nodes), JSON (everything). The dimension editor also has a live **Map**.
- **Graph**: wires nodes into a biome. Double-click a node or empty canvas, or right-click, to search and add. Block fields have a searchable **Pick** list with icons.
- **Top bar**: Undo, Redo, Save, **Apply** (saves and recompiles into the open world), **Regen** (rebuilds the whole generated world, saved chunks plus view distance; player builds in them are replaced), **Live** (after each edit, rebuilds only a square of chunks around you; Pin moves it, r sets its size).
- Shortcuts: Ctrl+S save, Ctrl+Z undo, Ctrl+Y redo, Ctrl+J problems, Ctrl+Enter apply, Esc close.

## 4. Status: what is done

**Engine and parity** (all verified by `verifyEngine`, `verifyGemini`, `verifyPhase*`): density, curve, vector, material, pattern, scanner, positions, assignments, props, Prop Runtime context, World Structure, prop distributions, Graph Generator conformance, rivers and caves as graphs, JSON AST parity (293 node types), Hytale release schema nodes (Column, Cluster, Box, Prefab directions, Pipeline, Mesh2D/3D, Cache2D and more). 201 of 202 real Hytale asset files compile (`probeHytaleAssets`). Hytale block names map to Minecraft blocks through `hytale_blocks.json` (exact name, then longest prefix).

**Speed** (output byte-identical): about 57 ms of work per chunk on a 384-tall Hytale world (about 42 ms at 320 tall), down from about 580 ms. Run-length terrain tiles, per-tile futures, Y-independence tabulation, material run lengths, cached prop positions, memoised first-stage anchors. Opt-in extras (`WG2_PRUNE=1`, `WG2_SHARE=1`) did not pay off on Hytale assets.

**World height**: each setup chooses Min Y and depth (presets: Standard -64..320, Hytale 320, 512, Extended 576, Epic 1024, custom). A level's height comes from its registered dimension type, so `HeightPack` writes one per height into `config/worldgenv2/heights` (an always-on data pack) and `WorldHeightDimensions` swaps the overworld's type when a world is created. Vanilla shows a one-time "experimental settings" prompt for such worlds. Existing worlds keep their height.

**Distant Horizons**: far terrain is generated by the engine itself through DH's world generator API (about 140 far chunks a second); does nothing when DH is absent. Set `WG2_WITH_DH=1` to run DH in the dev game.

**Prefab rotations**: Hytale's block rotation index (roll*16 + pitch*4 + yaw) is converted to Minecraft block state by `HytaleRotation`, verified against Hytale's own table for all 64 values.

**Studio**: the shell, editors, graph, map, block picker, new-biome and new-dimension lists, live region preview, per-world setups, a Biome map switch, and the dev bridge.

## 5. Verification
- `./gradlew verifyEngine verifyGemini verifyPhase33 verifyWorldHeight` and `verifyGuide` (the chain also runs verifyPhase3..32). All pass.
- **Same output after optimising**: `tools\samecheck.ps1` hashes 75 chunks in three areas (the `/hash` route) against `tools\samecheck_reference.txt` (valid for the 384-tall `projtest` world). Prefab changes intentionally alter output for Hytale prefabs, so that reference needs regenerating after them.
- `tools\benchgen.ps1` times first view-radius generation (`-Profile` records JFR, `tools\jfrtop.ps1` summarises; `WG2_COUNTERS=1` writes per-chunk timings).
- Probes: `probeHytaleAssets`, `probeHytaleCoverage`, `probeHytaleDensity`, `probeHytaleSpeed`.

## 6. Known gaps and open questions
- **Terrain walls**: in a Hytale zone world, tall sheer vertical walls and stripes appear. It may be faithful (the zone has Gorges and Mountains biomes) or a parity bug. Settling it needs Hytale's own output: the Hytale server jar is on the dev machine, so generating reference terrain headless is the proper check.
- Studio map preview ignores a chosen Hytale zone (shows the Voronoi map).
- `SimplexNoise2D/3D` without a Seed, and `FastGradientWarp`, seed from a compile-order counter; Hytale hashes the Seed string. Angle `IsAxis`, biome transitions, Prefab MoldingDirection/LegacyPath/LoadEntities, entities in prefabs.
- Border props: a prop reaching beyond its context region can be clipped (fixing costs about 40% speed; undecided).
- Prefab block shapes beyond stairs, halves, logs, vines, dripstone: rails, furniture, quarters.
- The vanilla "experimental settings" prompt on custom-height worlds cannot be removed without a mixin.
- Not built: a C2ME-style density bytecode compiler (estimated 5 to 10%), a Voxy adapter, a Studio UI for choosing the Hytale assets path (it is a JSON setting), a prefab editor and viewer, a world-setup README for outsiders.
- Only the Overworld is project-driven; Nether and End stay vanilla unless switched in the dimension.
- `/regen` style rebuilds on already-saved chunks can disturb vanilla's light engine; Regen now loads and rebuilds them in batches, but test on a copy of a world you care about.

## 7. Dev loop
`tools\dev.ps1`: `start` (build and launch, wait for the title screen), `reload` (compile and hot-swap method bodies over JDWP; adding or removing methods or fields prints `RESTART_REQUIRED`), `stop`. `DevBridge` (loopback only, port 5006) routes: `/state /screenshot /open /close /world /click (x,y,button,dbl) /move /drag /scroll /key /type /guiscale`, Studio drivers `/openproject /doc /save /apply /regen /tp /probe /heights /census /hash /patch /newworld /newasset /newproject /cmd /spans`. Coordinates are in GUI units (screenshot pixels divided by the GUI scale, 3 on a 1080p screen). Write sources without a BOM (`[IO.File]::WriteAllText(path, text, New-Object Text.UTF8Encoding($false))`). The build needs Java 25; Gradle finds it.

Windows PowerShell pitfalls seen repeatedly: `R` alias clashes, backtick quoting in here-strings, `Remove-Item` on protected paths (use `[IO.Directory]::Delete`), relative paths in `[IO.File]` calls (use absolute).

## 8. Roadmap

**Next (in order)**
1. **In-game wiki / guide** (section 8.1).
2. Settle the terrain walls against real Hytale output; fix the Studio map preview for Hytale zones; a "biomes in use in this world" list.
3. Studio polish: prefab viewer with rotation preview, Hytale assets path UI, graph editor basics (multi-select, auto-arrange, search, drag to reconnect), collapsible and searchable dimension form, collapsible Create World list.
4. Prefab shape coverage (rails, furniture, quarters), entities, MoldingDirection.
5. Remaining parity items from section 6.
6. Optional: Voxy adapter, density bytecode compiler.

### 8.1 The in-game wiki (started: the Guide button)
A **Guide** tab inside the Studio, opened from the top bar (and from any editor with a "?" next to a field or node). Built from data already in the repo, not hand-copied.

- **How-to sections**: the Studio overall; the biome editor; the dimension editor (each feature: zones, spawn, landmarks, rivers, caves, ores, height); the graph editor; the map and Live preview; world setups and how worlds remember them; importing from Hytale and presets; addons.
- **Node reference**: one page per node, generated from `node_schema.json` and `hytale_node_schema.json` (parameters, defaults, input slots, what plugs in where) plus the text in `assets/worldgenv2/node_wiki.json` (formula and description for 295 node types, kept from the old node catalog). Pages group by kind (density, curve, vector, material, pattern, prop, positions, scanner, assignments, mask, graph).
- **Interactive demos**: for a node where it makes sense, a small simulated preview with sliders for the node's parameters and a stand-in for every input it needs, so the effect can be seen and played with. Order of build: density nodes first (a 1D curve and a 2D heightmap preview, since most terrain nodes are densities), then curves, then materials (a column of blocks), then positions and scanners (dots on a grid), then props (a stamped footprint). The preview evaluates the real engine node, not a copy, so it cannot drift from the game.
- **Teaching setups**: short guided examples that load into a scratch setup: a flat biome, rolling hills, mountains with snow, a beach, a forest with trees, a river, a cave, a floating island. Each explains why every node is there.
- **Search** across everything; links between pages (a node page lists what it commonly connects to).

Built so far: the Guide tab (top bar), search, nine written how-to pages (`guide.json`), a generated page for every node (421 of them, from the schemas plus `node_wiki.json`), four teaching setups (`GuideRecipes`: flat grassland, rolling hills, snowy mountains, desert dunes; each has a button that adds the biome to the open world setup, and a check that it compiles and has ground), and a live demo for density and curve nodes (`DensityDemo`: sliders for the settings, stand-ins for the inputs, a top-down slice and a height profile, run by the real engine; 77 of 79 density nodes and all 23 curve nodes build one). Demos by kind: density 77 of 79, curve 23 of 23, material 18 of 19, positions 38 of 39 (points from above), pattern 21 of 21 (where it matches on a small landscape), scanner 7 of 7 (what it finds), prop 17 of 24 (what it builds, cut through a slice), vector 45 of 45 (arrows), assignments 7 of 10 (which prop lands where); `SceneDemo` runs the block-level ones on a scratch landscape. Not demoable alone: masks, prop runtimes and the graph-generator families, and a few props that need structured entries (Weighted, DensitySelector). Still to build: the teaching setups; more how-to text; "?" links from editors into pages.

Build order for 8.1: the page framework and search; the node reference generated from the schemas; the how-to text; density demos; the teaching setups; remaining demo kinds.

## 9. Reference material
- `docs/research/`: 18 background specifications (philosophy, staged generation, biomes, density maths, materials, props, schema, 26.1 adaptation, implementation spec, spawn and landmarks, prefab translators, Hytale generator documentation under `docs/research/hytale_generator_docs/`). Background only; where they disagree with this file or the code, this file and the code win.
- `docs/ADDONS.md`: registering presets, node schemas, finders, conditions and actions from another mod.
- `addons/hytale-compat/`: an older, set-aside translator (block map, Orbis zone presets, prefab converter). The active Hytale support is in the `hytale` package.
- The real ground truth: the player's Hytale `Assets.zip` and `HytaleServer.jar` (never copied into the repo).

Repository: https://github.com/Ringmaster500/Minecraft-Worldgen-V2 (public).

## 10. Driving the Studio without clicking (dev bridge)

For automated testing and for an assistant working through the dev bridge (`tools\dev.ps1 start`, loopback port 5006). All answers are JSON except the images.

| Route | What it does |
| :-- | :-- |
| `/assets`, `/asset?path=`, `/problems` | list assets; read one asset's JSON; every error and warning |
| `/put?path=&json=` (GET or POST body), `/set?path=&key=a.b.0.c&value=`, `/unset?path=&key=` | replace an asset; set or remove one value by dotted path (parents created, numbers index arrays) |
| `/patch?path=&json=` | replace whole top-level keys of an asset |
| `/delete?path=`, `/rename?path=&name=`, `/undo`, `/redo`, `/save`, `/apply`, `/regen` | edit-session commands |
| `/importbiome?source=preset:ID/biome` or `hytale:Name` | add a ready-made biome |
| `/compile?dim=overworld` | compile as the game does: warnings, biomes (id, asset, minecraft biome), sea level, height |
| `/live?on=&radius=`, `/guide?open=&page=` | live preview, open the guide at a page id |
| `/openproject`, `/doc`, `/newproject`, `/newasset`, `/newworld?project=&name=`, `/tp`, `/cmd` | navigation and world control |
| `/topdown?x&z&size`, `/side?x&z&dir=x|z&len&y0&y1`, `/iso?x&z&size` (PNG) | pictures of the world drawn from its blocks: a map from above, a vertical cut, an isometric-style view |
| `/census`, `/probe`, `/heights`, `/hash`, `/state`, `/screenshot`, `/click` etc. | inspection and raw UI |

## 11. Showcase: the Crashlanded pack
A real modpack was built on top of this mod as a test of the whole system (a separate workspace, `C:\VSCode Projects\Crashlanded`): a hot dry alien world with a crash site, a debris trail with roads, four vaults under coloured beacon beams, infinite outposts and relay towers, radial difficulty zones, 3D cave biomes, a replacement Nether ("the Underdeep"), a quest tree and structures built from Laboratory Blocks, XKs Decoration, Powah and Connected Glass blocks. Building it fixed three things in this mod: prefabs with explicit air now clear the ground behind them; the voxel space creates block entities for blocks that need one (beacons, lecterns, modded machines); and the built-in test content (a citadel with two watchtowers joined by roads, a per-landmark tower stamp, the `test-prefab` command) was removed. The dev bridge gained `/blocks` (blocks per mod) and a `dim=` option on the world pictures.
