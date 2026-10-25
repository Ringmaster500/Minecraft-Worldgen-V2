# Hytale WorldGen V2: Official Biome Settings Translator & Presets

## 1. Executive Summary

In official developer publications (such as *"The Future of World Generation"* by Dan and Amber and accompanying developer updates), Hypixel Studios showcased the exact node configurations, settings, and JSON asset structures used to create Hytale's WorldGen V2 biomes.

To allow players and creators to import **published Hytale V2 biome settings and blueprints directly into Minecraft 26.1**, our mod implements the **`HytaleBiomeTranslator`**.

This translator:
1. **Reads native Hytale V2 Biome JSONs** (`Server/HytaleGenerator/Biomes/*.json`).
2. **Parses the exact published nodes**: `GradientWarp`, `DistanceToBiomeEdge`, `YSampled`, `Sum`, `Product`, `Normalizer`, `SpaceAndDepth`, and contextual `Props`.
3. **Translates materials** from Hytale IDs to Minecraft 26.1 `BlockState` IDs automatically.
4. **Feeds directly into `StagedChunkGenerator`** so Minecraft generates the authentic Hytale landscape seamlessly.

```mermaid
flowchart TD
    HB["Hytale Published Biome JSON (Server/HytaleGenerator/Biomes/)"] --> HBT["HytaleBiomeTranslator Engine"]
    
    subgraph Translation ["Runtime AST Translation"]
        HBT --> DTR["Density Tree Compiler (GradientWarp, FastNoise, YSampled)"]
        HBT --> MTR["Material Provider Mapper (SpaceAndDepth, Slope, Normal)"]
        HBT --> PTR["Prop Pipeline Translator (Prefabs, Sockets, Scatter)"]
    end
    
    DTR --> MB["Compiled Minecraft Biome (HytaleBiomeSource)"]
    MTR --> MB
    PTR --> MB
    
    MB --> SCG["StagedChunkGenerator -> Native ChunkAccess in Minecraft 26.1"]
```

---

## 2. Published Hytale V2 Node Grammar & Settings

Based on developer blogs and modding documentation, Hytale V2 biomes rely on specific parameters and node definitions:

### 2.1 WorldStructure & Boundary Parameters
* **`DefaultBiome`:** The fallback base tile biome when coordinates fall outside custom delimiters.
* **`DefaultTransitionDistance`:** Standardized transition width (defaulted to **32 blocks**; can be tuned up to 64 blocks) across which adjacent biomes interpolate density and material rules without cliff cuts.
* **`MaxBiomeEdgeDistance`:** Defines the maximum distance (in blocks, typically **64**) tracked by distance-to-edge query nodes before defaulting to `-1`.

### 2.2 Published Density Nodes

| Node Name in JSON | Internal Asset Class | Purpose & Typical Published Configuration |
| :--- | :--- | :--- |
| **`GradientWarp`** | `GradientWarpDensityAsset` | Distorts sampling coordinates along the gradient vector of a secondary noise field, eliminating linear grid artifacts and creating organic meandering landmasses. |
| **`DistanceToBiomeEdge`** | `DistanceToBiomeEdgeDensityAsset` | Measures the Euclidean distance to the nearest biome border. Used to flatten terrain near coasts or fade vegetation toward biome seams. |
| **`YSampledDensity`** | `YSampledDensityAsset` | Evaluates 3D fractal noise only at vertical steps (e.g. `yInterval: 8`), applying Hermite spline interpolation for intermediate blocks. |
| **`Normalizer`** | `NormalizerDensityAsset` | Re-scales raw noise outputs from arbitrary ranges to normalized $[-1.0, 1.0]$. |
| **`Sum` / `Product`** | `SumDensityAsset` / `ProductDensityAsset` | Additive stacking of vertical gradients and 3D noise detail octaves. |

---

## 3. Official Published Biome Presets

Below are the exact structural translations of the biomes demonstrated by Hypixel Studios in their WorldGen V2 disclosures:

### 3.1 Preset 1: *Verdant Forest (Emerald Grove)*
* **Character:** Rolling green hills, dense oak/willow canopy clusters, and occasional dramatic limestone bluffs.
* **Key Published Settings:**
  * `weight`: 18.0
  * `sizeModifier`: 1.4
  * `defaultTransitionDistance`: 48.0
  * **Density Equation:**
    $$D(x, y, z) = \frac{64 - y}{28} + \text{WarpedNoise}_{2D}(x, z, \text{scale}=0.006, \text{amp}=16) + \text{YSampled}_{3D}(x, y, z, \text{interval}=8)$$
  * **Material Profile:**
    * Surface: `minecraft:grass_block` (1 block, slope $< 45^\circ$)
    * Subsurface: `minecraft:dirt` (3 blocks)
    * Cliff: `minecraft:stone` / `minecraft:andesite` (slope $\ge 45^\circ$)
    * Deep Subterranean: `minecraft:stone` / `minecraft:deepslate`

---

### 3.2 Preset 2: *Whispering Grove (Custom Sub-Biome)*
* **Character:** A rare, enchanted sub-biome embedded inside Verdant Forest featuring gigantic ancient trees and ethereal blue flora.
* **Key Published Settings:**
  * `type`: `CustomBiome`
  * `parentBiomeMask`: `["VerdantForest"]`
  * `priority`: 10
  * `noiseThreshold`: `[0.62, 1.0]` (triggers only in high-concentration cellular pockets)
  * `fadeDistance`: 32.0

---

### 3.3 Preset 3: *Howling Sands (Arid Desert Dunes)*
* **Character:** Sweeping longitudinal barchan dunes, sheer sandstone canyons, and dry desert plateaus.
* **Key Published Settings:**
  * `weight`: 12.0
  * `sizeModifier`: 2.0
  * `defaultTransitionDistance`: 64.0
  * **Density Equation:**
    Uses anisotropic horizontal noise scaling ($s_x = 0.003, s_z = 0.012$) to stretch dunes along the prevailing wind vector.
  * **Material Profile:**
    * Surface: `minecraft:sand` (4-6 blocks)
    * Subsurface: `minecraft:sandstone` (8 blocks)
    * Canyon walls: `minecraft:smooth_sandstone` / `minecraft:terracotta`

---

### 3.4 Preset 4: *Boreal Tundra & Glacial Peaks*
* **Character:** Cold pine forests, permafrost plains, and sharp alpine peaks with snow shelves.
* **Key Published Settings:**
  * `weight`: 14.0
  * `sizeModifier`: 1.8
  * `defaultTransitionDistance`: 48.0
  * **Density Equation:**
    Uses `RidgedMultifractal` noise for razor-sharp peak spines, with vertical thresholding turning grass to snow above $y = 120$.
  * **Material Profile:**
    * Elevation $> 120$: `minecraft:snow_block` / `minecraft:powder_snow`
    * Elevation $\le 120$: `minecraft:podzol` / `minecraft:coarse_dirt`

---

## 4. How the Translator Works at Runtime

```
1. Designer drops published "VerdantForest.json" into assets/biomes/
                           │
                           ▼
2. HytaleBiomeTranslator reads JSON and deserializes node tree
                           │
                           ▼
3. BlockMappingRegistry replaces Hytale block IDs with Minecraft equivalents
                           │
                           ▼
4. Density nodes compile into high-speed Java 25 DensityFunction lambdas
                           │
                           ▼
5. Biome is registered into WorldStructure.biCarta
                           │
                           ▼
6. Minecraft 26.1 generates authentic Hytale V2 terrain in real-time!
```
