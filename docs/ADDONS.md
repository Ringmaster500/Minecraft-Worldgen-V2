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

## Placement rules: finders, conditions and actions

Anything that "looks for a spot and builds there" (a fallen tree over a river, a hilltop lookout, a shrine near water, a camp
beside a lake) is a **placement rule**: data in `placements/<id>.json` that a dimension lists under `placements`. In the Studio
it is a node graph (Find, Conditions, Place). The built-in pieces and yours are registered the same way, so an addon can add a
new piece in a few lines and creators get it as a node immediately (the registry feeds the node schema; no schema file to write).

```java
// in your mod, any time before worlds compile (mod constructor is fine)
Placement.registerFinder("Shore", "A point on dry land right beside water.",
    List.of(Placement.param("reach", "int", 6, "How far from the water")),
    (cfg, ctx) -> (env, px, pz, rng) -> {                       // Finder: (Env, px, pz, rng) -> Vars or null
        int reach = cfg.get("reach").getAsInt();
        for (int d = 1; d <= reach; d++) if (env.wet(px + d, pz)) return new Vars().set("loc.x", px).set("loc.z", pz).set("point.x", px).set("point.z", pz);
        return null;
    });

Placement.registerCondition("MoonPhase", "Only on this moon phase.", List.of(Placement.param("phase", "int", 0, "0-7")),
    (cfg, ctx) -> { int p = cfg.get("phase").getAsInt(); return (env, vars) -> myMod.phase() == p; });
```

* **Finder** `find(env, px, pz, rng)` looks around a candidate point and returns what it found as `Vars` (`loc.x/z` is where the
  find is; other names are yours), or null. `env.surface(x,z)`, `env.waterLevel(x,z)`, `env.wet(x,z)` read the terrain exactly as
  chunk generation will produce it. `reach()` says how far it can look.
* **Condition** `test(env, vars)` accepts or rejects. Built in: Biome, Zone, Surface (height range), Wet, Density (any density node
  graph, sampled at the find), StructureNear, Expression.
* **Action** `run(env, vars, rng, out)` adds `StructurePlacement`s and may record more variables. Return false when a required step
  fails and nothing from the rule is placed. Built in: Prefab (see below).
* Pieces must be **pure functions of their inputs and `env`**: rules run per cell of a grid and must give the same answer whichever
  chunk is generated first.

**Expressions** (`require`, `height`, the Expression condition): numbers, `+ - * /`, comparisons, `&& || !` (or `and or not`),
`min max abs floor ceil`, and dotted variables. A missing variable is NaN, so a comparison with it is false.

The **Prefab** action takes `from` (ids), `require` (an expression that decides which prefabs qualify, with `prefab.length`,
`prefab.height`, `prefab.width`), `rotate` (`none`, `random`, `alignSpan`, degrees), `at` (`point`, `span.center`, `<name>.center`,
`<name>.socket.<socket>`), offsets `alongMin/Max` and `sideMin/Max`, `count`, `height` (an expression; `ground` is the surface at the
spot), `align`, `onLand`, `optional`. It records `<name>.x/y/z`, `.centerX/Z`, `.length`, `.height` and `.socket.<socket>.x/z` for later
actions. Example: the shipped *Fallen tree across a river* places a log with `require: prefab.length >= span.length + 2 && ...`,
then up to three trees at `log.socket.root` whose `require` is `prefab.height >= log.length`, so only trees tall enough to have
made the log are used (if none is, the rule places nothing).
