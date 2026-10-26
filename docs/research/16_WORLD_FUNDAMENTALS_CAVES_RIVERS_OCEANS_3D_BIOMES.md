# World Fundamentals: Caves, Rivers, Oceans & 3D Biomes

This document records how Hytale's WorldGen V2 (officially) approaches the "basics" that were missing from our first playtest, and how Worldgen V2 for Minecraft 26.1 implements each one.

## 1. Playtest Findings (Why This Phase Exists)

| Symptom | Root cause |
| :--- | :--- |
| No caves at all | Every biome density was `gradient + small noise`; below Y~55 the gradient dominates so density was strictly positive down to bedrock. Vanilla carvers are bypassed. |
| No oceans | Every tile biome had a base height far above sea level (63). |
| No rivers | Nothing carved below sea level; only `PondFillerProp` produced ponds/lakes. |
| No 3D biomes | `WorldStructure` and `getNoiseBiome` were strictly 2D `(x, z)`. |
| Only 4 biomes reachable | Biome choice was a 1D weight spectrum along one noise field. |

## 2. How Hytale WorldGen V2 Handles It

* **Caves** are *intrinsic features of the density field*, not a post-process: `D = min(D_terrain, -C(x,y,z))` with cheese (big chambers) and spaghetti (tunnels) noise. Floors/ceilings are discovered by the same normal-based material/prop pass as the surface.
* **Rivers** are first-class corridors: a trench function of distance-to-centerline is subtracted from terrain, banks are graded, and riparian `CustomBiomes` own the shoreline.
* **Oceans** come from the two-tier hierarchy: a macro layout of continents separated by ocean zones, and a cellular biome grid inside. Coasts slope smoothly through wide transition corridors.
* **3D biomes**: underground themes are contextual (`Y` band + cavity + the personality of the surface zone above), e.g. lush root caverns beneath forests, glacial caves beneath tundra.

## 3. Our Implementation

### 3.1 Climate-space biome selection (`BiCarta`)
Each `TileBiome` declares a `ClimateTarget(continentalness, temperature, humidity)` in `[-1, 1]`. Voronoi cell points sample three low-frequency fBm fields and pick the nearest target (continentalness axis weighted 1.6x; `weight` acts as rarity bias, distance / sqrt(weight)). Results are memoized per cell. A radial bias keeps the area around the origin on land.

| Continentalness | Meaning |
| :--- | :--- |
| `-0.9` | Deep ocean basins |
| `-0.5` | Oceans / shelf |
| `0.0 .. 0.5` | Coasts, lowlands, forests, deserts, tundra |
| `0.55 .. 0.92` | Highlands and peak interiors |

Beaches are not cells: a composable `MaterialProvider` wrapper paints sand across the sea-level band wherever land meets water.

### 3.2 Volumetric caves (`CaveCarver`)
Applied once per column *after* biome blending (so caves are continuous across biome borders).

* **Continuous Subtraction vs Discontinuous Min**: Uses smooth continuous density subtraction `col[i] -= caveSubtract` rather than `min(D, -cave)`. This eliminates derivative spikes, zero-thickness floating wafer sheets, and sharp collision glitches.
* **Cubic Hermite Interpolation**: Noise is vertically sampled every 2 blocks (`SAMPLE_STEP = 2`) with cubic Hermite smoothstep interpolation, guaranteeing smooth tunnel contours.
* **Walkable Tunnel Sizing**: Spaghetti noise field combined with width modulation yielding 5–10 block wide walkable passages (`radius 0.20f + w * 0.06f`) that prevent 1-block pinching.
* **Deep Dark Cathedral Caverns**: Below Y=-16 ($Y \in [-58, -16]$), dedicated low-frequency cathedral chambers excavate massive 25–40 block tall subterranean halls. Combined with 28x Beardifier negative density excavation in `StagedChunkGenerator` and `WorldGenV2ChunkGenerator`, Ancient Cities generate cleanly inside open cavernous voids without being entombed in solid stone or cut by rock walls.
* **Entrances**: A rare 2D mask widens tunnels so they breach the surface on land (≥ Y66).
* **Protected Bedrock**: Protected 7-block bedrock shell prevents void breaches.
* **Cave-aware surface materials**: Sea flooding only fills air *above the topmost solid block*, keeping caves dry; lava pools in the deepest cave floors ($Y \le -54$). Underground surfaces receive bulk rock rather than sky-exposed grass/sand.

### 3.3 Rivers (`RiverCarver`) & Riparian Valleys
A connected network along the zero-contour of domain-warped 2D noise with dedicated riparian valley engineering:

* **Graded Valley Profile**: Replaced the steep V-trench with a three-tier wide valley:
  - Outer valley corridor (~50–70 blocks wide) with gentle slope ($< 22^\circ$) blending smoothly into surrounding terrain. Surface grass, savanna turf, and forest floors remain intact without triggering cliff-rock exposure.
  - Channel basin (~24–36 blocks wide) sloping into a smooth U-shaped riverbed.
  - Deep core (~12–16 blocks wide) dipping down to bed level ($Y = 57$, 6 blocks below sea level).
* **Dedicated River Material Provider**: Submerged riverbed voxels ($Y \le 63$) receive procedural gravel, sand, and clay beds, while banks ($Y \ge 64$) receive rich riparian soil and grass.
* **Selective Beach Sand**: Inland biomes (savannas, plains, forests, mountains) do not paint beach sand along river channels; sand is reserved for true maritime coasts.
* Fades smoothly across oceans/shallows and through high mountains ($Y \ge 92$), preventing rivers from cutting unnatural gorges through mountain peaks.

### 3.4 Organic Biome Borders (`WorldStructure`)
Voronoi cell boundaries incorporate organic noise dappling (`coherentPatchNoise`):
* When within the transition zone (`blendFactor > 0.10f`), primary and secondary biomes intermingle in natural patches rather than along a rigid geometric Voronoi line.
* Both materials and flora/props are resolved according to the locally dithered biome choice.

### 3.5 True 3D biomes (`UndergroundBiome` / `UndergroundBiomeMap`)
A volumetric zone mask decides whether a point is inside any special volume; a second volumetric selector picks among biomes eligible at that Y and under that *surface biome* (`allowedSurfaceIds`). `getNoiseBiome(x, y, z)` resolves underground volumes first, then rivers, then the surface cell.

| Underground biome | Y band | Surface requirement | Floor / ceiling |
| :--- | :--- | :--- | :--- |
| Lush Caves | -24 .. 50 | humid land | moss / moss |
| Dripstone Caves | -30 .. 50 | any land | dripstone / dripstone |
| Deep Dark | -60 .. -26 | any | sculk / sculk |
| Mushroom Grotto | -40 .. 18 | any | mycelium / - |
| Frozen Caverns | -10 .. 50 | cold land | snow / packed ice |

Each is presented as the matching vanilla biome so vanilla decoration features, colors and spawns apply on top of our floor/ceiling pass.

## 4. The 39 Default Biomes

* **Oceans & islands (8):** Deep Ocean, Deep Frozen Ocean, Warm Ocean, Lukewarm Ocean, Ocean, Cold Ocean, Frozen Ocean, Mushroom Island
* **Hot / arid (3):** Arid Dunes, Badlands, Savanna
* **Wet / tropical (3):** Jungle, Swamp, Mangrove Swamp
* **Temperate (7):** Temperate Forest, Sunlit Plains, Flower Forest, Birch Forest, Dark Forest, Meadow, Cherry Grove
* **Cold (4):** Taiga, Snowy Plains, Snowy Taiga, Grove
* **Highlands & peaks (5):** Windswept Hills, Alpine Peaks, Stony Peaks, Jagged Peaks, Frozen Peaks
* **Overlays (2):** Old Growth Grove, Ice Spikes
* **Rivers (2):** River, Frozen River
* **Underground (5):** Lush Caves, Dripstone Caves, Deep Dark, Mushroom Grotto, Frozen Caverns

Definitions live in `DefaultBiomeCatalog` (ids, names, vanilla presentation) and `DefaultBiomeFactory` (compact `Spec` -> density DAG + strata + flora).

## 5. Known Gaps / Next Steps
See Phase 7 in [`ROADMAP_AND_PROGRESS.md`](../ROADMAP_AND_PROGRESS.md): ravines & aquifers, riparian shoreline biome, ocean props (kelp / coral), downhill-monotonic rivers & deltas, custom cave props, and an in-game tuning pass over heights and transition widths.
