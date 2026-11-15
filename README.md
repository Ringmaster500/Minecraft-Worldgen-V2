# Minecraft Worldgen V2 for Minecraft 26.1

A NeoForge 26.1 mod that replaces vanilla world generation with a generator modelled on Hytale's World Generator V2, plus an in-game **Studio** for building worlds with it. It can read your own Hytale install (Assets.zip) in place, so Hytale's biomes, props and prefabs generate in Minecraft. Nothing from your Hytale files is copied into this repository.

**Everything about the project (what it is, how it works, what is done, what is left, the roadmap) is in [`docs/PROJECT.md`](docs/PROJECT.md).**

## Quick start
1. Build and run the client: `.\tools\dev.ps1 start` (needs Java 25; Gradle finds it), or `.\gradlew.bat runClient`.
2. Create a world, choose the **Worldgen V2: Project** world type, press **Customize** and pick a world setup.
3. Open the Studio with **G** (or the Studio button on the pause screen) to edit the setup. Each world remembers its own setup.

To use Hytale's own content, put your Hytale install's `Assets.zip` path in a dimension's `hytale` block (`"assets"`, plus `"worldStructure": "Zone1_Plains1"` to use a Hytale zone's biome map).

## Where things are
- `docs/PROJECT.md`: the project file (architecture, status, gaps, roadmap, dev loop).
- `docs/ADDONS.md`: how another mod adds presets, nodes, finders, conditions and actions.
- `docs/research/`: background specifications (the code and `PROJECT.md` win where they differ).
- `src/main/java/com/worldgenv2`: the engine, the Hytale layer, the Minecraft adapter and the Studio.
- `tools/`: dev loop, verification and benchmark scripts.
- `addons/hytale-compat/`: an older, set-aside Hytale translator.
