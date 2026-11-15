# Hytale parity

Goal: match Hytale's world generator wherever it has an answer, so anything that works there works here the same way, then build our extras on top. Reference: `docs/research/hytale_generator_docs/` (scraped from docs.hytale.com/hytale-generator).

## Principle
A node name that exists in Hytale must mean exactly what it means there: same parameters (PascalCase in Hytale JSON; we read keys case-insensitively), same inputs, same semantics. Our own nodes that Hytale does not have stay, under their own names, and are clearly extras. Where one of our nodes shares a name with a Hytale node but behaves differently, Hytale's behaviour wins and ours is renamed.

## Status (node level, from `tools`-style audit of the docs against `node_schema.json`)

| Category | Matches Hytale | Differs (params/inputs) | Missing |
|---|---|---|---|
| assignments | 0 | 5 | 0 |
| curve | 17 | 2 | 0 |
| density | 32 | 28 | 11 |
| material | 2 | 9 | 2 |
| pattern | 3 | 9 | 3 |
| positions | 4 | 16 | 2 |
| prop | 0 | 16 | 0 |
| scanner | 1 | 5 | 0 |
| vector | 13 | 4 | 0 |

(Some 'differs' entries are only naming, for example ours takes a child under a different key. Counts are for nodes documented by Hytale; our extra nodes are not counted.)

## Done
- **Prop Runtime context** (item 1 below): terrain is a deterministic per-chunk stage (bedrock and water included); distribution props run as staged runtimes over a context region sized from the props' reach, every anchor that can reach a chunk is evaluated with seeds that depend only on the anchor, only the chunk's own cells are committed. Proven by Phase18VerificationTest.
- **Hytale Pattern, Scanner, Locator, Orienter** (`com.worldgenv2.hytale`, registered under their Hytale names): Constant, BlockType, BlockSet, Offset, Rotator, Floor, Ceiling, Wall, Surface, Cuboid, And, Or, Not, FieldFunction; Origin, Linear, Random, Radial, Queue scanners that enumerate positions and leave validity to the Pattern; Locator (Pattern + Scanner + PlacementCap); Orienter (Rotations + Pattern + Scanner + SelectionMode, turning the child with the pattern). Patterns are rotation-aware, so one definition tests a prop in every direction. Phase 19 (30 checks) and Phase 20 (7 checks, Hytale-format JSON through the compiler) cover them, including a log laid across a gap in either direction.
- **Hytale biome Props form**: `Props: [{ Runtime, Positions, Assignments, Skip }]` loads; a Hytale distribution generates props at the position given (no surface snapping), and Locator/Orienter do the searching. Runtime numbers start at 0 as in Hytale.
- Where a Hytale name is unsuffixed (`Locator`) it now means Hytale's node; the engine's own versions stay under their suffixed names (`LocatorProp`, `OrienterProp`, `SurfacePattern`) and still load.
- **Hytale positions providers** (`HPositions`): SquareGrid2d/3d and TriangularGrid2d (one-block spacing, no parameters; our shipped biomes' `spacing` is honoured as an extension), Scaler (3D scale), Jitter2d/3d, DirectionalJitter, Clusters (+ contextual Anchor), List, Anchor, Bound, FieldFunction, Occurrence, Offset, VectorOffset, BaseHeight and Framework (read the world's Framework constants/positions, once World Structure fills them), Union, SimpleHorizontal, Cache.
- **Hytale props**: Offset, Mask (DontPlace / DontReplace / Advanced), StaticRotator, DensitySelector (first delimiter wins), Density, Cuboid, Manual, Prefab (weighted paths), plus the Hytale `Constant` material; Queue, Union, Weighted, Constant/Weighted/FieldFunction/Sandwich assignments verified against the docs. Phase 21 (33 checks). Assumed shapes where the docs are silent: Material as an id or `{Solid}`, Bounds as `{Min,Max}` vectors, Rotation as degrees, SimpleHorizontal's RangeY as a number (symmetric) or `{Min,Max}`.
- **Hytale density nodes** (`HDensity`): Multiplier (short-circuits at 0), SmoothMax/SmoothMin, SmoothClamp, Inverter, Scale, Normalizer, Slider, Selector (inputs Then, Else, Condition), BaseHeight (World Structure constants, Distance on/off), Distance and Axis (curve of distance), CellWallDistance. SimplexNoise2D/3D already matched Hytale (Scale is the period in blocks); string `Seed` values now give different seeds instead of all becoming 0. Phase 22 (18 checks). Math, comparison, logic, override, value, cache and curve-mapper nodes were checked with `probeHytaleDensity` and already behave as documented.
- Keys are matched without regard to case (`Type`/`type`), as are type names.
- **Hytale material providers** (HMaterials): Transparent (no material), Solidity, Queue, SimpleHorizontal (bottom inclusive, top exclusive, optional base heights), Striped (inclusive stripes), Weighted (Seed, SkipChance, per-block deterministic), FieldFunction (later delimiters win), SpaceAndDepth with Condition (`SPACE_ABOVE_FLOOR` and `SPACE_BELOW_CEILING`) and `DEPTH_INTO_FLOOR` / `DEPTH_INTO_CEILING` layers (Constant/Range/Weighted/Noise thickness). The older SpaceAndDepth shape still loads. Phase 23 (12 checks).
- **World Structure** (DensityBiomeMap): a dimension with a WorldStructure object (DefaultBiome, Density, Biomes delimiters {Biome, Min, Max} (also From/To), DefaultTransitionDistance, MaxBiomeEdgeDistance, SpawnPositions, Framework with DecimalConstants and Positions) takes its biome from the density field instead of the Voronoi map, restricted to biomes in the dimension's pool; edges blend over the transition distance (edge distance estimated from the field's slope) and the DistanceFromBiomeEdge density reads it (-1 beyond MaxBiomeEdgeDistance). Verified in game (biomes follow a noise field, terrain generates across boundaries). Phase 25 (10 checks). Assumed: delimiter key names, field sampled at Y 0, first matching delimiter wins.
- **Prop Distribution nodes** (HDistributions): Constant, Positions, Assigned (OverrideAllProps), Union, Anchor (shares Clusters' anchor), Graph, Imported; a biome Props entry takes a PropDistribution pin (plus ContextPrecision) or the older Positions + Assignments form. Phase 26 (10 checks).
- **Graph Generator** conformance: every Hytale pass, selector, action, predicate and supplier is in Hytale's shape; Skip works on all of them; Imported reads Name and ExportAs exports are found even when written later; Spawner uses ClusterBounds; Graph readers: density with TransitionSlope/Offset/Smooth and BackgroundValue, material (nearest node in range, nothing when none), positions, prop distribution. The graph is one infinite field: a cell is generated with a margin (the sum of the graph's caps) and keeps only the nodes it owns, and random choices use a position-derived key rather than node ids, so nodes, jitter and random deletes agree across cell borders. Phase 27 (29 checks). Assumed: the exact weight curve of the Graph density (slope/offset/smooth), and below-1 weights fade into the background.
- **Studio speaks Hytale**: tools/GenHytaleSchema.java turns the Hytale docs into palette entries (205 nodes: every documented node, with documented parameter names and slots, across density, curve, vector, material, pattern, prop, positions, scanner, assignments, prop distribution and the graph families); a Hytale name offers Hytale's parameters. The graph editor reads Hytale files as written (Type, PascalCase keys), edits vectors/bounds/lists as JSON text, shows biome Runtime entries, and a dimension has a graph with the World Structure (density, delimiters, SpawnPositions). SpawnPositions now sets the world spawn (nearest to the origin). Verified in game: a Hytale-form PropDistribution runtime placed poppies and the world structure placed biomes.
- **Props that were left**: PondFiller (Hytale's: fills existing depressions inside Bounds up to their lowest rim, walls from BarrierBlockSet, FillMaterial; the old carving pond stays as legacy), RandomRotator (Rotations list, HorizontalRotations, Seed), Prefab folders (a path naming a folder: the weight picks the folder, then equal chance inside; in a world setup a folder is a name prefix, 	rees = 	rees_oak, 	rees_birch), project prefabs now resolve for biome props, Imported nodes find assets by id. Densities Angle and Terrain (the blended terrain, for material providers). Phase 28 (8 checks). Every documented node name in density, curve, vector, material, prop, positions, scanner and assignments is registered (checked with probeHytaleCoverage).
- **Graph content** (Phase 29): cave tunnels (a Splitter chops an edge into nodes carrying density, read by Graph density), road networks (Positions + ProximityConnector with Cap), landmark points of interest (Spawner clusters read by Graph positions) are all built from graph nodes; SpawnPositions accepts any positions provider, including Graph. Our older bespoke systems (RiverCarver, CaveCarver, spawn anchor, macro landmark graph) remain as ready-made defaults on top of the same capabilities.
- **Context Precision** (Biome / Chunk / Prop) is read and stored; Hytale uses it only to shrink context for speed, results are identical, so the engine always uses the safe Biome-sized context.
- **Placement rules** are our own layer on top (finders, conditions, actions, rule-driven spans). Everything they do can also be written in Hytale terms (Locator/Orienter with Pattern and Scanner, Phase 19/20); they stay as an optional extra.
- **Density shapes and context nodes** (Phase 30): Ellipsoid / Cuboid / Cube / Cylinder in blocks with Scale, NewYAxis, Spin; Rotator (NewYAxis, SpinAngle); Trig (InputScale, clamped); VectorWarp, GradientWarp, FastGradientWarp in Hytale's Inputs form; Switch with SwitchCases and SwitchState; PositionsCellNoise with ReturnType (Distance family, CellValue, Curve, Density), DistanceFunction and MaxDistance; Anchor and CellWallDistance inside a Density cell. Assumed where the docs are silent: warp direction (sample at p + offset), Cylinder's curves multiply, the Distance family is normalised by MaxDistance.
- **Math, comparison and logic densities** (Phase 31, 14 checks): Sum, Max, Min, Abs, Sqrt, Clamp (`WallA`/`WallB` in either order), Mix (gauge clamped to `[0, 1]` in both evaluation and constant-folding), Comparator, Equal (`Epsilon`), ordered comparisons, And (`0` on empty inputs), Or, Not, Nor, Xor.
- **Rivers and caves as graphs** (Phase 32): Hytale V2 builds both from the Graph Generator (its tutorial's Example_Graph_River branches rivers out with the Spawner action; caves are nodes carrying Density on a caves layer, joined by edges the Splitter chops into nodes). A dimension now has iverMode and caveMode (graph or classic), and optional iverGraph / caveGraph (a Graph Generator, edited in the Graph view; the Form view has a Customize-as-nodes button that copies the shipped graph in). River graphs set where rivers run; the river settings (water width, bank width, depth, width variation) and the flush-with-the-land water level still apply. Cave graphs are read like a Graph density on the layer caves and subtracted from the terrain; graph node content is evaluated relative to its node when wrapped in an Anchor node. Also new: the DistanceToGraphEdge density (docs name it, no parameters documented here: ours takes GraphGenerator and MaxDistance). New setups default to graph rivers and caves; older setups keep classic until switched. Shipped graphs: default_river_graph.json (sparse sources, 17 steps each following a smooth flow field, no forks), default_cave_graph.json (a jittered 3D lattice joined by proximity, edges split into overlapping tunnel nodes, junctions become chambers). Assumed: the exact blending Hytale uses for overlapping content (ours: linear falloff for caves), edge length reach for Manual passes. Left: surface cave entrances and deep cathedral halls (classic only), forks/merging for river graphs, props growing in river water near chunk edges (seen once in game).
- Hytale's positional `Inputs` list works for every density node (mapped onto the node's slots), and all Hytale Pattern types (`Constant`, `Offset`, `Floor`, `Ceiling`, `Wall`, `Cuboid`, `And`, `Or`, `Not`, `FieldFunction`) are also registered in `PATTERN_TYPE`.

## Structural gaps (bigger than single nodes)
1. **Prop Runtime context.** Hytale generates earlier stages over a margin big enough for later stages' reach, so props near a chunk edge see the real terrain and each other. Ours reads neighbours from a cache filled only when they happen to be generated, so props clip or see air at borders. Needs deterministic neighbour terrain and evaluation of props whose anchors lie within reach of the chunk; Context Precision (Biome / Chunk / Prop) as in Hytale's docs.
2. **World Structure asset** (DONE, see the done list). Hytale: `DefaultBiome`, a `Density` plus a list of Biome delimiters (value ranges), `DefaultTransitionDistance`, `MaxBiomeEdgeDistance`, `SpawnPositions`, `Framework` (named Positions and DecimalConstants shared across biomes). Ours: Voronoi cells with climate selection (BiCarta) configured from dimension JSON.
3. **Biome owns its content.** Hytale biomes hold terrain Density, MaterialProvider and a list of Prop Runtimes. Our biomes have the same fields, but dimension-level things (placement rules, ores, structures) live outside the biome.
4. **Graph Generator** (node graphs carrying content: roads, rivers, caves, connected structures; passes, node/edge selectors, actions, content layers). Ours has a graph package and a macro feature graph with different shape; spawn, landmarks and points of interest are bespoke sections instead of graph content.
5. **Prop vocabulary.** Hytale's Locator / Orienter / Queue / Mask / Offset / Weighted / PondFiller... with Pattern + Scanner reading real blocks. Ours has same-named props with different parameter shapes (0 of 16 match).

## Order of work
1. Prop Runtime context - DONE.
2. Prop, Pattern, Scanner, Assignments, Positions Provider conformance - MOSTLY DONE (left: RandomRotator details, PondFiller's depression-filling, Prefab folders, Prop Distribution nodes Constant/Positions/Assigned/Union/Anchor/Graph, Imported nodes). Earlier note: Pattern, Scanner, Locator, Orienter and the biome Props form are done. Still to conform: Queue, Mask, Offset, Union, Weighted, StaticRotator, RandomRotator, DensitySelector, Density, Cuboid, Manual, PondFiller, Prefab (props); Weighted/FieldFunction/Sandwich assignments (Seed, SkipChance, WeightedAssignments); Positions providers (TriangularGrid2d takes no parameters in Hytale; Scaler, BaseHeight, Cache, Anchor...). (All done: see Phase 26 and 28.)
3. Density and Material Provider conformance - DONE (Phase 22, 23). Block Mask done (Phase 11/21). Curve and Vector Provider nodes verified against the docs (Phase 24, 24 checks); every Imported node now reads Hytale's `Name` key.
4. World Structure asset and Framework; biome-owned content.
5. Graph Generator conformance - DONE (Phase 27). Next: re-express spawn / landmarks / points of interest / rivers / caves as graph content.
6. Our extras (placement rules, Studio, addons) sit on top and must keep working through the Hytale-shaped core.

## Node differences (generated)

### assignments
| Node | Status | Detail |
|---|---|---|
| Constant | DIFFERS | doc params not found: Prop |
| FieldFunction | DIFFERS | doc params not found: FieldFunction,Delimiters |
| Sandwich | DIFFERS | doc params not found: Delimiters |
| Weighted | DIFFERS | doc params not found: Seed,SkipChance,WeightedAssignments,Assignments |
| Imported | DIFFERS | doc params not found: Name |

### curve
| Node | Status | Detail |
|---|---|---|
| Manual | DIFFERS | doc params not found: Points |
| Imported | DIFFERS | doc params not found: Name |

### density
| Node | Status | Detail |
|---|---|---|
| CellNoise2D | DIFFERS | doc params not found: Jitter,CellType,ScaleX,ScaleZ,Octaves,Seed |
| CellNoise3D | DIFFERS | doc params not found: Jitter,CellType,ScaleX,ScaleY,ScaleZ,Octaves,Seed |
| PositionsCellNoise | DIFFERS | doc params not found: ReturnType,DistanceFunction,Density,DefaultValue,ChoiceDensity,Delimiters,DefaultValue,Curve |
| CellWallDistance | MISSING |  |
| Distance | MISSING | Curve |
| Ellipsoid | DIFFERS | doc params not found: Curve,Scale,NewYAxis,Spin |
| Cube | DIFFERS | doc params not found: Curve |
| Cuboid | DIFFERS | doc params not found: Curve,Scale,Spin,NewYAxis |
| Cylinder | DIFFERS | doc params not found: AxialCurve,RadialCurve,Spin,NewYAxis |
| Axis | MISSING | Axis,Curve,IsAnchored |
| Plane | DIFFERS | doc params not found: PlaneNormal,Curve |
| Shell | DIFFERS | doc params not found: Axis,Mirror,AngleCurve,DistanceCurve |
| Multiplier | MISSING |  |
| SmoothMax | MISSING | Range |
| SmoothMin | MISSING | Range |
| Clamp | DIFFERS | doc params not found: WallA,WallB |
| SmoothClamp | MISSING | WallA,WallB,Range |
| Equal | DIFFERS | doc params not found: Epsilon |
| YSampled | DIFFERS | doc params not found: SampleDistance,SampleOffset,Interpolate |
| Cache | DIFFERS | doc params not found: Capacity |
| Angle | MISSING | Vector,VectorProvider |
| Inverter | MISSING |  |
| Trig | DIFFERS | doc params not found: InputScale |
| Scale | DIFFERS | doc params not found: X,Y,Z |
| Terrain | MISSING |  |
| Rotator | DIFFERS | doc params not found: NewYAxis,SpinAngle |
| Slider | DIFFERS | doc params not found: SlideX,SlideY,SlideZ |
| PositionsPinch | DIFFERS | doc params not found: Positions,PinchCurve,MaxDistance,NormalizeDistance,HorizontalPinch,PositionsMaxY,PositionsMinY |
| PositionsTwist | DIFFERS | doc params not found: Positions,TwistCurve,TwistAxis,MaxDistance,NormalizeDistance,ZeroPositionsY |
| GradientWarp | DIFFERS | doc params not found: SampleRange,WarpFactor,2D,YFor2D |
| FastGradientWarp | DIFFERS | doc params not found: WarpScale,WarpLacunarity,WarpPersistence,WarpOctaves,WarpFactor |
| VectorWarp | DIFFERS | doc params not found: WarpFactor,WarpVector |
| Anchor | DIFFERS | doc params not found: Reverse |
| BaseHeight | MISSING | BaseHeightName,Distance |
| Switch | DIFFERS | doc params not found: SwitchCases |
| SwitchState | DIFFERS | doc params not found: SwitchState |
| Graph | DIFFERS | doc params not found: GraphGenerator,ContentLayer,BackgroundValue,TransitionSlope,TransitionOffset,TransitionSmooth,QueryBufferCapacity |
| Imported | DIFFERS | doc params not found: Name |
| Exported | DIFFERS | doc params not found: SingleInstance |

### material
| Node | Status | Detail |
|---|---|---|
| Solidity | DIFFERS | doc params not found: Empty |
| Queue | DIFFERS | doc params not found: Queue |
| SimpleHorizontal | DIFFERS | doc params not found: TopY,BottomY,Material |
| Striped | DIFFERS | doc params not found: Stripes,Material |
| Weighted | DIFFERS | doc params not found: SkipChance,WeightedMaterials |
| FieldFunction | MISSING | FieldFunction,Delimiters |
| SpaceAndDepth | DIFFERS | doc params not found: LayerContext,MaxExpectedDepth,Condition,Layers |
| Condition | MISSING | ContextToCheck,Value,ContextToCheck,Threshold,ContextToCheck,Threshold,Conditions,Conditions,Condition |
| Layer | DIFFERS | doc params not found: Material,Thickness,Material,RangeMin,RangeMax,Seed,Material,PossibleThicknesses,Seed,Material,ThicknessFunctionXZ |
| Graph | DIFFERS | doc params not found: GraphGenerator,ContentLayer,QueryBufferCapacity |
| Imported | DIFFERS | doc params not found: Name |

### pattern
| Node | Status | Detail |
|---|---|---|
| Constant | MISSING | Value |
| BlockSet | DIFFERS | doc params not found: BlockSet |
| Offset | DIFFERS | doc params not found: Pattern,Offset |
| Rotator | DIFFERS | doc params not found: Pattern,Rotation |
| Floor | MISSING | Floor,Origin |
| Ceiling | DIFFERS | doc params not found: Ceiling,Origin |
| Wall | DIFFERS | doc params not found: Wall,Origin,Directions,RequireAllDirections |
| Surface | DIFFERS | doc params not found: Surface,Medium,SurfaceRadius,MediumRadius,SurfaceGap,MediumGap,Facings,RequireAllFacings |
| Cuboid | DIFFERS | doc params not found: Min,Max,SubPattern |
| Not | DIFFERS | doc params not found: Pattern |
| FieldFunction | MISSING | FieldFunction,Delimiters |
| Imported | DIFFERS | doc params not found: Name |

### positions
| Node | Status | Detail |
|---|---|---|
| Scaler | DIFFERS | doc params not found: Positions,Scale |
| Jitter2d | DIFFERS | doc params not found: Positions,Magnitude,Seed |
| Jitter3d | DIFFERS | doc params not found: Positions,Magnitude,Seed |
| DirectionalJitter | DIFFERS | doc params not found: Positions,Magnitude,Direction,IsBidirectional,Seed |
| Clusters | DIFFERS | doc params not found: Cluster,Distributor,ClusterBounds |
| List | DIFFERS | doc params not found: Positions |
| Anchor | DIFFERS | doc params not found: Positions,Reversed |
| Bound | DIFFERS | doc params not found: Bounds,Positions |
| FieldFunction | DIFFERS | doc params not found: Density,Delimiters,Positions |
| Occurrence | DIFFERS | doc params not found: FieldFunction,Seed,Positions |
| Offset | DIFFERS | doc params not found: Offset,Positions |
| VectorOffset | DIFFERS | doc params not found: Positions,VectorProvider,MovementBounds |
| BaseHeight | MISSING | BaseHeightName,Positions |
| Union | DIFFERS | doc params not found: Positions |
| SimpleHorizontal | DIFFERS | doc params not found: RangeY,Positions |
| Cache | MISSING | SectionSize,CacheSize,Positions |
| Graph | DIFFERS | doc params not found: GraphGenerator,ContentLayer |
| Imported | DIFFERS | doc params not found: Name |

### prop
| Node | Status | Detail |
|---|---|---|
| Cuboid | DIFFERS | doc params not found: Bounds,Material |
| Manual | DIFFERS | doc params not found: Blocks |
| Locator | DIFFERS | doc params not found: Prop,Pattern,Scanner,PlacementCap |
| Orienter | DIFFERS | doc params not found: Prop,Rotations,Pattern,Scanner,SelectionMode,Seed |
| Queue | DIFFERS | doc params not found: Props |
| Mask | DIFFERS | doc params not found: Prop,Mask |
| StaticRotator | DIFFERS | doc params not found: Prop,Rotation |
| RandomRotator | DIFFERS | doc params not found: Prop,Rotations,HorizontalRotations,Seed |
| DensitySelector | DIFFERS | doc params not found: Delimiters,Density |
| Density | DIFFERS | doc params not found: Bounds,Density,Material |
| Prefab | DIFFERS | doc params not found: WeightedPrefabPaths |
| Union | DIFFERS | doc params not found: Props |
| Offset | DIFFERS | doc params not found: Offset,Prop |
| Weighted | DIFFERS | doc params not found: Entries,Seed |
| PondFiller | DIFFERS | doc params not found: Bounds,BarrierBlockSet,FillMaterial |
| Imported | DIFFERS | doc params not found: Name |

### scanner
| Node | Status | Detail |
|---|---|---|
| Linear | DIFFERS | doc params not found: Scanner,Range,Axis |
| Random | DIFFERS | doc params not found: Scanner,Range,Axis,Seed |
| Radial | DIFFERS | doc params not found: Scanner,Bounds |
| Queue | DIFFERS | doc params not found: Scanners |
| Imported | DIFFERS | doc params not found: Name |

### vector
| Node | Status | Detail |
|---|---|---|
| Constant | DIFFERS | doc params not found: Vector |
| Normalizer | DIFFERS | doc params not found: Vector,Magnitude |
| Exported | DIFFERS | doc params not found: SingleInstance,VectorProvider |
| Imported | DIFFERS | doc params not found: Name |

## Phase 33: Hytale's own assets (release schema)

- The released V2 schema differs from the pre-release docs: the real `Assets.zip` (Server/HytaleGenerator, Server/Prefabs) is the ground truth. Curve points are `{ In, Out }`, ranges can be `MinInclusive`/`MaxExclusive`, and there is no Graph Generator in the release.
- A world setup can point at the user's own Hytale install (`hytale` block in the dimension JSON: `assets`, `worldStructure`). Assets are read in place and never copied into this repository.
- Added release nodes: Column, Cluster, Box, directional Prefab, ColumnLinear/ColumnRandom/Area scanners, Mesh2D/3D, Amplitude, Offset, Gradient, Pipeline, Cache2D, YSampled, YOverride/XOverride/ZOverride, Solidity with an Empty side, Imported lookups by family.
- Hytale block names map to Minecraft blocks through `hytale_blocks.json` (exact name, then longest prefix).
- Tested by `verifyPhase33`; `probeHytaleAssets` compiles 201 of 202 real asset files.
- Known gaps: generation speed on full real assets, prefab block rotations, Angle IsAxis, biome transitions.

### Generation speed (Phase 33 follow-up)

Measured on a Hytale world setup (real Assets.zip, Zone1_Plains1) by generating a fresh 16-chunk view radius: about 256 s at first, about 90 s now.
- Terrain columns skip repeated work when a density does not depend on Y (`yIndependent`).
- Chunk snapshots are stored run-length encoded and the tile cache is sized from the real prop reach (it was thrashing, so neighbour terrain was recomputed for every chunk).
- The prop pass works out, per distribution, how far it can reach and only uses biomes actually near the chunk; most chunks no longer load the worst-case 11 x 11 tile neighbourhood.
- The prop context reads through to the terrain tiles and copies only the columns props write.
- Props take the biome from a single sample of the biome-map density (Hytale: a prop belongs to the biome whose range contains it); the edge-blending of props between two biomes is only used with Voronoi biome maps.
- `probeHytaleSpeed` times terrain per real biome with a sampling profile of the slowest.

### Generation speed, round two

Measured with `tools\benchgen.ps1` (wipes the saved chunks, opens the setup, times the first view radius; `-Profile` records a Flight Recorder profile, `tools\jfrtop.ps1` summarises it). The same 33 x 33 chunk view radius that took about 90 s now takes about 20 s, and each chunk uses about 140 ms of CPU instead of about 580 ms.

What changed:
- A chunk's terrain is computed once, as a shared tile (tiles are stored run-length encoded and built straight from runs); the chunk copies it in, and neighbours' props read the same tile. Before, each chunk computed its terrain twice and tiles were evicted and recomputed (3.8 tiles per chunk, now about 1.5). One thread computes a tile while others wait for it. The tile cache is limited by memory, not count.
- Density nodes say whether they depend on Y only, not at all on Y, or not at all on X/Z (`yIndependent`, `xzIndependent`). A Y-only value (a curve of BaseHeight, the gradients Hytale's caves use) is tabulated once for the whole world; a column-constant input is folded in as one number; Mix skips a branch when its blend is 0 or 1 for the whole column. Buffers come from a per-thread pool instead of being allocated per node.
- Materials report how many blocks down a column their answer holds (`runLength`), so a solid or empty run is resolved in one call. `Solidity`'s Empty side (the fluid provider) was never wired in `HMaterials`; it is now.
- The biome map's field is cached per thread (a chunk asks for each column and its four neighbours); prop sampling points are shared across chunks; positions providers presize their lists; block names resolve through a cache.
- The prop pass only considers distributions whose biome is near the chunk and sizes its context from them, reads the terrain tiles lazily, drops writes nobody reads, and skips positions whose prop cannot reach what will be read.

Still costly: Hytale's per-block position grids for small props (each chunk evaluates a ring of positions around itself so props that overlap a border come out identical from both sides), 3D-noise biomes, and the Angle gradient used by surface materials.

### Generation speed, round three (output unchanged)

Every change in this round keeps the generated blocks byte-identical. `tools\samecheck.ps1` hashes every block of 75 chunks in three areas (the `/hash` dev route), and `tools\samecheck_reference.txt` holds the reference those must reproduce. `WG2_COUNTERS=1` makes the game write per-chunk timings to `%TEMP%\wg2_counts.txt`, and `tools\benchgen.ps1` reads them.

- Work per chunk went from about 87 ms to about 57 ms. The biggest single win was removing waiting: chunks blocked on striped locks while another thread computed a neighbour tile. Now each tile is a future; a thread computes every tile it can first, and waits only at the end (36 ms of waiting per chunk became about 4 ms).
- A distribution's positions are cached per 16 x 16 cell and shared by all chunks, keyed by the distribution itself. They are stored pre-sorted and merged in the original order on plain arrays.
- First-stage prop anchors (they read only terrain) are remembered with the box of blocks they read. A chunk replays one only if that box lies inside its own context, where it would have computed exactly the same.
- Terrain tiles: Mix evaluates each input only where it is weighted; gradient windows are cached; material lookup returns the material and its run length in one pass; the slope halo is skipped when no material reads slopes; 2D noises with identical settings share one per-thread memo.
- Fixed: the Positions Cache node keyed sections with a hash that could collide, so a query could get another section's positions (and the result depended on which was cached first).
- Tried, exact, but off by default (they did not pay on Hytale's assets): interval bounds that skip parts of columns whose sign is proven (`WG2_PRUNE=1`), and sharing columns between copies of an exported density (`WG2_SHARE=1`).
- Known: `SimplexNoise2D/3D` without a Seed, and `FastGradientWarp`, draw their seed from a compile-order counter, so two imports of the same node are not the same noise. Hytale hashes the Seed string.

### Distant Horizons

When Distant Horizons is installed, every level generated by Worldgen V2 registers its own DH world generator (`minecraft/compat/DistantHorizonsCompat.java`), the way the "SeedGen" mods do for vanilla. Far chunks come straight from the engine's run-length terrain tiles as DH API chunks: terrain only in DH's Surface mode, with props in Features mode. Minecraft's chunk pipeline is never run for them. In testing this took about 21 ms per far chunk with props, around 140 far chunks a second on DH's six threads. The integration compiles against the DH API (`maven.modrinth:distanthorizonsapi`) and does nothing when DH is absent. To run DH in the dev game, set `WG2_WITH_DH=1` before `tools\dev.ps1 start` (it builds with `-PwithDH`).
