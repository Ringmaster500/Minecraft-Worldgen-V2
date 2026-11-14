# Writing an Addon for Worldgen V2

Worldgen V2 generates **everything** itself (terrain, biomes, ores, vegetation, structures); no vanilla feature or structure
generation runs. Addons extend it with data and a few registries. Nothing patches the generator.

## 1. Ship a preset (no code)
A preset is one JSON file ("bundle"). In Studio: open your project, project menu -> **Export as preset file**
(writes `config/worldgenv2/exports/<name>.json`). Put it in your mod jar:

```
src/main/resources/worldgenv2_presets/index.json      ["wasteland.json", "skylands_pack.json"]
src/main/resources/worldgenv2_presets/wasteland.json
```
Bundle format:
```json
{ "id": "mymod:wasteland", "name": "Wasteland", "description": "...", "kind": "world",
  "assets": { "world.json": {}, "dimensions/overworld.json": {}, "biomes/ash_flats.json": {}, "prefabs/ruin.json": {} } }
```
- `kind: "world"` appears in **New project**; `kind: "pack"` (biomes/prefabs only) is offered by **Add from preset / pack...**
  and never overwrites the player's assets.
- A preset can be used directly with no project folder: `config/worldgenv2/active.json` = `{"preset": "mymod:wasteland"}`.
- Block ids in your biomes may be any registered block, including your own mod's. Biomes choose the Minecraft biome they
  present as (`minecraftBiome`), so modded biomes work for sky/foliage/mob rules.

## 2. Register a preset in code
```java
WorldGenV2Addons.registerPreset(new WorldGenV2Addons.Preset(
    "mymod:skylands", "Skylands", "Floating islands", "world", () -> myBundleJson()));
```

## 3. Add node types (density, curve, prop, pattern, scanner, positions, assignments, mask)
Register a deserializer during mod init (`WorldGenV2Registries.DENSITY_NODE_TYPE.register("MyNoise", (json, ctx) -> ...)`,
likewise `PROP_TYPE`, `PATTERN_TYPE`, ...). To make the Studio's graph palette and inspector know it, ship
`worldgenv2_node_schemas.json` at the root of your jar, same format as `assets/worldgenv2/node_schema.json`:
```json
{ "density": { "MyNoise": { "params": [ {"key":"frequency","t":"number","def":0.01} ], "slots": [ {"key":"input","kind":"density","multi":false} ] } } }
```
(`tools/GenNodeSchema.java` generates the built-in file from the compiler; do the same for yours or write it by hand.)

## 4. Add zone shapes
```java
ZoneLayout.registerMaskKind("coastline", (params, seed) -> (x, z) -> /* true where this zone applies */);
```
Then a zone with `"kind": "coastline"` works in any project. (The Studio zone editor shows custom kinds in the dropdown; their
parameters can be edited in the JSON view.)

## 5. React to compilation
```java
WorldGenV2Addons.registerCompileHook((project, result) -> {
    // result.structure is the compiled WorldStructure: add ore rules, structure placements, etc.
});
```
Hooks run for the Studio preview and for the real world, so previews stay truthful.

## What the player controls vs what you control
Players edit projects in the Studio. You provide presets, packs, nodes and zone kinds. Anything you add is plain data
the player can copy, edit and re-export.
