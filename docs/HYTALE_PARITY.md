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
- **Hytale material providers** (HMaterials): Transparent (no material), Solidity, Queue, SimpleHorizontal (bottom inclusive, top exclusive, optional base heights), Striped (inclusive stripes), Weighted (Seed, SkipChance, per-block deterministic), FieldFunction (later delimiters win), SpaceAndDepth with Condition and Constant/Range/Weighted/Noise thickness layers. The older SpaceAndDepth shape still loads. Phase 23 (11 checks). Left: ceiling contexts (the engine only measures the floor), conditions on space below ceiling.
- **World Structure** (DensityBiomeMap): a dimension with a WorldStructure object (DefaultBiome, Density, Biomes delimiters {Biome, Min, Max} (also From/To), DefaultTransitionDistance, MaxBiomeEdgeDistance, Framework with DecimalConstants and Positions) takes its biome from the density field instead of the Voronoi map; edges blend over the transition distance (edge distance estimated from the field's slope) and the DistanceFromBiomeEdge density reads it (-1 beyond MaxBiomeEdgeDistance). Verified in game (biomes follow a noise field, terrain generates across boundaries). Phase 25 (10 checks). Assumed: delimiter key names, field sampled at Y 0, first matching delimiter wins. Left: SpawnPositions, Studio editor for it, biomes only from the dimension's pool.
- **Prop Distribution nodes** (HDistributions): Constant, Positions, Assigned (OverrideAllProps), Union, Anchor (shares Clusters' anchor), Graph, Imported; a biome Props entry takes a PropDistribution pin (plus ContextPrecision) or the older Positions + Assignments form. Phase 26 (10 checks).
- **Graph Generator** conformance: every Hytale pass, selector, action, predicate and supplier is in Hytale's shape; Skip works on all of them; Imported reads Name and ExportAs exports are found even when written later; Spawner uses ClusterBounds; Graph readers: density with TransitionSlope/Offset/Smooth and BackgroundValue, material (nearest node in range, nothing when none), positions, prop distribution. The graph is one infinite field: a cell is generated with a margin (the sum of the graph's caps) and keeps only the nodes it owns, and random choices use a position-derived key rather than node ids, so nodes, jitter and random deletes agree across cell borders. Phase 27 (29 checks). Assumed: the exact weight curve of the Graph density (slope/offset/smooth), and below-1 weights fade into the background.
- **Studio speaks Hytale**: 	ools/GenHytaleSchema.java turns the Hytale docs into palette entries (205 nodes: every documented node, with documented parameter names and slots, across density, curve, vector, material, pattern, prop, positions, scanner, assignments, prop distribution and the graph families); a Hytale name offers Hytale's parameters. The graph editor reads Hytale files as written (Type, PascalCase keys), edits vectors/bounds/lists as JSON text, shows biome Runtime entries, and a dimension has a graph with the World Structure (density, delimiters, SpawnPositions). SpawnPositions now sets the world spawn (nearest to the origin). Verified in game: a Hytale-form PropDistribution runtime placed poppies and the world structure placed biomes.
- Hytale's positional `Inputs` list works for every density node (mapped onto the node's slots).

## Structural gaps (bigger than single nodes)
1. **Prop Runtime context.** Hytale generates earlier stages over a margin big enough for later stages' reach, so props near a chunk edge see the real terrain and each other. Ours reads neighbours from a cache filled only when they happen to be generated, so props clip or see air at borders. Needs deterministic neighbour terrain and evaluation of props whose anchors lie within reach of the chunk; Context Precision (Biome / Chunk / Prop) as in Hytale's docs.
2. **World Structure asset** (DONE, see the done list). Hytale: `DefaultBiome`, a `Density` plus a list of Biome delimiters (value ranges), `DefaultTransitionDistance`, `MaxBiomeEdgeDistance`, `SpawnPositions`, `Framework` (named Positions and DecimalConstants shared across biomes). Ours: Voronoi cells with climate selection (BiCarta) configured from dimension JSON.
3. **Biome owns its content.** Hytale biomes hold terrain Density, MaterialProvider and a list of Prop Runtimes. Our biomes have the same fields, but dimension-level things (placement rules, ores, structures) live outside the biome.
4. **Graph Generator** (node graphs carrying content: roads, rivers, caves, connected structures; passes, node/edge selectors, actions, content layers). Ours has a graph package and a macro feature graph with different shape; spawn, landmarks and points of interest are bespoke sections instead of graph content.
5. **Prop vocabulary.** Hytale's Locator / Orienter / Queue / Mask / Offset / Weighted / PondFiller... with Pattern + Scanner reading real blocks. Ours has same-named props with different parameter shapes (0 of 16 match).

## Order of work
1. Prop Runtime context - DONE.
2. Prop, Pattern, Scanner, Assignments, Positions Provider conformance - MOSTLY DONE (left: RandomRotator details, PondFiller's depression-filling, Prefab folders, Prop Distribution nodes Constant/Positions/Assigned/Union/Anchor/Graph, Imported nodes). Earlier note: Pattern, Scanner, Locator, Orienter and the biome Props form are done. Still to conform: Queue, Mask, Offset, Union, Weighted, StaticRotator, RandomRotator, DensitySelector, Density, Cuboid, Manual, PondFiller, Prefab (props); Weighted/FieldFunction/Sandwich assignments (Seed, SkipChance, WeightedAssignments); Positions providers (TriangularGrid2d takes no parameters in Hytale; Scaler, BaseHeight, Cache, Anchor...). Prop distributions Constant / Positions / Assigned / Union / Anchor / Graph.
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
