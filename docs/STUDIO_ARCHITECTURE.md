# Studio Architecture & Dev Loop

The Studio is an **asset IDE**: a project is a folder of JSON assets, the UI edits those assets through one model,
and everything else (preview, export, in-world apply) reads from that model. This replaces the older tab/drill-down
screens (`WorldExplorerTab`, `NodeCanvasTab`, ... — still in the tree, currently unreachable, to be re-hosted or removed).

## Layers (top depends on bottom)

| Package | Role |
| :-- | :-- |
| `studio.gui.WorldGenStudioScreen` | Minecraft `Screen` host. Renders the widget tree on the Studio's own pixel grid (default 2 device px per virtual px, `Ctrl +/-` to change), converts input, routes global shortcuts. No layout, no state. |
| `studio.shell` | Application. `StudioApp` (session state + commands), `Shell` (the one place that lays out panes), `Explorer`, `DocPane`, `TopBar`, `StatusBar`, `ProblemsPanel`, `HomeView`, `Dialogs`, and one `Editor` per asset type (`WorldEditor`, `DimensionEditor`, `BiomeEditor`, `JsonView`). |
| `studio.ui` | Widget toolkit: `Widget` (owns bounds; single `layout()` pass; hit-test and render read the same fields), `UiRoot` (focus, drag capture, overlays), `TextField`, `Button`, `Dropdown`, `Toggle`, `Tree`, `TabBar`, `Form`, `PopupMenu`, `Modal`, `Splitter`, `Pair`. |
| `studio.project` | Model. `StudioProject` (assets on disk, undo/redo, dirty tracking, validation, soft-delete to `.trash/`), `Asset`, `AssetType`, `Validator`, `Templates`, `ProjectStore`, `J` (null-safe JSON access). **UI code never touches files.** |

Rules that keep it from becoming a mess again:
1. Widgets never compute coordinates for other widgets; parents assign bounds in `layout()`.
2. Editors write through `StudioProject.edit(asset, label, mutator)` only. That gives undo, dirty marking and validation for free.
3. Editors bind widgets to JSON paths (`Editor.num/str/choice/toggle`); never copy values into parallel state.
4. State that must outlive the Screen lives in `StudioApp` or `StudioProject`.

## Project layout on disk (`config/worldgenv2/projects/<name>/`)

```
world.json              vertical range: preset, minY, genDepth, seaLevel
dimensions/*.json       id, generator (worldgenv2|vanilla), layout, seed, cellSize, biome pool [{biome, enabled, weight, size, transition}]
biomes/*.json           same JSON AST the engine already compiles (name, category, weight, climate, terrain{...})
prefabs/*.json
.trash/                 deleted assets (recoverable)
```

## Not done yet (honest list)
- **Nothing in the runtime reads a project yet.** The README says `config/worldgenv2/` is auto-mounted as a datapack; there is no
  pack-finder hook in the code. Next: a loader that turns a project into the live `WorldStructure`/datapack, plus an "Apply to world" action
  (`StudioLiveCompiler` already hot-swaps biomes into the integrated server).
- Graph view for biome terrain (re-host `NodeCanvasTab` on `studio.ui`); today the terrain is shown as a read-only outline + JSON.
- Zones, rings and landmarks editors for dimensions; spline editor; asset shelf; macros; map preview.
- Old tabs/`StudioState` are dead code until re-hosted or deleted.

## Dev loop (no manual game restarts for most edits)

`tools/dev.ps1` (PowerShell):

```
.\tools\dev.ps1 start               # build + launch the client, wait until the title screen is reached
.\tools\dev.ps1 call "/open?screen=studio"
.\tools\dev.ps1 shot name           # run\devbridge\name.png  (1920x1080)
.\tools\dev.ps1 reload              # gradle compileJava + hot-swap changed classes over JDWP (127.0.0.1:5005)
.\tools\dev.ps1 stop
```

- `DevBridge` (`studio/dev`) is a loopback-only HTTP server inside the client, enabled by `-Dworldgenv2.devbridge=5006` (set in `build.gradle`'s client run).
  Routes: `/state /screenshot /open /close /world /click /move /drag /scroll /key /type /guiscale`. Coordinates can be GUI units (`x`,`y`) or
  screenshot pixels (`px`,`py`), so you can click exactly what you see in a screenshot.
- `reload` can only swap **method bodies**. Adding/removing fields, methods or changing signatures prints `RESTART_REQUIRED`; then `stop` + `start`.
  New classes need no action. Hash state is kept in `tools/.reload-hashes`.
- The JDWP port is bound to `127.0.0.1` only (it was `*:5005`, i.e. reachable from the network).
- Known quirk: PowerShell `Set-Content -Encoding UTF8` writes a BOM that javac rejects; use `[IO.File]::WriteAllText(path, text, New-Object Text.UTF8Encoding($false))`.
