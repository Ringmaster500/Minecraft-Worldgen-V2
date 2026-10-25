# Hytale WorldGen V2: Density Functions & 3D Volumetric Terrain Mathematics

## 1. The Volumetric Density Paradigm

Unlike traditional voxel engines that rely on 2D heightmaps (which can only store a single $y$ height value per $(x, z)$ coordinate and thus cannot produce true 3D overhangs, arches, or hollow caverns natively), Hytale WorldGen V2 is built entirely upon a **continuous 3D scalar density field**:

$$D(x, y, z): \mathbb{R}^3 \to [-1.0, 1.0]$$

### 1.1 The Isosurface Convention
* **$D(x, y, z) > 0.0$:** Solid Matter (bedrock, stone, dirt, ice).
* **$D(x, y, z) < 0.0$:** Negative Space / Air (atmosphere, cave caverns, chasms, ocean basins).
* **$D(x, y, z) = 0.0$:** The Exact Surface Boundary (the zero-crossing isosurface).

```
Visualizing Density Sign Convention:
         y (Height)
         ▲
         │       D = -0.8 (Air / Sky)
         │       D = -0.3 (Air)
~~~~~~~~~┼─────────────────────────── D = 0.0 (Terrain Surface / Isosurface)
         │       D = +0.2 (Subsurface Dirt)
         │       D = +0.9 (Solid Stone Bedrock)
         │       D = -0.6 (Underground Cave Void)
         │       D = +0.7 (Cave Floor Stone)
─────────┴───────────────────────────► x / z
```

---

## 2. Complete Catalog of V2 Density Nodes

In Hytale's built-in generator engine (`com.hypixel.hytale.builtin.hytalegenerator.density` and `fields`), every density function is a modular node that can be composed into arbitrary directed graphs.

### 2.1 Primitive Math & Transform Nodes

| Class Name | Mathematical Definition | Purpose & Typical Use |
| :--- | :--- | :--- |
| **`ConstantDensity`** | $f(x, y, z) = c$ | Baseline bias, fixed offset values, solid floor constants. |
| **`AbsDensity`** | $f(x, y, z) = \|g(x, y, z)\|$ | Produces symmetrical pinching, sharp ridged fracture planes. |
| **`ClampDensity`** | $f(x, y, z) = \text{clamp}(g, \min, \max)$ | Constrains values to legal range $[-1, 1]$ or creates flat plateau shelves. |
| **`InvertDensity`** | $f(x, y, z) = -g(x, y, z)$ | Inverts solid/void; turns mountains into pits and caves into solid pillars. |
| **`NormalizerDensity`** | $f(x, y, z) = \frac{g - \text{min}}{\text{max} - \text{min}} \cdot 2 - 1$ | Re-scales raw arbitrary noise ranges back to canonical $[-1, 1]$. |
| **`OffsetDensity`** | $f(x, y, z) = g(x + \Delta x, y + \Delta y, z + \Delta z)$ | Domain warping, shifting coordinates, phase alteration. |
| **`ScaleDensity`** | $f(x, y, z) = g(x \cdot s_x, y \cdot s_y, z \cdot s_z)$ | Adjusts spatial frequency / wavelength across dimensions independently. |

---

### 2.2 Combinator & Aggregator Nodes

| Class Name | Mathematical Definition | Purpose & Typical Use |
| :--- | :--- | :--- |
| **`SumDensity`** | $f(x, y, z) = \sum_{i=1}^n g_i(x, y, z)$ | Additive multi-octave terrain shaping; adding detail noise onto base slope. |
| **`ProductDensity`** | $f(x, y, z) = \prod_{i=1}^n g_i(x, y, z)$ | Masking, gating features, dampening noise in specific elevation bands. |
| **`MinDensity`** | $f(x, y, z) = \min(g_1, g_2)$ | **Boolean Union of Air**: Carves negative space; cuts tunnels and trenches. |
| **`MaxDensity`** | $f(x, y, z) = \max(g_1, g_2)$ | **Boolean Union of Solid**: Merges solid structures, floating islands, peaks. |

---

### 2.3 Noise Generator Nodes (FastNoiseLite Integration)

Hytale V2 embeds optimized FastNoise algorithms supporting SIMD and AVX-512 extensions:

* **`Noise2dDensity`:**
  * Evaluates noise solely on horizontal plane $(x, z)$ and projects vertically.
  * Used for: Continental elevation baselines, regional hill contours, climate moisture, river vectors.
* **`Noise3dDensity`:**
  * Evaluates full 3-dimensional noise field $N(x, y, z)$.
  * Noise types:
    * **Simplex / OpenSimplex2:** High-quality organic rolling terrain without directional artifacts.
    * **Cellular / Worley:** Crystal formations, cavern chambers, Voronoi fracture networks.
    * **Fractal Modes:**
      * *FBM (Fractal Brownian Motion):* Standard rough landscape surfaces.
      * *Ridged Multifractal:* Razor-sharp alpine mountain spines and deep canyon ravines.
      * *PingPong:* Sinuous wave forms, undulating strata layers.

---

### 2.4 The `YSampledDensity` Optimization Engine

Evaluating 3D fractal noise at every single block in a chunk requires tens of thousands of trigonometric and hashing evaluations per chunk. 

To achieve blistering performance, V2 employs **`YSampledDensity`**:

```
Vertical Subsampling Grid:
Y = 384 ───●───────────● (Evaluated Noise Sample)
           │           │
           │  Hermite  │ (Interpolated with zero noise calls)
           │  Spline   │
Y = 376 ───●───────────● (Evaluated Noise Sample)
           │           │
Y = 368 ───●───────────● (Evaluated Noise Sample)
```

1. **Cell Step Sampling:** The generator samples the full complex 3D density graph only at horizontal intervals $(\Delta x = 4, \Delta z = 4)$ and vertical intervals $(\Delta y = 8)$.
2. **Trilinear / Cubic Interpolation:** For all internal blocks inside the $(4 \times 8 \times 4)$ cell, density values are computed using hardware-accelerated trilinear or cubic Hermite spline interpolation:
   $$D(x, y, z) = \text{Lerp}\left(D_{000}, D_{100}, \dots, u, v, w\right)$$
3. **Adaptive Sampling:** For regions near critical features or sheer cliff boundaries, the sampler can dynamically increase sampling density without sacrificing performance across uniform sky or deep underground rock.

---

## 3. Sculpting Real-World Geographies via Density Equations

### 3.1 Base Terrain Gradient (The Gravity Term)
To produce a terrestrial planet where stone is at the bottom and sky is at the top, every biome includes a foundational vertical gradient:

$$G(y) = \frac{y_{\text{target}} - y}{H_{\text{scale}}}$$

* When $y \ll y_{\text{target}}$, $G(y) \gg 0$ (solid stone).
* When $y \gg y_{\text{target}}$, $G(y) \ll 0$ (open sky).
* When $y = y_{\text{target}}$, $G(y) = 0$ (the baseline surface).

Composite terrain density is formed by adding 3D noise onto the base gradient:
$$D_{\text{terrain}}(x, y, z) = G(y) + \alpha \cdot N_{3D}(x, y, z)$$

### 3.2 True 3D Overhangs & Arches
Because $N_{3D}(x, y, z)$ varies with height $y$, wherever a large positive noise pocket occurs at an elevation *above* a negative noise pocket:
$$D(x, y_1, z) > 0 \quad \text{while} \quad D(x, y_0, z) < 0 \quad (y_1 > y_0)$$
The engine naturally generates a soaring stone arch or dramatic cliff overhang with zero special-casing!

```
Arch Profile:
               ┌─────────────────────┐  <-- y1: Positive Noise Pocket (Solid Roof)
               │    Solid Stone      │
            ┌──┴─────────────────────┴──┐
            │   Open Air Tunnel / Arch  │  <-- y0: Negative Noise Pocket (Cavity)
────────────┴───────────────────────────┴─────────── Ground Level
```

### 3.3 Seamless Cave Carving via Density Substraction
In legacy V1 (and Minecraft prior to 1.18), caves were carved using separate "worm" algorithms that punched spheres out of finished terrain. This created awkward intersections, floating liquid glitches, and unnatural cutoffs.

In V2, caves are **intrinsic features of the density field**:
* Let $C(x, y, z) = 1.0 - |N_{\text{cave}}(x, y, z)| - \text{threshold}$.
* When combined via `MinDensity`:
  $$D_{\text{world}}(x, y, z) = \min\left(D_{\text{terrain}}(x, y, z), -C(x, y, z)\right)$$
* Caves emerge organically through the bedrock, naturally widening into massive caverns where the noise is strong, narrowing into tunnels, and smoothly breaking out into surface canyon entrances.

---

## 4. Implementation Blueprint for Minecraft 26.1

In Minecraft Java 26.1:
1. Minecraft's built-in `DensityFunction` framework has rigid class abstractions that introduce heavy boxing and virtual method call overhead.
2. Our Hytale V2 engine will implement a dedicated high-performance **Primitive Float Density Engine** (`HytaleDensityFunction`), where coordinates and values are evaluated as raw `float` or `double` primitives.
3. Utilize SIMD / Vector API (`jdk.incubator.vector` or AVX unrolled math) to evaluate 8 or 16 voxel densities simultaneously along chunk column vectors.
