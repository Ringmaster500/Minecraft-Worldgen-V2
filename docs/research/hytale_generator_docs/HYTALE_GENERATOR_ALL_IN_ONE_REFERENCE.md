# Hytale Generator Complete Official Reference (All-In-One)

> This document compiles all 24 official documentation categories from `https://docs.hytale.com/hytale-generator/` into a single, searchable master reference.

---

[TOC]

---


# ============================================================================
# SECTION: OVERVIEW & CORE
# ============================================================================



======================================================================
## Hytale Generator Overview
Official URL: https://docs.hytale.com/hytale-generator/
======================================================================

# Hytale Generator

> Source: [https://docs.hytale.com/hytale-generator/](https://docs.hytale.com/hytale-generator/)

**Description:** Node reference for the Hytale world generator.

---

Reference for the node types in the Hytale world generator. Each page below lists the nodes in one category with their parameters.

- [Density](/hytale-generator/density/): defines a 3D decimal value field.
- [Curve](/hytale-generator/curve/): maps decimal values to other decimal values.
- [Vector Provider](/hytale-generator/vector-provider/): defines a 3D decimal vector.
- [Material](/hytale-generator/material/): defines a block Material.
- [Material Provider](/hytale-generator/material-provider/): determines which Material to use where.
- [Block Mask](/hytale-generator/block-mask/): determines which Materials can replace which other Materials.
- [Positions Provider](/hytale-generator/positions-provider/): determines an infinite 3D positions field.
- [Scanner](/hytale-generator/scanner/): scans a local part of the world for valid positions.
- [Pattern](/hytale-generator/pattern/): validates a world location by its Material composition and other criteria.
- [Assignments](/hytale-generator/assignments/): assigns Props to each position in a Positions field.
- [Prop](/hytale-generator/prop/): generates localized content.
- [Prop Distribution](/hytale-generator/prop-distribution/): determines a field of Positioned Props.
- [Prop Runtime](/hytale-generator/prop-runtime/): shares context between Props and controls how precisely that context is calculated.
- [World Structure](/hytale-generator/world-structure/): defines the contents of an entire world.
- [Graph Generator](/hytale-generator/graph-generator/graph-generator/): generates a field of interconnected nodes that carry content.



======================================================================
## World Structure
Official URL: https://docs.hytale.com/hytale-generator/world-structure/
======================================================================

# World Structure

> Source: [https://docs.hytale.com/hytale-generator/world-structure/](https://docs.hytale.com/hytale-generator/world-structure/)

**Description:** Defines the contents of an entire world.

---

The World Structure asset defines how every aspect of a world is generated. Below are all entries for this asset.

### [DefaultBiome](#defaultbiome)

- Type: String
- Safe value: `"Basic"`

This Biome is used everywhere outside the delimiters.

### [Density](#density)

- Type: Density slot
- Safe value:




  ```
  "Type": "Constant",
  "Value": 1
  ```

Determines the Biome distribution in the world. The values are interpreted by the `Biomes` delimiter list.

### [Biomes](#biomes)

- Type: list of Biome delimiters
- Safe value: `[]`

Each delimiter assigns a Biome to places in the world where the Density is within its bounds.

### [DefaultTransitionDistance](#defaulttransitiondistance)

- Type: integer
- Safe value: `32`

The distance, in blocks, it takes for one Biome to transition into another. The terrain in that region is interpolated between the two Biomes. Larger distances impact performance.

### [MaxBiomeEdgeDistance](#maxbiomeedgedistance)

- Type: integer
- Safe value: `0`

The maximum distance from any Biome edge that world-gen tracks. This impacts Biomes that use the DistanceFromBiomeEdge Density node. Distances greater than this are read as `-1`.

### [SpawnPositions](#spawnpositions)

- Type: PositionProvider slot
- Safe value:




  ```
  "Type": "List",
  "Positions": [{"X": 0, "Y": 140, "Z": 0}]
  ```

The world's spawn positions.

### [Framework](#framework)

- Type: list of Framework modules
- Safe value: `[]`

The Framework helps define the world's high-level structure. It ensures consistency across multiple Biomes. For this to work, the Biomes in a World Structure must correctly interpret the Framework entries.

```
// Example of a DecimalConstants Framework entry.
"Framework": [
  {
    "Type": "DecimalConstants",
    "Entries": [
      {
        "Name": "Base",
        "Value": 100
      }
    ]
  }
]
```

Below are the types of Framework entries.

#### [Positions](#positions)

A list of named Positions fields for the world.

- Entries: list of entries


  - Name: string. The name to use when referencing this entry from inside the Biomes.
  - Positions: PositionProvider slot.

#### [DecimalConstants](#decimalconstants)

A list of named decimal constants.

- Entries: list of entries


  - Name: string. The constant's name.
  - Value: decimal number.



# ============================================================================
# SECTION: VOLUMETRIC TERRAIN & MATH
# ============================================================================



======================================================================
## Density (3D Fields & Noise)
Official URL: https://docs.hytale.com/hytale-generator/density/
======================================================================

# Density

> Source: [https://docs.hytale.com/hytale-generator/density/](https://docs.hytale.com/hytale-generator/density/)

**Description:** Defines a 3D decimal value field.

---

### [Constant](#constant)

Outputs a constant value.

Expected Inputs: `0`

Parameters:

- Value: decimal number.

---

### [SimplexNoise2D](#simplexnoise2d)

Outputs a value in the range `[-1, 1]` from a 2D Simplex noise field that varies on the x/z plane. For more details on how simplex noise works and what every parameter does to the output you can visit [this page](https://cmaher.github.io/posts/working-with-simplex-noise/).

This node automatically caches the value per x/z column.

Expected Inputs: `0`

Parameters:

- Lacunarity: positive decimal number. Impacts the scale of each consecutive octave. A good starting value would be `2.0`, which results in each consecutive octave having details that are 2x smaller than the previous. Higher values result in grainier noise.
- Persistence: positive decimal number. Multiplies the intensity of each consecutive octave. A good starting value would be `0.5`, which results in each consecutive octave being half as strong as the previous.
- Scale: positive decimal number. Represents the field’s period distance in blocks. Greater values stretch the noise field outward. A good starting value would be `50`.
- Octaves: integer greater than `0`. Greater values result in more detail. A good starting value would be `4`. Requiring values over `10` are uncommon.
- Seed: string.

---

### [SimplexNoise3D](#simplexnoise3d)

Outputs a value in the range `[-1, 1]` from a 3D Simplex noise field that varies on the x/y/z space. For more details on how simplex noise works and what every parameter does to the output you can visit [this page](https://cmaher.github.io/posts/working-with-simplex-noise/).

Expected Inputs: `0`

Parameters:

- Lacunarity: positive decimal number. Impacts the scale of each consecutive octave. A good starting value would be `2.0`, which results in each consecutive octave having details that are 2x smaller than the previous. Higher values result in grainier noise.
- Persistence: positive decimal number. Multiplies the intensity of each consecutive octave. A good starting value would be `0.5`, which results in each consecutive octave being half as strong as the previous.
- ScaleXZ: positive decimal number. Represents the field’s period distance in blocks on the horizontal XZ plane. Greater values stretch the noise field outward. A good starting value would be `50`.
- ScaleY: positive decimal number. Represents the field’s period distance in blocks on the vertical Y axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- Octaves: integer greater than `0`. Greater values result in more detail. A good starting value would be `4`. Requiring values over `10` are uncommon.
- Seed: string.

---

### [WhiteNoise](#whitenoise)

Outputs a value in the range `[-1, 1]` from a 3D white noise field that varies on the x/y/z space.

Expected Inputs: `0`

Parameters:

- Seed: a string.

---

### [CellNoise2D](#cellnoise2d)

Outputs a value in the range `[-1, 1]` from a 2D Voronoi cell noise field that varies on the x/z plane.

Expected Inputs: `0`

Parameters:

- Jitter: decimal number. Moves each cell center away from a regular grid. A good starting value would be `0.3`. A value of `0` keeps the cells on a grid. Greater values make the cells more irregular.
- CellType: one of CellValue, Distance, Distance2, Distance2Add, Distance2Sub, Distance2Mul or Distance2Div. The name is case sensitive. CellValue gives a constant value inside each cell. The Distance types use the distance to the closest cell centers.
- ScaleX: positive decimal number. Represents the field’s period distance in blocks on the X axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- ScaleZ: positive decimal number. Represents the field’s period distance in blocks on the Z axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- Octaves: integer greater than `0`. Greater values result in more detail. A good starting value would be `1`.
- Seed: a string.

---

### [CellNoise3D](#cellnoise3d)

Outputs a value in the range `[-1, 1]` from a 3D Voronoi cell noise field that varies on the x/y/z space.

Expected Inputs: `0`

Parameters:

- Jitter: decimal number. Moves each cell center away from a regular grid. A good starting value would be `0.3`. A value of `0` keeps the cells on a grid. Greater values make the cells more irregular.
- CellType: one of CellValue, Distance, Distance2, Distance2Add, Distance2Sub, Distance2Mul or Distance2Div. The name is case sensitive. CellValue gives a constant value inside each cell. The Distance types use the distance to the closest cell centers. These types match the traditional ReturnTypes documented under PositionsCellNoise.
- ScaleX: positive decimal number. Represents the field’s period distance in blocks on the X axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- ScaleY: positive decimal number. Represents the field’s period distance in blocks on the Y axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- ScaleZ: positive decimal number. Represents the field’s period distance in blocks on the Z axis. Greater values stretch the noise field outward. A good starting value would be `50`.
- Octaves: integer greater than `0`. Greater values result in more detail. A good starting value would be `1`.
- Seed: a string.

---

### [PositionsCellNoise](#positionscellnoise)

Produces a 2D/3D density field in which the value is determined by each coordinate’s distance from a field of Positions. You can use this asset to generate advanced Cell noise with control over the exact placement of the Cell’s through the Positions asset. You can also configure how the distance is interpreted to produce the output. You can use the traditional Cell noise ReturnTypes or create your own curves. You can also sample the Cell value from another Density field asset.

Parameters:

- Positions: Positions slot. This provides the Positions of each cell’s core.
- ReturnType: ReturnType slot. This determines how the distance from each Position point is interpreted to produce the density field.
- DistanceFunction: DistanceFunction slot. This determines how the distance is calculated.
- MaxDistance: decimal value. The maximum distance around each Position that it has an effect on the Density field. A good starting value is putting it a bit over half the distance between two of your Position points. Greater values have a greater impact on performance.

#### [ReturnType](#returntype)

Determines how each Position point affects the Densty field around it, or how the Cell looks.

Below are the different types you can use.

**CellValue**

The Density value is constant inside each cell and is sampled from the provided Densty field.

The images below show a 2D and a 3D output of the CellValue return type.

Parameters:

- Density: Density asset slot. The Density value of each cell is sampled from this field at the cell’s core Position point.
- DefaultValue: decimal number. This value is used outside of cells. This can happen when the field’s MaxDistance value is smaller than the distance between some Positions.

**Density**

The cell is populated with a Density field. The Density field is picked from a list of Delimiters. The Delimiters are picked if the ChoiceDensity field is within their range. The ChoiceDensity field’s value is sampled at the origin position of the cell.

Below is a screenshot showing a simple example of how you can anchor any Density field you design to the origin of the cell using the Anchor Density node. Below the screenshot you can see the configuration I used for that.

Parameters:

- ChoiceDensity: Density asset slot. *Benefits from a Cache Density.* Picks the delimiter to use for a Cell. This field is sampled at each Cell's origin.
- Delimiters: list of delimiters. Assign Density fields to cells based on the ChoiceDensity value at the Cell's origin.
- DefaultValue: decimal number. This value is used outside of cells. This can happen when the field’s MaxDistance value is smaller than the distance between some Positions.

**Curve**

The Curve ReturnType allows you to use a Curve asset to define the value of the Density field in terms of its distance from the Positions.

Parameters:

- Curve: Curve asset slot. The Density value of each cell is defined by the curve. The curve’s input is the distance in blocks between the closest Position, and the Curve’s output becomes the field’s value.

**Distance**

The Distance ReturnType works like the traditional CellNoise’s “Distance” return type.

It has no parameters.

**Distance2**

The Distance2 ReturnType works like the traditional CellNoise’s “Distance2” return type.

It has no parameters.

**Distance2Add**

The Distance2Add ReturnType works like the traditional CellNoise's `Distance2Add` return type.

It has no parameters.

**Distance2Sub**

The Distance2Sub ReturnType works like the traditional CellNoise's `Distance2Sub` return type.

It has no parameters.

**Distance2Mul**

The Distance2Mul ReturnType works like the traditional CellNoise's `Distance2Mul` return type.

It has no parameters.

**Distance2Div**

The Distance2Div ReturnType works like the traditional CellNoise's `Distance2Div` return type.

It has no parameters.

#### [DistanceFunction](#distancefunction)

Determines the way in which the distance to the closest Position is calculated. Currently there are two types: Euclidean and Manhattan.

No Parameters.

---

### [CellWallDistance](#cellwalldistance)

Outputs the distance to the nearest cell wall in blocks. The value is `0` at the wall and greater toward the cell center.

This should only be used inside a PositionsCellNoise node that uses the Density ReturnType. Outside of that context the output is the largest decimal number.

Expected Inputs: `0`

Parameters: None

---

### [Distance](#distance)

The Distance outputs a value in function of the distance from the origin `{0, 0, 0}` of the field. The provided Curve asset maps the distance to the output Density value.

Expected Inputs: `0`

Parameters:

- Curve: Curve asset slot. Maps the Distance from the origin to a Density value.

---

### [Ellipsoid](#ellipsoid)

The Ellipsoid is a deformed Sphere. You can use a Scale vector to stretch and compress the Sphere in different directions. You can spin it by giving it a new Y axis and a spin angle (in degrees).

The order of deformations done to the Sphere is as follows:

1. Stretch and compress using the Scale vector.
2. Align its Y axis to the provided one.
3. Spin it around the new Y axis.

Below is an example Ellipsoid and its assets.

Expected Inputs: `0`

Parameters:

- Curve: Curve asset slot. Maps the Distance from the origin to a Density value.
- Scale: 3D vector asset slot. Determines how much to stretch the Sphere in each direction.
- NewYAxis: 3D vector asset slot. Let’s you rotate the field around its origin.
- Spin: decimal number. Determines the angle in degrees by which to spin the Ellipsoid around its Y axis.

---

### [Cube](#cube)

The Cube outputs a Density value in function of the distance from the origin axis of the field. The provided Curve asset maps the distance to the output Density value.

Expected Inputs: `0`

Parameters:

- Curve: Curve asset slot. Maps the Distance from the origin to a Density value.

---

### [Cuboid](#cuboid)

The Cuboid is a deformed Cube. You can use a Scale vector to stretch and compress the Cube in different directions. You can spin it by giving it a new Y axis and a spin angle (in degrees).

The order of deformations done to the Cube is as follows:

1. Stretch and compress using the Scale vector.
2. Align its Y axis to the provided one.
3. Spin it around the new Y axis.

Below is an example cuboid and its assets.

Expected Inputs: `0`

Parameters:

- Curve: Curve asset slot. Maps the Distance from the origin to a Density value.
- Scale: 3D vector asset slot. Determines how much to stretch the Cube in each direction.
- Spin: decimal number. Determines the angle in degrees by which to spin the Ellipsoid around its Y axis.
- NewYAxis: Point3D asset slot. Let’s you rotate the field around its origin.

---

### [Cylinder](#cylinder)

The Cylinder generates a cylindrical shape, crazy I know. You can spin it by giving it a new Y axis and a spin angle (in degrees).

The AxialCurve determines the Density value along its Y axis (not the world’s Y axis). The AxialCurve takes positive inputs anywhere above the Cylinder’s XZ-plane and it takes negative values anywhere below it.

The RadialCurve determines the Density value in function of the distance from the Y axis. This basically can determine how thick the Cylinder is.

Below is an example cuboid and its assets.

Expected Inputs: `0`

Parameters:

- AxialCurve: Curve asset slot. Maps the Distance from the Cylinder’s XZ-plane to a Density value.
- RadialCurve: Curve asset slot. Maps the Distance from the Cylinder’s Y axis to a Density value.
- Spin: decimal number. Determines the angle in degrees by which to spin the Ellipsoid around its original Y axis.
- NewYAxis: Point3D asset slot. In combination with the spin, this lets you rotate the Cylinder around its origin by aligning its original Y-axis with the provided one.

---

### [Axis](#axis)

The Axis outputs a Density value in function of the distance from a provided Axis line that passes through the origin `{0, 0, 0}` of the field or through the anchor point if the IsAnchored flag is `true`. The provided Curve asset maps the distance to the output Density value.

Expected Inputs: `0`

Parameters:

- Axis: 3D vector asset slot. Determines the axis from which the distance is used.
- Curve: curve asset slot. Determines the Density value at any distance from the axis.
- IsAnchored: boolean. If `true`, the Axis node will anchor itself to the closest anchor. Although you could use an Anchor node to anchor an Axis Density node, this internal method doesn’t produce some artifacts.

---

### [Plane](#plane)

The Plane Density node allows you to define a Density field in function of the distance from a user-defined plane that passes through the field’s origin `{0, 0, 0}` or through the anchor if the flag `IsAnchored` is enabled. You can granularly define the Density value using a Curve asset. The PlaneNormal vector determines the direction the plane is facing.

Expected Inputs: `0`

Parameters:

- PlaneNormal: 3D vector asset slot. Determines the direction the plane is facing.
- Curve: curve asset slot. Determines the Density value at any distance from the plane.

---

### [Shell](#shell)

The Shell Density node allows you to define regions of the shell around the origin `{0, 0, 0}` of the field based on the direction and the distance from origin. The angles, thickness and Density values of the shell are fully configurable through Curve assets.

The diagram below shows the anatomy of the Shell node and how its AngleCurve and its DistanceCurve are multiplied to obtain the result in the screenshot.

Expected Inputs: `0`

Parameters:

- Axis: 3D vector asset slot. Determines the axis against which the angle is calculated.
- Mirror: boolean value. If `true` then the angle is mirrored in both directions of the axis.
- AngleCurve: curve asset slot. Determines the Density value at any given angle from the axis.
- DistanceCurve: curve asset slot. Determines the Density value at any distance from the origin.

---

### [CurveMapper](#curvemapper)

This node maps the input to a Curve.

Expected Inputs:

- `0`: Density to remap.

Parameters:

- Curve: curve. Maps the input Density value.

---

### [Mix](#mix)

Mixes two inputs. How much of each input to use in the mix is gauged by a third input called the gauge:

- Where the Gauge is less or equal to `0.0`: only Density A is used.
- Where the Gauge is more or equal to `1.0`: only Density B is used.
- Where the Gauge is between `0.0` and `1.0`: Density A and B are mixed proportional to the Gauge.

Expected Inputs: `3`

- `0`: Density A.
- `1`: Density B.
- `2`: Gauge.

Parameters: None

---

### [MultiMix](#multimix)

Mixes multiple inputs. How much of each input to use in the mix is gauged by the last input called the Gauge.

The inputs are ordered top-down (in the node editor) and each input is assigned an index: starting with `0` for the top one and incrementing downwards.

The keys let you pin the different inputs to Gauge values. Where the Gauge input's value approaches a Key's value, the input referenced by that Key is stronger in the final result.

Expected Inputs: unlimimied, the last one is the Gauge.

Parameters: None

---

### [Sum](#sum)

The output is the sum of all the inputs.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Multiplier](#multiplier)

The output is the multiplication of all the inputs.

The Multiplier node skips the remaining inputs after an input provides a Density value of 0. This can help you optimize your Density performance by ordering the Multiplier inputs with the cheapest mask first.

The image below is an example where I put the edge's shell first in the Multiplier's input list, and the noise second. I configured the shell to be `0` where I want to mask it out. As a result, the noise (more expensive than the shell) is not calculated for large volumes of space where the shell mask is `0`, and I got a performance improvement of ~40%. This is because around that percentage of the volume in that setup was masked out by the shell.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Max](#max)

The output is the greatest value of all the inputs. If no inputs are provided it is 0.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Min](#min)

The output is the smallest value of all the inputs. If no inputs are provided it is 0.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [SmoothMax](#smoothmax)

The output is a smoothed maximum between the two inputs. The diagram below compares a smooth maximum and a normal maximum of functions f and g. The normal maximum has sharp angles where f and g cross.

Expected Inputs: `2`

Parameters:

- Range: positive decimal number. Greater values result in more smoothing. A good starter value could be 0.2.

---

### [SmoothMin](#smoothmin)

The output is a smoothed minimum between the two inputs. This node works the same as SmoothMax, please read that node’s description.

Expected Inputs: `2`

Parameters:

- Range: positive decimal number. Greater values result in more smoothing. A good starter value could be 0.2.

---

### [Clamp](#clamp)

This node ensures that the output is within the provided range which is defined by the two wall parameters. Input values that are inside the range don’t change and inputs values outside the range are brought back to the range’s extreme.

Expected Inputs: `1`

Parameters:

- WallA: any decimal number.
- WallB: any decimal number.

---

### [SmoothClamp](#smoothclamp)

This node ensures that the output is within the provided range which is defined by the two wall parameters. The concept is similar to the Clamp node documented above, but the output is smoothed to the limits by the provided range value.

Expected Inputs: `1`

Parameters:

- WallA: any decimal number.
- WallB: any decimal number.
- Range: decimal values above 0. Larger values result in smoother transition when the input approaches or exceeds the walls.

---

### [Comparator](#comparator)

Compares the two inputs. The output is `-1` if the first input is smaller, `0` if both are equal, and `1` if the first input is greater.

Expected Inputs: `2`

1. Input A.
2. Input B.

This node has no parameters.

---

### [Equal](#equal)

The output is `1` if the two inputs are equal, otherwise `0`. Two inputs count as equal when the difference between them is at most the Epsilon. Use an Epsilon of `0` for an exact comparison.

Expected Inputs: `2`

1. Input A.
2. Input B.

Parameters:

- Epsilon: decimal number of `0` or more. The greatest difference between the two inputs that still counts as equal.

---

### [GreaterThan](#greaterthan)

The output is `1` if the first input is greater than the second input, otherwise `0`.

Expected Inputs: `2`

1. Input A.
2. Input B.

This node has no parameters.

---

### [GreaterOrEqual](#greaterorequal)

The output is `1` if the first input is greater than or equal to the second input, otherwise `0`.

Expected Inputs: `2`

1. Input A.
2. Input B.

This node has no parameters.

---

### [LessThan](#lessthan)

The output is `1` if the first input is less than the second input, otherwise `0`.

Expected Inputs: `2`

1. Input A.
2. Input B.

This node has no parameters.

---

### [LessOrEqual](#lessorequal)

The output is `1` if the first input is less than or equal to the second input, otherwise `0`.

Expected Inputs: `2`

1. Input A.
2. Input B.

This node has no parameters.

---

### [And](#and)

The logic nodes read each input as a boolean. A value of `0` is `false` and any other value is `true`.

The output is `1` if every input is non-zero, otherwise `0`. If no inputs are provided the output is `0`.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Or](#or)

The output is `1` if at least one input is non-zero, otherwise `0`. If no inputs are provided the output is `0`.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Not](#not)

The output is `1` if the input is `0`, otherwise `0`.

Expected Inputs: `1`

This node has no parameters.

---

### [Nor](#nor)

The output is `1` if every input is `0`, otherwise `0`. This is the inverse of the Or node. If no inputs are provided the output is `1`.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Xor](#xor)

The output is `1` if an odd number of inputs are non-zero, otherwise `0`. With two inputs this gives `1` when exactly one of them is non-zero.

Expected Inputs: `[0, infinite)`

This node has no parameters.

---

### [Selector](#selector)

Outputs one of two Density fields based on a condition input. If the condition is non-zero, the output is the Then input. Otherwise the output is the Else input. Only the selected branch is calculated, so you can use this node to skip expensive fields.

This node needs all three inputs. If you provide a different number of inputs, the output is `0`.

Expected Inputs: `3`

1. Then.
2. Else.
3. Condition.

This node has no parameters.

---

### [Abs](#abs)

The output is the absolute value of the input.

Expected Inputs: `1`

This node has no parameters.

---

### [XValue](#xvalue)

The local X coordinate.

Expected Inputs: `0`

Parameters: None

---

### [YValue](#yvalue)

The local Y coordinate.

Expected Inputs: `0`

Parameters: None

---

### [ZValue](#zvalue)

The local Z coordinate.

Expected Inputs: `0`

Parameters: None

---

### [XOverride](#xoverride)

Overrides the X coordinate the input sees.

Expected Inputs: `1`

Parameters: None

---

### [YOverride](#yoverride)

Overrides the Y coordinate the input sees.

Expected Inputs: `1`

Parameters: None

---

### [ZOverride](#zoverride)

Overrides the Z coordinate the input sees.

Expected Inputs: `1`

Parameters: None

---

### [YSampled](#ysampled)

Reduces the performance cost of its child Density by interpolating between spread-out samples along the Y-axis.

Instead of generating the child's Density value for every single coordinate, it generates the child's value in intervals determined by the SampleDistance and SampleOffset and interpolates the value between the samples.

Tips:

- Upstream transformations and warping of the domain could drastically reduce any performance gains.
- If the downstream Density doesn't cost more to generate vertically then this node won't have a significant impact.


  - 3D noise in the downstream will be sped up.
  - 2D noise in the downstream will not be significantly sped up if at all.
- Use the SampleOffset to reduce undesired effects such as alignment of the interpolation artifacts with fluid levels.
- You can use this in combination with a Mix to transition to the full non-sampled version of the child node in areas where precision is important (close to a POI, at the terrain's surface, etc.).

Below you can see the performance difference between different SampleDistance values for the terrain configuration in the screenshot below:

| SampleDistance | TerrainStage (ms) |
| --- | --- |
| None | 14 |
| 4 | 8 |
| 8 | 6 |
| 16 | 5 |

Expected Inputs: `1`

Parameters:

- SampleDistance: positive decimal number. Safe starting value: `4.0`. Determines how far apart the samples are taken. Larger numbers result in more performance savings but less precise terrain vertically.
- SampleOffset: decimal number. Safe starting value: `0.0`. Allows for non-zero-aligned sample distribution.
- Interpolate: boolean. Enables the smooth interpolation between the samples.

---

### [Cache](#cache)

Caches the input for the current coordinates. The capacity determines how many coordinates' can be cached before having to drop the oldest one.

Expected Inputs: `1`

Parameters:

- Capacity: positive Integer. Determines how many coordinates this cache can hold at once. A safe value is 3.

---

### [Angle](#angle)

The angle in degrees between two vectors, one of which is procedurally generated by a VectorProvider.

Expected Inputs: `0`

Parameters:

- Vector: 3D vector. Reference vector to compare against.
- VectorProvider: procedural vector. Vector to compare.

---

### [Sqrt](#sqrt)

The output is the square root value of the input.

For negative inputs, this node uses a modified function to always return values that make sense and are useful. The following graph shows in blue the values for positive inputs, and in red the values for negative inputs.

Expected Inputs: `1`

This node has no parameters.

---

### [Inverter](#inverter)

The output is the input multiplied by -1.

Expected Inputs: `1`

This node has no parameters.

---

### [Pow](#pow)

The output is the input taken to the power of the given exponent.

For negative inputs, this node uses a modified function to always return values that make sense and are useful.

Expected Inputs: `1`

Parameters:

- Exponent: any value. A good starting value would be `2`.

---

### [Trig](#trig)

The output is a trigonometric function of the input. Use this node to make a Density field periodic or wave shaped.

The node reads the input in radians, after it multiplies the input by InputScale. Density values usually stay near `[-1, 1]`. That range is less than a fifth of a radian cycle, so a raw input gives an almost straight line. Set InputScale to about `6.28` to sweep one full cycle over that range.

The output is always a finite value:

- Asin and Acos clamp the input to `[-1, 1]`. An input outside that range gives the value at the nearest boundary.
- Tan clamps the output near its asymptotes.

Expected Inputs: `1`

Parameters:

- Function: one of Sin, Cos, Tan, Asin, Acos or Atan. The name is case sensitive.
- InputScale: the factor applied to the input before the function. A good starting value would be `6.28`.

---

### [Scale](#scale)

Stretches or contracts the input Density field. The input’s Density field is scaled by the factor’s provided for each axis. Values above `1` stretches the field and values below `1` contract it. Values under `0` flip the field.

The image below shows a Density field before and after passing it through a Scale node: The node is configured to only scale up the X direction.

You can also scale on a non-orthogonal axis by using Rotator nodes in combination with a Scale node, like this:

Expected Inputs: `1`

Parameters:

- X: decimal number.
- Y: decimal number.
- Z: decimal number.

---

### [DistanceToBiomeEdge](#distancetobiomeedge)

Outputs the distance to the nearest biome edge in blocks.

Expected Inputs: `0`

Parameters: None

---

### [Terrain](#terrain)

Outputs the world's interpolated terrain Density.

This should only be used as part of a biome's MaterialProvider nodes. This won't work if used in the Terrain's Density nodes.

Expected Inputs: `0`

Parameters: None

---

### [Rotator](#rotator)

Aligns the input Density field so that its Y axis is aligned with the provided axis, and spins it around the new axis by the provided angle.

Expected Inputs: `1`

Parameters:

- NewYAxis: 3D vector asset. Y-axis of the rotated field rlative to the parent's y-axis.
- SpinAngle: decimal number. The angle in degrees to spin the field around the new axis.

---

### [Slider](#slider)

Slides the input Density field in the direction of the provided vector.

Expected Inputs: `1`

Parameters:

- SlideX: decimal number.
- SlideY: decimal number.
- SlideZ: decimal number.

---

### [PositionsPinch](#positionspinch)

Pinches or expands the first input’s Density field around the Positions. The intensity and look of the effect is determined by the PinchCurve. The range of the effect around each position is determined by the MaxDistance.

If the NormalizeDistance is `true`, then the curve’s input and output’s unit is normalized to the MaxDistance. In other words, for half of the MaxDistance, the curve’s input and output will be `0.5`, and at the full MaxDistance, it would be `1`. Otherwise if `false`, the curve’s values will represent the absolute distance in terms of blocks for both its input and output.

Below is an example configuration of a pinch, and the resulting terrain.

Below is an example configuration of a pull and the resulting terrain.

The PositionsMinY and PositionsMaxY parameters limit the vertical range queried from the Positions field. The default values are `0.0` and `0.0001` to let in all the Positions at `Y:0`.

Expected Inputs: `1`

Parameters:

- Positions: Positions asset slot. The Positions are the anchors that pinch and expand the Density field around them.
- PinchCurve: Curve asset slot. The curve determines how the Density field is pinched or expanded depending on how close it is to a position.
- MaxDistance: positive decimal number. The value represents how big the area of effect is in block units.
- NormalizeDistance: boolean value. Default is `true`. If `true` then the Curve’s input and output unit is normalized to the MaxDistance value, otherwise if `false` the Curve’s input and output unit is in blocks.
- HorizontalPinch: boolean value. Default is `false`. If `true`, the input is pinched horizontally only.
- PositionsMaxY: decimal number. Determines the upper Y level bound of the region queried from the Positions field. Any Positions that are at or above this value are ignored.
- PositionsMinY: decimal number. Determines the lower Y level bound of the region queried from the Positions field. Any Positions that are below this value are ignored.

---

### [PositionsTwist](#positionstwist)

Twists the first input’s Density field around the Positions. The intensity and look of the effect is determined by the TwistCurve. The range of the effect around each position is determined by the MaxDistance.

If the NormalizeDistance is `true`, then the curve’s input unit is normalized to the MaxDistance. In other words, for half of the MaxDistance, the curve’s input will be `0.5`, and at the full MaxDistance, it would be `1`. Otherwise if `false`, the curve’s values will represent the absolute distance in terms of blocks.

The output of the curve is always in degrees. This means that to produce a full twist around the axis, the curve must output `360` (degrees).

Below is an example configuration of a simple twist, and the resulting terrain.

Below is an example configuration of a double twist where part of the curve twists in one direction and part in the other direction, and the resulting terrain.

Expected Inputs: `1`

Parameters:

- Positions: Positions asset slot. The Positions are the anchors that pinch and expand the Density field around them.
- TwistCurve: Curve asset slot. The curve determines how the Density field is twisted depending on how close it is to a position.
- TwistAxis: vector asset slot. The axis around which the twisting happens.
- MaxDistance: positive decimal number. The value represents how big the area of effect is in block units.
- NormalizeDistance: boolean value. Default is `true`. If `true` then the Curve’s input unit is normalized to the MaxDistance value, otherwise if `false` the Curve’s input unit is in blocks.
- ZeroPositionsY: boolean value. Flattens the positions input to `y=0`.

---

### [GradientWarp](#gradientwarp)

Warps the first input based on the gradient of the second input Density field. This works similar to the legacy warping functionality, except it also works in 3D and you can plug any Density field into it.

This implementation is relatively expensive because it calculates the gradient of the input Density field. I recommend optimizing the use of this node by isolating and minimizing its use space. A fast version of this node can be implemented which would let you only use Simplex noise to warp. This will depend on the feedback and priorities.

This node incorporates a Cache2D so you don’t need to use a Cache2D node on its output.

Expected Inputs: `2`

- `0`: Density that is warped.
- `1`: Warping source.

Parameters:

- SampleRange: decimal number greater than 0. Distance between samples. Recommended value: 1.
- WarpFactor: decimal number greater than 0. With greater values you get more warping.
- 2D: boolean. If `true` the output of this node is 2D. An internal Cache2D is used.
- YFor2D: decimal coordinate. Default is 0. The Y coordinate used from the input to calculate the gradient from.

---

### [FastGradientWarp](#fastgradientwarp)

This is a faster implementation of the GradientWarp node. It warps the input Density field using an internal simplex noise generator.

Expected Inputs: `1`

- Input 1: Density field to be warped.

Parameters:

- WarpScale: decimal number greater than 0. This determines the scale of the internal warper noise field.
- WarpLacunarity: decimal number greater than 0. The lacunarity of the internal simplex field.
- WarpPersistence: decimal number greater than 0. The persistence of the internal simplex field.
- WarpOctaves: integer greater than 0. The internal field’s octaves.
- WarpFactor: decimal number greater than 0. The maximum distance coordinates can be warped.

---

### [VectorWarp](#vectorwarp)

Warps the input along the provided vector. The amount of warping is determined by the intensity of the second input Density field and the WarpFactor.

For example, if your warp field (second input) has a value of `0.5` and the WarpFactor is `15`, then the number of blocks the first input will be warped by in the direction of the vector is `7.5` units (blocks), `15 x 0.5 = 7.5`.

Below is a PositionsCellNoise (Distance2Div) passed through a VectorWarp. The warping field is a normal simplex, the WarpVector is sideways `{1, 0, 1}` and the WarpFactor is `15`.

Expected Inputs: `2`

- Input 1: Density field to be warped.
- Input 2: Density field that warps the first field.

Parameters:

- WarpFactor: decimal number greater than 0. With greater values you get more warping.
- WarpVector: vector asset slot. Can be any vector. This determines the direction in which the warping happens. Negative values of the factor or the warping density field will result in flipped warping direction.

---

### [Anchor](#anchor)

Anchors the origin of the child field (input) to the contextual Anchor if it exists. It basically puts the `{0, 0, 0}` origin of the child Density at the position of the Anchor.

For a contextual Anchor to exist, a parent of this node must produce an Anchor. The Density ReturnType node, for example, leaves the cell's center as a contextual Anchor in its Density child’s context.

You can also reverse the effect later down the line to move the origin back to the world’s origin like in the screenshot below. The field is unanchored when it reaches the bottom branch of the Sum, but the top branch is still anchored.

*Example: Say you have a Density field that creates a ball around its origin `{0, 0, 0}`. You also have a PositionsCellNoise with a Density return type, which leaves an anchor point at its cell's center. If you anchor the ball Density to the cell, the ball will generate around the cell's center.*

Expected Inputs: `1`

Parameters:

- Reverse: boolean. If `true`, the node will reverse the origin of the child to the world’s origin, or the origin before the previous Anchor node.

---

### [Normalizer](#normalizer)

It linearly remaps the input's range. The scale is defined by the FromMin, FromMax, ToMin and ToMax parameters. The diagrams below show how those four parameters can be intuitively used to normalize the input.

Note that the FromMin and FromMax can be any values, in this example I decided to place them at the extremes of the input’s range.

Expected Inputs: `1`

Parameters:

- FromMin: any decimal number.
- FromMax: any decimal number.
- ToMin: any decimal number.
- ToMax: any decimal number.

---

### [BaseHeight](#baseheight)

This node lets you reference a DecimalConstant from the WorldStructure. It can operate in 2 modes:

- Distance OFF: output the Y value of the BaseHeight at each X/Z position
- Distance ON: output the distance between the queried position and the BaseHeight.

In Distance mode ON, the output is measured in blocks (coordinate units). The value is positive if the query position is above the BaseHeight, and negative if it is below the BaseHeight. Below is an example of that.

Expected Inputs: `0`

Parameters:

- BaseHeightName: string. Name of the DecimalConstant to reference.
- Distance: boolean value. Toggles whether to output the raw Y coordinate of the BaseHeight or the distance from the BaseHeight from the query position.

---

### [Switch](#switch)

Lets you switch between multiple Density branches based on the contextual SwitchState. The SwitchState is a string that can be placed in the context of a Density tree using a SwitchState Density node. The default SwitchState of a Density tree is “Default”.

Below is an example where I use a Default branch, alongside two other branches.

Expected Inputs: `0`

Parameters:

- SwitchCases: Switch Case asset slots. The possible paths to switch into based on the parent's SwitchState.

---

### [SwitchState](#switchstate)

Sets the contextual SwitchState for the downstream Density branch.

See the Switch documentation above for an example use case.

Expected Inputs: `1`

Parameters:

- SwitchState: string. Name of the SwitchState to set for the children of this node.

---

### [Graph](#graph)

Reads a Density from the content that graph nodes carry. Every entry within Range of the sampled position contributes, and entries closer to that position get more weight. See [GraphContent](/hytale-generator/graph-generator/graph-content/) for how content and layers work.

Expected Inputs: `0`

Parameters:

- GraphGenerator: [GraphGenerator](/hytale-generator/graph-generator/graph-generator/) slot. The graph to read.
- ContentLayer: string. The Density layer to read from each node's content.
- BackgroundValue: decimal number. Sets the value returned where no content is in range.
- TransitionSlope: decimal number. Sets how steeply the weight rises between the edge of the Range and the node. A safe starting value is `2.5`.
- TransitionOffset: decimal number. Shifts the weight curve. Positive values move it toward the node. A safe starting value is `0`.
- TransitionSmooth: decimal number greater than or equal to `0`. Softens the curve where the weight reaches its maximum. A safe starting value is `0.2`.
- QueryBufferCapacity: integer greater than `0`. Sets how many node lookups stay in memory. A safe starting value is `3`.

---

### [Imported](#imported)

Imports an exported Density asset.

Parameters:

- Name: string. The exported Density.

---

### [Exported](#exported)

Allows exporting a Density field as a single instance. Enabling the SingleInstance on this node ensures all importers share the same logic.

By default a completely different instance is create for every Imported node. When there are multiple Imported nodes that import the same exported key, a new instance of that exported Density tree will be created for each one of the Imported nodes. SingleInstance ensures all importers share the same underlying instance of the node tree.

This node can be used to optimize caching when an exported Density is imported multiple times in the same context and contains caches. The caches would be shared between the different imported instances.

*Important: This is still an experimental feature and could cause unexpected behaviors if misused.*

Expected Inputs: `1`

Parameters:

- SingleInstance: boolean. Enable to share the exported for all Imported nodes referencing this key.



======================================================================
## Curve (Math Transfer Functions)
Official URL: https://docs.hytale.com/hytale-generator/curve/
======================================================================

# Curve

> Source: [https://docs.hytale.com/hytale-generator/curve/](https://docs.hytale.com/hytale-generator/curve/)

**Description:** Curves map decimal values to other decimal values. They enable the basic f(x) = y math expression. These nodes can be used to build the functions you need in world-gen.

---

---

### [Constant](#constant)

Outputs a constant value.

Parameters:

- Value: decimal number.

---

### [Manual](#manual)

You can plot points that connect with lines to create the curve. The points are connected with straight lines. The function is constant before the first point and after the last point.

The example above shows a function made of 3 points.

Parameters:

- Points: list of 3D vectors. Curve points.

---

### [DistanceExponential](#distanceexponential)

The DistanceExponential Curve has the following shape depending on the Exponent value. As this curve’s input approaches the Range value, it outputs `0.0`. At an input of `0.0`, this curve outputs `1.0`.

Parameters:

- Exponent: positive decimal number. Affects the curve’s shape like in the diagram above.
- Range: positive decimal number. The value after which the curve outputs a constant 0.0.

---

### [DistanceS](#distances)

The DistanceS Curve combines two DistanceExponential curves to produce a shape similar to the diagram below. As this curve’s input approaches the Range value, it outputs `0.0`. At an input of `0.0`, this curve outputs `1.0`. The asset’s parameters allow you to tweak the shape of the curve.

Below are some examples of Positions2D terrain using this curve.

Parameters:

- ExponentA: decimal value greater than 0.0. Affects the curve’s shape in the first half of the range.
- ExponentB: decimal value greater than 0.0. Affects the curve’s shape in the second half of the range.
- Range: decimal value greater than 0.0. The value after which the curve outputs a constant 0.0.
- Transition: optional decimal value between `0.0` and `1.0` with a default of `1.0`. Values close to `0.0` create a curve with a faster, more sudden transition between ExponentA and ExponentB. Values of `1.0` transition from ExponentA to ExponentB over the entire curve.
- TransitionSmooth: optional decimal value between `0.0` and `1.0` with a default of `1.0`. Affects the shape of the transition. Lower values can result in a sharper curve in some situations. Try different values while you design to get a feel for it.

---

### [Ceiling](#ceiling)

This curve puts a ceiling on the output of the child curve asset.

Parameters:

- Ceiling: decimal number. The maximum value this curve will output.
- Curve: curve slot.

---

### [Floor](#floor)

This curve puts a floor on the output of the child curve asset.

Parameters:

- Floor: decimal number. The minimum value this curve will output.
- Curve: curve slot.

---

### [SmoothCeiling](#smoothceiling)

This curve puts a ceiling on the output of the child curve asset. As the curve approaches the ceiling within the provided range, it gets smoothed.

Parameters:

- Ceiling: decimal number. The maximum value this curve will output.
- Range: decimal number, greater than or equal to `0`. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
- Curve: curve slot.

---

### [SmoothFloor](#smoothfloor)

This curve puts a floor on the output of the child curve asset. As the curve approaches the floor within the provided range, it gets smoothed.

Parameters:

- Floor: decimal number. The minimum value this curve will output.
- Range: decimal number, greater than or equal to `0`. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
- Curve: curve slot.

---

### [SmoothClamp](#smoothclamp)

This curve limits the range of the child curve asset within the provided walls. As the curve approaches the limits within the provided range, it gets smoothed.

Parameters:

- WallA: decimal number.
- WallB: decimal number.
- Range: decimal number, greater than or equal to `0`. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
- Curve: curve slot.

---

### [SmoothMax](#smoothmax)

This curve retrieves the maximum between the two curves provided. The intersection between the curves is smoothed as their values approach within the provided range.

Parameters:

- Range: decimal number, greater than or equal to `0`. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curves.
- CurveA: curve slot.
- CurveB: curve slot.

---

### [SmoothMin](#smoothmin)

This curve retrieves the minimum between the two curves provided. The intersection between the curves is smoothed as their values approach within the provided range.

Parameters:

- Range: decimal number, greater than or equal to `0`. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curves.
- CurveA: curve slot.
- CurveB: curve slot.

---

### [Clamp](#clamp)

This clamps the curve between the two wall values provided. The output of this curve will never reach outside the walls.

Parameters:

- WallA: decimal number.
- WallB: decimal number.
- Curve: curve slot.

---

### [Inverter](#inverter)

This inverts the child curve such that positive values become negative and negative values become positive.

Parameters:

- Curve: curve slot.

---

### [Max](#max)

This outputs the maximum value of all the child curves.

Parameters:

- Curves: list of curve slots.

---

### [Min](#min)

This outputs the minimum value of all the child curves.

Parameters:

- Curves: list of curve slots.

---

### [Multiplier](#multiplier)

This multiplies all the child curves’ outputs.

Parameters:

- Curves: list of curve slots.

---

### [Not](#not)

This applies a logical NOT operation on the child curve. When the child’s output is `1`, this will output `0`, and when the child’s output is `0`, this will output `1`. All numbers in between are scaled accordingly.

Parameters:

- Curve: curve slot.

---

### [Sum](#sum)

This adds the values of all the provided curves.

Parameters:

- Curves: list of curve slots.

---

### [Imported](#imported)

Imports an exported Curve.

Parameters:

- Name: string. The exported Curve asset.



======================================================================
## Vector Provider (3D Vectors & Warping)
Official URL: https://docs.hytale.com/hytale-generator/vector-provider/
======================================================================

# Vector Provider

> Source: [https://docs.hytale.com/hytale-generator/vector-provider/](https://docs.hytale.com/hytale-generator/vector-provider/)

**Description:** Defines a 3D decimal vector {x, y, z}.

---

### [Constant](#constant)

Generates the provided vector.

Parameters:

- Vector: 3D decimal vector.

---

### [SetX](#setx)

Sets the value of the vector's X component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [SetY](#sety)

Sets the value of the vector's Y component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [SetZ](#setz)

Sets the value of the vector's Z component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [Adder](#adder)

Adds all the input vectors.

This is component-wise addition. If the inputs are `v_1, v_2, ..., v_n`, the result is `sum of all v_i`, so each output component is the sum of the matching input components: `x = sum of all x_i`, `y = sum of all y_i`, `z = sum of all z_i`.

Parameters:

- Vectors: list of VectorProvider slots.

---

### [Subtracter](#subtracter)

Subtracts vector B from vector A.

This is component-wise subtraction. If `A = (a_x, a_y, a_z)` and `B = (b_x, b_y, b_z)`, the result is `A - B = (a_x - b_x, a_y - b_y, a_z - b_z)`, which you can read as the vector that points from **B** to **A**.

Parameters:

- Minuend: VectorProvider slot. Vector A.
- Subtrahend: VectorProvider slot. Vector B.

---

### [Random](#random)

Generates a unit vector in a random direction.

Parameters:

- Seed: String. Determines pseudo-random outcome.

---

### [Normalizer](#normalizer)

Sets the vector's length to the provided magnitude. The vector keeps its direction.

For any non-zero vector `v`, this computes `m * (v / |v|)`, where `|v| = sqrt(x^2 + y^2 + z^2)` and `m` is the target magnitude. In other words, it keeps the direction and rescales the length to exactly match the provided value.

Parameters:

- Vector: VectorProvider slot.
- Magnitude: Density slot. Determines the length of the vector.

---

### [ScalarMultiplier](#scalarmultiplier)

Multiplies the vector by a scalar.

If the input vector is `v = (x, y, z)` and the scalar is `k`, the result is `kv = (kx, ky, kz)`. This scales the length by `|k|`, and a negative scalar also flips the direction.

Parameters:

- Vector: VectorProvider slot.
- Scalar: Density slot.

---

### [Multiplier](#multiplier)

Multiplies all the provided vectors.

This is component-wise multiplication, not a dot or cross product. For vectors `A = (a_x, a_y, a_z)` and `B = (b_x, b_y, b_z)`, the result is `(a_x * b_x, a_y * b_y, a_z * b_z)`, and the same rule extends across any number of inputs.

Parameters:

- Vectors: list of VectorProvider slots. Vectors to multiply.

---

### [Cross](#cross)

Finds the cross product of the provided vectors: A cross B.

The cross product produces a vector perpendicular to both inputs. Its magnitude is `|A| * |B| * sin(theta)`, where `theta` is the angle between them, so it is largest for perpendicular vectors and zero for parallel ones.

Parameters:

- VectorA: VectorProvider slot.
- VectorB: VectorProvider slot.

---

### [PlaneProjector](#planeprojector)

Projects the vector onto a plane defined by the vectors PlaneA and PlaneB.

This keeps only the part of the vector that lies in the plane spanned by **PlaneA** and **PlaneB**. If `n` is the plane normal, usually from `n = (A cross B) / |A cross B|`, then the projection is `v - (v dot n) n`, which removes the component orthogonal to the plane.

Parameters:

- Vector: VectorProvider slot. The vector to project.
- PlaneA: VectorProvider slot. Defines the plane.
- PlaneB: VectorProvider slot. Defines the plane.

---

### [VectorProjector](#vectorprojector)

Projects the vector onto another vector.

This keeps only the component of **Source** that points along **Target**. For non-zero target vector `t` and source vector `s`, the projection is `proj_t(s) = ((s dot t) / (t dot t)) t`.

Parameters:

- Source: VectorProvider slot.
- Target: VectorProvider slot.

---

### [DensityGradient](#densitygradient)

Generates the gradient of the provided Density field. The resulting gradient vector shows which direction the Density field increases and how quickly it changes.

For more information about gradient vectors, see: https://en.wikipedia.org/wiki/Gradient

Parameters:

- SampleDistance: positive decimal value. How far apart the Density field value samples are taken. The optimal value for performance is 1.0. Greater values could be useful for smoothing out results for specific applications.
- Density: Density slot.

---

### [Cache](#cache)

Caches the input vector for each position.

Use this only if the downstream (child) VectorProvider is expensive and the same coordinate is queried more than once.

Parameters:

- Vector: VectorProvider slot. The output of this slot is cached.

---

### [Exported](#exported)

Allows exporting a VectorProvider as a single instance. Enabling `SingleInstance` on this node ensures all importers share the same logic.

By default, a completely different instance is created for every Imported node. When multiple Imported nodes import the same exported key, a new instance of that exported VectorProvider tree is created for each Imported node. `SingleInstance` ensures all importers share the same underlying instance of the node tree.

This node can be used to optimize caching when an exported VectorProvider is imported multiple times in the same context and contains caches. The caches would be shared between the different imported instances.

*Important: This is still an experimental feature and could cause unexpected behaviors if misused.*

Expected Inputs: `1`

Parameters:

- SingleInstance: boolean. Enable this to share the export across all Imported nodes that reference this key.
- VectorProvider: VectorProvider slot.

---

### [Imported](#imported)

Imports an exported VectorProvider.

Parameters:

- Name: string. The exported VectorProvider.



# ============================================================================
# SECTION: MATERIALS, STRATA & BLOCKS
# ============================================================================



======================================================================
## Material (Solid & Fluid Blocks)
Official URL: https://docs.hytale.com/hytale-generator/material/
======================================================================

# Material

> Source: [https://docs.hytale.com/hytale-generator/material/](https://docs.hytale.com/hytale-generator/material/)

**Description:** Defines a block Material.

---

Defines a block's solid and fluid Material content.

- You can flip the solid material vertically by checking the box.
- For precise control, you can define a rotation for the solid material, as shown below.
- Yaw, Pitch, and Roll can be one of: `None`, `Ninety`, `OneEighty`, `TwoSeventy`.
- If you add a rotation node to the pin, the checkbox is ignored.



======================================================================
## Material Provider (Terrain & Surface Rules)
Official URL: https://docs.hytale.com/hytale-generator/material-provider/
======================================================================

# Material Provider

> Source: [https://docs.hytale.com/hytale-generator/material-provider/](https://docs.hytale.com/hytale-generator/material-provider/)

**Description:** Determines which Material to use where.

---

### [Constant](#constant)

Provides a constant Material.

Parameters:

- Material: Material slot.

---

### [Transparent](#transparent)

The lack of any Material.

Parameters: None.

---

### [Solidity](#solidity)

Separates terrain into Solid and Empty blocks. Provides a Material Provider slot for each selection.

Parameters:

- Solid: Material Provider slot. Where the terrain's Density value is greater than 0.
- Empty: Material Provider slot. Where the terrain's Density value is smaller or equal to `0`.

---

### [Queue](#queue)

Goes through a queue of Material Provider slots from highest to lowest priority. If a Material Provider in the queue doesn’t provide a block type, then the next one in the queue is queried. If no slot provides a block, then the Queue Material Provider will not provide a block. The query stops at the first slot that provides a block.

Parameters:

- Queue: list of Material Provider slots. Slots are queried from top to bottom.

---

### [SimpleHorizontal](#simplehorizontal)

Applies the child Material Provider to a vertical range.

If a BaseHeight is provided, the TopY and BottomY values are relative to the referenced DecimalConstant. Otherwise, they’re relative to the world’s Y coordinate.

Parameters:

- TopY: integer. Y coordinate. Defines the vertical top **exclusive** limit of the region.
- Top BaseHeight: string. DecimalConstant name.
- BottomY: integer. Y coordinate. Defines the vertical bottom **inclusive** limit of the region.
- Bottom BaseHeight: string. DecimalConstant name.
- Material: Material Provider slot. Slot to query for the selected region.

---

### [Striped](#striped)

Applies a Material Provider slot to a set of horizontal stripes of blocks of varying thickness and position. Below is an example of basalt stripes in a stone cliff.

Parameters:

- Stripes: list of stripe assets. Each stripe contains `TopY` and `BottomY`.


  - TopY: integer. World Y value. Inclusive.
  - BottomY: integer. World Y value. Inclusive.
- Material: Material Provider slot. Slot to query for the selected region.

---

### [Weighted](#weighted)

Picks the Material Provider slot to query from a list. Each slot has a weight that determines the likelihood of being picked.

Parameters:

- Seed: string.
- SkipChance: float (`0.0`–`1.0`). Percentage of blocks to skip. When skipped, this provider returns no block and does not pick a slot.
- WeightedMaterials: list of weighted Material Provider slots.


  - Weight: number (> `0`). Higher value increases likelihood of being picked.
  - Material: Material Provider slot.

---

### [FieldFunction](#fieldfunction)

Selects a 3D region using a noise function and value delimiters. The delimiters link a Material Provider slot to a specific range of values in the noise function.

Parameters:

- FieldFunction: field function asset. Defines the field value for each block. Same asset type used by DAOTerrain density.
- Delimiters: list of delimiters. Higher entries have higher priority.


  - From: float. Minimum limit of the delimiter’s range.
  - To: float. Maximum limit of the delimiter’s range.
  - Material: Material Provider slot. Slot linked to this delimiter.

---

### [SpaceAndDepth](#spaceanddepth)

Allows placing layers of blocks on a terrain’s floor or ceiling surfaces. Layers of Material are piled on top of each other inside the surface (floor or ceiling), like a cake. There are multiple Layer types, each providing a unique way to define thickness. Below is an example with two layers in the floor of our terrain.

Conditions let you skip this Material Provider when they’re not met. An example could be a Condition that only places grass if there are at least 5 empty blocks above the surface.

Parameters:

- LayerContext: string. Context the layers are applied on. Values: `DEPTH_INTO_FLOOR`, `DEPTH_INTO_CEILING`.
- MaxExpectedDepth: positive integer. Optimization hint. Set to max expected combined layer depth. Too small can cut off layers. Too large can hurt performance.
- Condition: Condition slot. Provider returns no block if the Condition fails.
- Layers: list of Layer objects. Stacked top-to-bottom as depth increases into the context.

---

### [Condition](#condition)

A Condition object checks whether the environment is valid to determine whether the Material Provider should run for the current block. If the Condition fails for a block, the Material Provider returns no block.

An example Condition could check if there are at least 5 blocks of empty space above the floor. This is useful if you want dirt in tight spaces instead of grass. An example is shown below.

See the [JSON code here](https://miro.com/app/board/o9J_loYgP38=/?moveToWidget=3458764528990928227&cot=14).

The Condition’s `ContextToCheck` parameter selects which context value the Condition queries. In the example above, we’re checking the `SPACE_ABOVE_FLOOR` context, which is the number of blocks above the floor. There are currently two possible context values you can use:

- `SPACE_ABOVE_FLOOR`
- `SPACE_BELOW_CEILING`

There are multiple Condition types that allow you to compose more complex Conditions from simpler ones. Below are the different types.

**EqualsCondition**

Validates only if a context value is equal to the Condition’s configured value.

Parameters:

- ContextToCheck: string (`SPACE_ABOVE_FLOOR` | `SPACE_BELOW_CEILING`). Which context value the Condition queries.
- Value: integer. Condition passes only if the context value equals this.

**GreaterThanCondition**

Validates only if a context value is greater than the Condition’s configured threshold.

Parameters:

- ContextToCheck: string (`SPACE_ABOVE_FLOOR` | `SPACE_BELOW_CEILING`). Which context value the Condition queries.
- Threshold: integer. Condition passes only if the context value is greater than this.

**SmallerThanCondition**

Validates only if a context value is smaller than the Condition’s configured threshold.

Parameters:

- ContextToCheck: string (`SPACE_ABOVE_FLOOR` | `SPACE_BELOW_CEILING`). Which context value the Condition queries.
- Threshold: integer. Condition passes only if the context value is smaller than this.

**AndCondition**

Logical AND Condition. Validates only if all child Conditions validate.

Parameters:

- Conditions: list of Condition slots. Condition passes only if all child Conditions pass.

**OrCondition**

Logical OR Condition. Validates if any child Condition validates.

Parameters:

- Conditions: list of Condition slots. Condition passes if any child Condition passes.

**NotCondition**

Logical NOT Condition. Validates only if its child Condition does not validate.

Parameters:

- Condition: Condition slot. Single slot (not a list). Condition passes if the child Condition fails.

**AlwaysTrueCondition**

This Condition type always validates.

---

### [Layer](#layer)

In the SpaceAndDepth Material Provider type, Layers are stacked into the depth of the floor or ceiling. Layers have a thickness and a Material Provider.

Below are some examples that illustrate how layers work.

Example 1:

- Layer 1: grass of thickness 1.

Example 2:

- Layer 1: grass of thickness 1.
- Layer 2: dirt of thickness ranging from 2 to 3.

Example 3:

- Layer 1: mud of thickness 1 to 5 defined by a 2D simplex noise function.
- Layer 2: dirt of thickness ranging from 2 to 3.

Example 4: This example shows how this can work not only on floors but also on ceilings by changing the LayerContext value to `DEPTH_INTO_CEILING`.

- Layer 1: dirt of thickness in range 2 to 3.

End of examples.

Below are the supported Layer types.

**ConstantThickness**

Has the same thickness everywhere.

Parameters:

- Material: Material Provider slot.
- Thickness: integer (>= `0`). Thickness in blocks.

**RangeThickness**

The thickness varies per XZ coordinate and is defined by a range of possible values.

Parameters:

- Material: Material Provider slot.
- RangeMin: integer (>= `0`). Minimum thickness in blocks.
- RangeMax: integer (>= `0`). Maximum thickness in blocks (inclusive).
- Seed: string.

**WeightedThickness**

The thickness varies per XZ coordinate and is picked from a list of weighted thickness values.

Parameters:

- Material: Material Provider slot.
- PossibleThicknesses: list of weighted thickness values. Used to pick a thickness per XZ.
- Seed: string. Seed used when picking the thickness.

**NoiseThickness**

The thickness varies per XZ coordinate and is determined by a noise function tree. The thickness value comes from the function output. In the example below, the output is normalized from `0` to `5`. This makes the thickest points about 5 blocks.

Parameters:

- Material: Material Provider slot.
- ThicknessFunctionXZ: noise function tree. Sampled as a 2D function on the XZ plane (Y is always `0`).

---

### [Graph](#graph)

Reads a Material Provider from the content that graph nodes carry. The nearest node within Range of the sampled position wins, and its content provides the material. See [GraphContent](/hytale-generator/graph-generator/graph-content/) for how content and layers work.

Parameters:

- GraphGenerator: [GraphGenerator](/hytale-generator/graph-generator/graph-generator/) slot. The graph to read.
- ContentLayer: string. The Material layer to read from each node's content.
- QueryBufferCapacity: integer greater than `0`. Sets how many node lookups stay in memory. A safe starting value is `3`.

---

### [Imported](#imported)

Imports an exported Material Provider.

Parameters:

- Name: string. The exported Material Provider.



======================================================================
## Block Mask (Replacement Rules)
Official URL: https://docs.hytale.com/hytale-generator/block-mask/
======================================================================

# Block Mask

> Source: [https://docs.hytale.com/hytale-generator/block-mask/](https://docs.hytale.com/hytale-generator/block-mask/)

**Description:** Determines which Materials get to replace which other Materials when placing content in the world.

---

#### [Source and Destination Material Explained](#source-and-destination-material-explained)

If you're trying to place bricks in water, then bricks are the Source Material, and water is the Destination Material.

- Source Material: the Material we're trying to place.
- Destination Material: the Material that exists where we want to place the Source Material.

#### [DontPlace](#dontplace)

If a Source Material is in the DontPlace BlockSet, it will not be placed.

#### [DontReplace](#dontreplace)

By default, Source Materials cannot replace Destination Materials in this BlockSet. This rule is overridden by the Advanced rules below.

#### [Advanced](#advanced)

The Advanced rules override the rules above for specific BlockSets.

Each rule has:

- A Source BlockSet that identifies which Source Materials the rule applies to.
- A CanReplace BlockSet that determines which Destination Materials those sources can replace.

Parameters:

- DontPlace: BlockSet slot. Source Materials in this set will not be placed.
- DontReplace: BlockSet slot. By default, Source Materials cannot replace Destination Materials in this set.
- Advanced: list of rules. Per-BlockSet overrides. Each rule contains a Source BlockSet and a CanReplace BlockSet.


  - Source: BlockSet slot. Identifies which Source Materials the rule applies to.
  - CanReplace: BlockSet slot. Determines which Destination Materials those sources can replace.



======================================================================
## Pattern (Position & Composition Validation)
Official URL: https://docs.hytale.com/hytale-generator/pattern/
======================================================================

# Pattern

> Source: [https://docs.hytale.com/hytale-generator/pattern/](https://docs.hytale.com/hytale-generator/pattern/)

**Description:** Validates a world location based on its Material composition and other criteria.

---

### [Constant](#constant)

Gives the same result at every position. It always validates, or it never validates.

Parameters:

- Value: boolean. If `true`, the Pattern validates every position. If `false`, it validates no position.

---

### [BlockType](#blocktype)

Checks against the block's Material.

Parameters:

- Material: block Material.

---

### [BlockSet](#blockset)

Checks if the block's Material belongs to the BlockSet.

Parameters:

- BlockSet: BlockSet slot.

---

### [Offset](#offset)

Offsets the child Pattern by the given vector.

Parameters:

- Pattern: Pattern slot.
- Offset: 3D integer vector. The direction to offset the child Pattern in.

---

### [Rotator](#rotator)

Rotates the child Pattern.

Parameters:

- Pattern: Pattern slot.
- Rotation: Rotation. The child's rotation.

---

### [Floor](#floor)

Checks if there is a floor below the position. The Origin validates the position and the Floor validates right under the position. For a simple Floor configuration, use BlockType or BlockSet Patterns in those slots.

Parameters:

- Floor: Pattern slot. Validates the block under the Origin position.
- Origin: Pattern slot. Validates the block at the Origin position.

---

### [Ceiling](#ceiling)

Checks if there is a ceiling above the world position. The Origin validates the position and the Ceiling validates right above the position. For a simple Ceiling configuration, use BlockType or BlockSet Patterns in those slots.

Parameters:

- Ceiling: Pattern slot. Validates the coordinate above the Origin position.
- Origin: Pattern slot. Validates the coordinate at the Origin position.

---

### [Wall](#wall)

Checks if there is a wall next to the world position. For a simple Wall configuration, use BlockType or BlockSet Patterns in those slots. You can specify which directions are valid for the wall using the `N`, `S`, `E`, `W` string values.

The size of the wall can be 1x1 blocks or larger.

Parameters:

- Wall: Pattern slot. Validates the coordinate next to the Origin position.
- Origin: Pattern slot. Validates the coordinate at the Origin position.
- Directions: list of string directional values. “N”, “S”, “E” or “W”.
- RequireAllDirections: boolean. If `true` then all the directions you put in the list need to validate for the Pattern to validate a position. Otherwise only one of the list directions needs to validate.

---

### [Surface](#surface)

Validates if presented with a surface. A surface is a transition from one set of materials to another. For example, a soil floor to place a large tree on would be a soil surface in an air medium. The surface’s facing, the surface’s area, the medium’s area and the transition gap are configurable.

The surface can face 6 orthogonal directions: up, down, north, south, east and west. A surface’s facing direction is determined by its Facings. Furthermore, you can specify more than one facing and use the RequireAllFacings parameter. When a surface faces up, the medium is at the origin and the surface is below the origin. See the diagram below that shows a side-view of an up-facing surface.

The minimum area required by the surface and medium materials is determined by the SurfaceRadius and the MediumRadius values. A radius of `0.0` would result in an area of 1x1 block, while a radius of `2.0` would result in a disc of a 5 block diameter. The diagram below shows that relationship with a top-down view of an up-facing surface.

You can make the surface detection less strict by configuring a gap between the medium and the surface. That gap would allow some roughness between the medium and the surface. The diagrams below show how that works.

Parameters:

- Surface: Pattern slot. Validates every block of the surface.
- Medium: Pattern slot. Validates every block of the medium.
- SurfaceRadius: positive decimal number (>= `0.0`). Determines the size of the surface area.
- MediumRadius: positive decimal number (>= `0.0`). Determines the size of the medium area.
- SurfaceGap: integer (>= `0`).
- MediumGap: integer (>= `0`).
- Facings: list of string facing values. “N”, “S”, “E”, “W”, “U”, “D”.
- RequireAllFacings: boolean. If `true` then the Pattern will only validate if all Facings validate.

---

### [Cuboid](#cuboid)

Defines a cuboid region relative to the Pattern’s origin. The region is anchored by Min and Max inclusive points. This is similar to the shape of a creative Selection Tool. The SubPattern is tested against every position inside the cuboid. The Cuboid Pattern validates only if all the inner positions are validated by the SubPattern.

Below is a 2D representation of this 3D Cuboid Pattern.

Parameters:

- Min: 3D point. Inclusive minimum point of the cuboid relative to the Pattern’s origin.
- Max: 3D point. Inclusive maximum point of the cuboid relative to the Pattern’s origin.
- SubPattern: Pattern slot. The Cuboid uses this Pattern to validate the positions inside itself.

---

### [And](#and)

Logical operation that validates only if all the Patterns in its list also validate.

Parameters:

- Patterns: list of Pattern asset slots.

---

### [Or](#or)

Logical operation that validates if at least one of the Patterns in its list validates.

Parameters:

- Patterns: list of Pattern asset slots.

---

### [Not](#not)

Logical operation that validates only where the nested Pattern does not validate.

Parameters:

- Pattern: Pattern asset slot.

---

### [FieldFunction](#fieldfunction)

This Pattern validates only if the provided Density field at the position is within the specified delimiters. In combination with an And and Or pattern, this Pattern provides more control over the valid positions.

Important: because this Pattern queries a noise field, it can be expensive if placed high in the hierarchy. I recommend setting your Pattern structure such that this Pattern is the last to be tested. The diagram below shows examples of that.

In the green box, the FieldFunction Pattern runs only on positions with a floor. In the red box, the FieldFunction Pattern runs on every position. The difference between the two scenarios is significant.

Parameters:

- FieldFunction: slot for a noise function RootNode.
- Delimiters: list of noise value delimiters.


  - Min: decimal number. Lower inclusive bound of the delimiter.
  - Max: decimal number. Higher exclusive bound of the delimiter.

---

### [Imported](#imported)

Imports an exported Pattern.

Parameters:

- Name: string. The exported Pattern.



# ============================================================================
# SECTION: POSITIONS, SCANNING & PLACEMENT
# ============================================================================



======================================================================
## Positions Provider (Point Fields & Grids)
Official URL: https://docs.hytale.com/hytale-generator/positions-provider/
======================================================================

# Positions Provider

> Source: [https://docs.hytale.com/hytale-generator/positions-provider/](https://docs.hytale.com/hytale-generator/positions-provider/)

**Description:** Determines an infinite 3D positions field.

---

### [TriangularGrid2d](#triangulargrid2d)

Generates a 2D infinite triangular mesh of positions with a distance of 1 block between them.

Parameters: None

---

### [SquareGrid2d](#squaregrid2d)

Generates a 2D infinite square mesh of positions with a distance of 1 block between them.

Parameters: None

---

### [SquareGrid3d](#squaregrid3d)

Generates a 3D infinite square mesh of positions with a distance of 1 block between them.

Parameters: None

---

### [Scaler](#scaler)

Scales the child Positions.

Parameters:

- Positions: PositionProvider slot. Positions to scale.
- Scale: 3D decimal vector. Determines the scale.

---

### [Jitter2d](#jitter2d)

Adds jitter along the x/z plane.

Parameters:

- Positions: PositionProvider slot. Positions to jitter.
- Magnitude: decimal number. The maximum distance each position is allowed to travel from its original place when jittered. The actual movement distance is random for each position between `0` and the Magnitude.
- Seed: string. Determines the direction and distance applied to each position.

---

### [Jitter3d](#jitter3d)

Adds jitter in all directions.

Parameters:

- Positions: PositionProvider slot. Positions to jitter.
- Magnitude: decimal number. The maximum distance each position is allowed to travel from its original place when jittered. The actual movement distance is random for each position between `0` and the Magnitude.
- Seed: string. Determines the direction and distance applied to each position.

---

### [DirectionalJitter](#directionaljitter)

Adds jitter along one direction. Each position moves along the Direction vector only.

Parameters:

- Positions: PositionProvider slot. Positions to jitter.
- Magnitude: decimal number (>= `0`). The maximum distance each position is allowed to travel from its original place when jittered. The actual movement distance is random for each position between `0` and the Magnitude.
- Direction: 3D decimal vector. The direction the positions move along. Only the direction of the vector matters, not its length.
- IsBidirectional: boolean. If `true`, a position can move both ways along the Direction. If `false`, it only moves the way the Direction points.
- Seed: string.

---

### [Clusters](#clusters)

Generates clusters of positions anchored by a Distributor PositionProvider. The cluster size is determined by the ClusterBounds, and the cluster contents are determined by the Cluster PositionProvider.

The Cluster PositionProvider receives the cluster's position as an anchor.

Parameters:

- Cluster: PositionProvider slot. Positions making up the cluster.
- Distributor: PositionProvider slot. Each cluster is anchored to one of these positions.
- ClusterBounds: Bounds. The maximum size of a cluster around its anchor.

---

### [List](#list)

Allows you to manually define a static list of positions in world coordinates.

Parameters:

- Positions: list of 3D positions. Each entry contains `X`, `Y`, and `Z`.


  - X: decimal number.
  - Y: decimal number.
  - Z: decimal number.

---

### [Anchor](#anchor)

Anchors the origin of the child Positions field to the contextual Anchor, if one exists. For a contextual Anchor to exist, a parent of this node must produce an Anchor.

You can also reverse the effect later in the chain to move the origin back to the world’s origin.

Parameters:

- Positions: Positions slot.
- Reversed: boolean. If `true`, reverses the origin of the child back to the world’s origin (or to the origin before the previous Anchor node).

---

### [Bound](#bound)

Limits the positions to a bounded region.

This can limit an infinite mesh for use as spawn points.

Parameters:

- Bounds: decimal bounds 3D.
- Positions: PositionProvider slot.

---

### [FieldFunction](#fieldfunction)

Enables masking out positions using a Density field. The delimiters determine the regions of the Density field where positions are kept.

Parameters:

- Density: Density slot. Slot for a Density input.
- Delimiters: list of delimiters. List of Density value delimiters.


  - Min: decimal number. Lower bound of the delimiter.
  - Max: decimal number. Higher bound of the delimiter.
- Positions: PositionProvider slot. The positions on which the mask is applied.

---

### [Occurrence](#occurrence)

Discards a percentage of input positions based on a Density field. The value of the Density field at each position determines the chance that the position is kept.

- Positions where the Density value is less than or equal to `0.0` have a 0% chance of being kept.
- Positions where the Density value is greater than or equal to `1.0` have a 100% chance of being kept.
- Positions where the Density value is between `0.0` and `1.0` have a proportional percentage chance of being kept. *Example: `0.4` → 40%.*

Below is an image showing a simple noise field used to create regions with rare oak trees and regions with dense oak trees.

Parameters:

- FieldFunction: Density slot. Determines the chance of keeping the positions.
- Seed: string. Determines the outcome.
- Positions: PositionProvider slot.

---

### [Offset](#offset)

Offsets the positions by the vector.

Parameters:

- Offset: 3D decimal vector. Offset vector.
- Positions: PositionProvider slot. Positions to offset.

---

### [VectorOffset](#vectoroffset)

Offsets each position by their respective vector.

The MovementBounds limit the possible movement of each position. The position doesn't move if the vector points outside its MovementBounds.

Parameters:

- Positions: PositionProvider slot. Positions to offset.
- VectorProvider: VectorProvider slot. Offset vector.
- MovementBounds: 3D decimal bounds. The bounds each position is allowed to move relative to its origin.

---

### [BaseHeight](#baseheight)

Vertically offsets the Positions inside the configured vertical region by the number of blocks determined by the referenced DecimalConstant.

A typical application of this would be to grab Positions at `Y: 0` and offset them up by the DecimalConstant to get closer to a terrain feature. Further adjustments can then be made using an Offset PositionsProvider node.

Parameters:

- BaseHeightName: string. Name of the DecimalConstant to reference.
- Positions: PositionProvider slot.

---

### [Framework](#framework)

Provides a named Positions field from the World Structure's Framework. Use this node to share one Positions field between the Biomes of a world.

The referenced entry is a Positions Framework module entry of the World Structure asset.

If no entry has that name, this node provides no positions.

Parameters:

- Name: string. Name of the Framework Positions entry to reference.

---

### [Union](#union)

Combines all positions into a single Positions field.

Parameters:

- Positions: list of PositionProvider slots.

---

### [SimpleHorizontal](#simplehorizontal)

Keeps only positions within the provided Y range.

Parameters:

- RangeY: decimal number. The Y range to keep the positions in.
- Positions: PositionProvider slot.

---

### [Cache](#cache)

Caches the output provided by the Positions slot to improve performance in certain situations. This asset can be useful to improve performance when a Positions asset is expensive and queried numerous times.

How effective this Cache is depends heavily on the use case, the provided child Positions asset, and the order in which it is queried. You can get the best performance out of this Cache asset by trial and error. An example of a good use case for this asset is at the root of an expensive Positions tree used by a Positions2D/Positions3D Density node.

I recommend that you don’t use this cache everywhere in your Positions trees, but instead place it at (or close to) the root of your Positions asset tree. That said, feel free to experiment.

This cache functions by saving 3D sections of space containing the Positions (points) generated by the child slot. The sections are cubes, and their size is determined by the asset’s SectionSize parameter. The number of sections allowed to be saved in the cache is determined by the CacheSize asset parameter. A safe starting value for the SectionSize parameter would be `32`, and a safe starting value for the CacheSize would be `50`.

Parameters:

- SectionSize: integer (> `0`). Determines the side length of each section cube, in blocks. A safe starting value is `32`.
- CacheSize: integer (>= `0`). Determines how many sections can be saved in memory. A safe starting value is `50`. If set to `0`, the cache is ignored and positions are sourced directly from the child slot.
- Positions: PositionProvider slot. The output of this Positions asset is cached.

---

### [Graph](#graph)

Reads a PositionProvider from the content that graph nodes carry. Every node within Range contributes its positions, and the results are combined. See [GraphContent](/hytale-generator/graph-generator/graph-content/) for how content and layers work.

Parameters:

- GraphGenerator: [GraphGenerator](/hytale-generator/graph-generator/graph-generator/) slot. The graph to read.
- ContentLayer: string. The Positions layer to read from each node's content.

---

### [Imported](#imported)

Imports an exported PositionsProvider.

Parameters:

- Name: string. The exported PositionsProvider.



======================================================================
## Scanner (Raycasting & Surface Probing)
Official URL: https://docs.hytale.com/hytale-generator/scanner/
======================================================================

# Scanner

> Source: [https://docs.hytale.com/hytale-generator/scanner/](https://docs.hytale.com/hytale-generator/scanner/)

**Description:** Scans a local part of the world for valid positions.

---

### [Origin](#origin)

Only scans the origin position.

No parameters.

---

### [Linear](#linear)

Scans linearly block by block.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Range: Integer Range. The range of blocks around the anchor to scan.
- Axis: string (`X` | `Y` | `Z`). Axis to scan along.

---

### [Random](#random)

Scans a line of blocks in a random order determined by the seed.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Range: Integer Range. The range of blocks around the anchor to scan.
- Axis: string (`X` | `Y` | `Z`). Axis to scan along.
- Seed: string. Determines the pseudo-random order.

---

### [Radial](#radial)

Starts at the anchor block, then moves outward from closest to farthest.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Bounds: Integer Bounds. The reach of this Scanner around the anchor.

---

### [Queue](#queue)

Runs all the Scanners in the Queue, one after another.

Parameters:

- Scanners: list of Scanner slots. Scanners to run, in order.

---

### [Imported](#imported)

Imports an exported Scanner.

Parameters:

- Name: string. The exported Scanner.



======================================================================
## Assignments (Prop to Position Mapping)
Official URL: https://docs.hytale.com/hytale-generator/assignments/
======================================================================

# Assignments

> Source: [https://docs.hytale.com/hytale-generator/assignments/](https://docs.hytale.com/hytale-generator/assignments/)

**Description:** Assigns Props to each position in a Positions field.

---

### [Constant](#constant)

Assigns a Prop to all positions.

Parameters:

- Prop: Prop slot.

---

### [FieldFunction](#fieldfunction)

Allows you to select which Props to assign based on a Density field and your configured delimiters. The Density’s value at each position determines which delimiter’s Prop to assign.

Parameters:

- FieldFunction: Density slot.
- Delimiters: list of delimiters. Attaches Props to value ranges of the Density field.


  - Min: decimal number. Lower inclusive bound of the delimiter.
  - Max: decimal number. Higher exclusive bound of the delimiter.
  - Assignments: Assignments asset slot. The Assignments providing the Props to use.

---

### [Sandwich](#sandwich)

Allows you to select which Props to assign based on their vertical (world Y) position and your configured delimiters. Depending on each position’s world height, the matching delimiter’s Prop is assigned.

Parameters:

- Delimiters: list of height delimiters. Each delimiter defines a world height range and its Assignments.


  - MinY: decimal number. Lower inclusive bound of the delimiter.
  - MaxY: decimal number. Higher exclusive bound of the delimiter.
  - Assignments: Assignments asset slot.

---

### [Weighted](#weighted)

Picks which Prop to assign randomly based on weights and a seed.

Parameters:

- Seed: string.
- SkipChance: decimal number (`0.0`–`1.0`). Chance to skip a position without assigning a Prop.
- WeightedAssignments: list of weighted Assignments slots.
- Assignments: Assignments asset slot.

---

### [Imported](#imported)

Imports exported Assignments.

Parameters:

- Name: string. The exported Assignments name.



# ============================================================================
# SECTION: PROPS, PREFABS & RUNTIME STAGES
# ============================================================================



======================================================================
## Prop (Localized Structures & Prefabs)
Official URL: https://docs.hytale.com/hytale-generator/prop/
======================================================================

# Prop

> Source: [https://docs.hytale.com/hytale-generator/prop/](https://docs.hytale.com/hytale-generator/prop/)

**Description:** Generates localized content.

---

### [Cuboid](#cuboid)

Places a cuboid made from the provided materials.

Parameters:

- Bounds: Bounds. Size of the cuboid.
- Material: MaterialProvider slot. The materials the cuboid is made of.

---

### [Manual](#manual)

Places manually configured blocks.

Parameters:

- Blocks: Positioned block. Block relative to the Prop's anchor.

---

### [Locator](#locator)

Locates a valid position using a Pattern and a Scanner.

Parameters:

- Prop: Prop slot. Prop to place at valid positions.
- Pattern: Pattern slot. Defines what valid locations look like.
- Scanner: Scanner slot. Scans relative to the anchor for valid locations.
- PlacementCap: Integer ≥ 0. Limits the number of valid locations in which the child Prop is generated.

---

### [Orienter](#orienter)

Locates valid positions and orients the child Prop accordingly.

Automatically checks the Pattern in different rotations and rotates the child Prop accordingly.

Parameters:

- Prop: Prop slot. Prop to place at valid positions.
- Rotations: list of Rotations. Possible rotations.
- Pattern: Pattern slot. Defines what valid locations look like.
- Scanner: Scanner slot. Scans relative to the anchor for valid locations.
- SelectionMode: String (`FirstValid` | `AllValid` | `RandomValid`). Determines which valid rotation is generated:


  - `FirstValid`: The first valid rotation in the Rotations list.
  - `AllValid`: All valid rotations in the Rotations list.
  - `RandomValid`: Picks one of the valid Rotations at random.
- Seed: String. Determines which position is picked if `RandomValid`.

---

### [Queue](#queue)

Places the first Prop in the list that generates, and skips the rest.

Parameters:

- Props: list of Prop slots. Ordered list making up the queue.

---

### [Mask](#mask)

Masks the blocks placed by the child.

Parameters:

- Prop: Prop slot. Prop to mask.
- Mask: BlockMask. Determines which materials are allowed to be placed.

---

### [StaticRotator](#staticrotator)

Rotates the child Prop by a static Rotation.

Parameters:

- Prop: Prop slot. Prop to rotate.
- Rotation: Rotation. Child Prop's rotation relative to this Prop's direction.

---

### [RandomRotator](#randomrotator)

Rotates the child Prop by a random Rotation.

Parameters:

- Prop: Prop slot. Prop to rotate.
- Rotations: list of Rotations. The rotations to pick from.
- HorizontalRotations: Boolean. Reduces the need to define four extra nodes for all X/Z rotations by adding them all automatically. Don’t use the Rotations pin if you enable this.
- Seed: String. Determines which Rotation to pick.

---

### [DensitySelector](#densityselector)

Selects a child Prop based on a Density field.

The first delimiter whose range contains the Density value at the Prop’s position determines the child Prop to use.

Parameters:

- Delimiters: list of Delimiters. Determines which Prop is selected for which regions of the Density field.
- Density: Density slot. The Density field.

---

### [Density](#density)

Generates a Prop from a Density field and a MaterialProvider asset.

Below is an example of terrain with smaller dirt boulders on top of large stone ones.

Parameters:

- Bounds: Bounds. The maximum reach.
- Density: Density asset slot. Gives shape to the Prop.
- Material: MaterialProvider asset slot. Determines the block types to use.

---

### [Prefab](#prefab)

Places a Prefab from the weighted paths.

Parameters:

- WeightedPrefabPaths: list of weighted paths to files or folders containing prefabs. Each path entry contains the keys below.


  - Path: string. Path to the folder or file to load from.
  - Weight: number (> `0`). Weight for placing a prefab from this path. If the path points to a folder, the weight applies to the folder pick. Once picked, all prefabs inside have an equal chance.

---

### [Union](#union)

Places all the Props in the provided list at the same position, one after another.

Parameters:

- Props: list of Prop slots. Props to place together.

---

### [Offset](#offset)

Offsets the position of the child Prop.

Parameters:

- Offset: 3D integer vector. The direction in which to offset the child Prop’s position.
- Prop: Prop slot.

---

### [Weighted](#weighted)

Picks a Prop to place based on a seed and weights.

Parameters:

- Entries: list of weighted Prop entries. Each entry contains `Weight` and `Prop`.


  - Weight: number (> `0.0`).
  - Prop: Prop slot.
- Seed: string. The seed determining which Prop is picked. This seed also mutates the seed passed to the children.

---

### [PondFiller](#pondfiller)

Fills depressions in the terrain with the provided MaterialProvider.

The screenshots below show a few examples of ponds generated with this Prop.

You can configure the bounding box inside which it will identify and fill existing terrain depressions. The bounding box’s size and shape determine how large the resulting ponds can be.

The diagram below shows how the bounding box defines the outcome.

The ponds that fit inside that bounding box are filled with the MaterialProvider.

The BarrierBlockSet defines what the walls of the depressions are made of.

Important: The performance impact of this Prop can be relatively high depending on the size of its bounding box. Because of that, I recommend optimizing the size of its bounding box for each use case.

Parameters:

- Bounds: Bounds. The Prop's reach.
- BarrierBlockSet: BlockSet asset slot. Defines the types of blocks that can make up the solid terrain, through which the pond material cannot seep.
- FillMaterial: MaterialProvider asset slot. Determines the types of blocks used when filling up the ponds (for example, water).

---

### [Imported](#imported)

Imports an exported Prop.

Parameters:

- Name: string. The exported Prop name.



======================================================================
## Prop Distribution (Positioned Prop Fields)
Official URL: https://docs.hytale.com/hytale-generator/prop-distribution/
======================================================================

# Prop Distribution

> Source: [https://docs.hytale.com/hytale-generator/prop-distribution/](https://docs.hytale.com/hytale-generator/prop-distribution/)

**Description:** Determines a field of Positioned Props.

---

### [Constant](#constant)

Pairs the Prop with every position.

Parameters:

- Positions: PositionProvider slot. Positions to pair with the Prop.
- Prop: Prop slot. The Prop.

---

### [Positions](#positions)

Creates empty positions.

Parameters:

- Positions: PositionProvider slot. The positions.

---

### [Assigned](#assigned)

Assigns all empty positions coming from the child PropDistribution, using the Assignments. All non-empty positions are left unchanged.

Parameters:

- PropDistribution: PropDistribution slot. The child PropDistribution to assign the empty positions from.
- Assignments: Assignments slot. Assigns Props to the empty positions.
- OverrideAllProps: boolean. When enabled, Assignments replace all positions from the child PropDistribution. When disabled, only empty positions are assigned.

---

### [Union](#union)

Combines all children, keeping all their positioned Props.

Parameters:

- PropDistributions: list of PropDistribution slots. The children to combine.

---

### [Anchor](#anchor)

Anchors the origin of the child PropDistribution field to the contextual Anchor, if one exists. For a contextual Anchor to exist, a parent of this node must produce an Anchor.

You can also reverse the effect later in the chain to move the origin back to the world’s origin.

Parameters:

- PropDistribution: PropDistribution slot.
- Reversed: boolean. If `true`, reverses the origin of the child back to the world’s origin (or to the origin before the previous Anchor node).

---

### [Graph](#graph)

Reads a PropDistribution from the content that graph nodes carry. Every node within Range contributes its Props, and the results are combined. See [GraphContent](/hytale-generator/graph-generator/graph-content/) for how content and layers work.

Parameters:

- GraphGenerator: [GraphGenerator](/hytale-generator/graph-generator/graph-generator/) slot. The graph to read.
- ContentLayer: string. The PropDistribution layer to read from each node's content.

---

### [Imported](#imported)

Imports an exported PropDistribution.

Parameters:

- Name: string. The exported PropDistribution name.



======================================================================
## Prop Runtime (Staged Pipeline & Context)
Official URL: https://docs.hytale.com/hytale-generator/prop-runtime/
======================================================================

# Prop Runtime

> Source: [https://docs.hytale.com/hytale-generator/prop-runtime/](https://docs.hytale.com/hytale-generator/prop-runtime/)

**Description:** Generates a Prop Distribution and controls the order of the Props relative to each other.

---

Runtimes place Props in a given order and let them share context, which allows Props to place on, edit, or avoid each other. Each unique Runtime Index number adds another Prop Stage. A Stage's context size directly impacts the Output Size of its prior Stages (Stage 2's context size impacts the Output Sizes of Stages 0 and 1). A Prop Stage's context size is determined by the horizontal reach of its Props (Scanner, Pattern, and the horizontal size of the Prop's content).

# [Context Precision](#context-precision)

In the Runtime node's `ContextPrecision` drop-down menu, you can select the precision at which the context requirement is calculated for each Chunk. Each Context Precision option has benefits and drawbacks, which make it appropriate for some use cases and not for others. Below are the three Context Precision options and their appropriate use cases.

The overhead of each Context Precision option impacts the `Dynamic Bounds Initialization` performance that you find at the top of HytaleGenerator's performance report. Picking the right option is about finding the best compromise between the performance cost of a larger Context Size and the additional `Dynamic Bounds Initialization` overhead. The source of overhead is documented under each option below.

> [!NOTE]

> [!NOTE]

On this page, "Prop Distribution" means everything attached to the Runtime node's `PropDistribution` pin, taken as one unit. "Props" means every Prop that unit can place. A [Union](/hytale-generator/prop-distribution/#union) on that pin does not split its children up. The Runtime measures all of them together, so it optimizes for the whole group instead of each child. To let it optimize for one child alone, move that child to [its own Runtime node](#optimal-runtime-setup).

## [Option: Biome](#option-biome)

`Biome` is the safe default option to start with.

With `Biome` Context Precision, the Runtime's context size is the maximum for the entire Biome's span. This option doesn't check the Prop Distribution at all, which gives it the lowest overhead possible, but it doesn't optimize the context size at the sub-Biome level.

**Use Cases**

- Use `Biome` Context Precision for Props that don't read at all from the environment because those Props don't need any context.
- This option doesn't call Positions at all, so it could benefit Props with very expensive Positions, where the overhead of `Dynamic Bounds Initialization` exceeds the benefits of reducing the context size.

---

## [Option: Chunk](#option-chunk)

With `Chunk` Context Precision, the context size is determined individually for each Chunk, based on whether there are any Props distributed locally. The overhead comes from a single run of the Prop Distribution, which only asks whether the Chunk contains any Prop at all. One run is enough to answer that, so the overhead stays low.

Example of how this option helps reduce the Context Size: in a forested Biome, a Chunk located inside the forest has a larger context size than a Chunk located in a treeless prairie, because in the prairie this option wouldn't find a single tree in its Prop Distribution.

**Use Cases**

The `Chunk` Context Precision is appropriate for the following situations:

- The Prop Distribution has large multi-Chunk gaps without any Props.
- The Prop Distribution has areas where the Props are a Chunk or less apart.
- The Props must read from the world (that is, they have a Locator or Orienter Prop). If the Props don't read from the world, they should use `Biome` Context Precision.

Some example use cases: grass patches, typical forest trees, boulder clusters, pebbles, small to medium-sized decorations.

Beware that an extremely expensive Prop Distribution could have a significant impact on the `Dynamic Bounds Initialization` performance, in which case the `Biome` Context Precision would be more suitable.

---

## [Option: Prop](#option-prop)

With `Prop` Context Precision, the Runtime's context size extends by the exact size needed by the Props distributed locally in each Chunk. This option is the most precise of all three for reducing context size. On the flip side, `Prop` Context Precision has more overhead for densely distributed Props because it computes the Prop Distribution over the entire Chunk's bounds.

**Use Cases**

- Use `Prop` Context Precision for large Props that are more than a few Chunks apart. Large Props increase the context size a lot, and this option helps the most to reduce it.
- The Props must read from the world (that is, they have a Locator or Orienter Prop). If the Props don't read from the world, they should use `Biome` Context Precision.

If the Positions field is extremely expensive or dense (a Chunk or less apart), the overhead from sampling dense Positions and each Prop's bounds could be greater than the benefit from the additional reduction in Context Size. For dense Positions fields, `Chunk` Context Precision could be more suitable.

# [Optimal Runtime Setup](#optimal-runtime-setup)

You can optimize your Prop Runtimes by using separate Runtime nodes for each unique type of Prop Distribution. Separate nodes let you pick the optimal Context Precision for each Prop Distribution, and they let each Runtime optimize itself more efficiently for its Props.

In short, grouping different Prop Distributions under a single Runtime forces that Runtime to work in the least efficient way across all of them, which results in poorer overall performance. It is especially costly if the Props have different sizes, or if the Positions have different scales and properties.

> [!NOTE]

> [!NOTE]

To start optimizing the Context Precision options for your asset, set all the Runtimes to `Biome`. Then test each Runtime with a different value to see whether it improves or hurts performance.

> [!NOTE]

> [!NOTE]

You can use a different Context Precision for each Runtime node, even if the nodes share the same Runtime index.

## [Decision Helper](#decision-helper)

The decision tree below can help you build an intuition for the options and find the optimal Context Precision for your Prop Distribution. Answer each question and follow the arrows until you reach an option.



# ============================================================================
# SECTION: PROCEDURAL GRAPH GENERATOR
# ============================================================================



======================================================================
## Graph Generator Core
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/graph-generator/
======================================================================

# Graph Generator

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/graph-generator/](https://docs.hytale.com/hytale-generator/graph-generator/graph-generator/)

**Description:** Generates a field of interconnected nodes that carry content such as Density, Prop Distribution, Positions and Material Provider.

---

The Graph Generator builds a procedural graph of interconnected nodes. Each node carries content, such as a [Density](/hytale-generator/density/) or a [Prop Distribution](/hytale-generator/prop-distribution/). With a graph you control where that content sits and how it connects.

The graph is built step by step by a list of [Graph Passes](/hytale-generator/graph-generator/graph-pass/). Each pass modifies the graph until it reaches its final form. For example:

1. The first pass places some empty nodes.
2. The second pass connects nodes that are close to each other.
3. The third pass puts content on the nodes.

You control what the graph looks like by editing the Graph Passes and defining their order.

Depending on your Graph Passes, the graph can extend to infinity or stay inside a given area. The Graph Generator splits the graph into a grid of cells. Once generated, each cell is cached to improve performance.

> Graph generation and content generation are two separate steps. Graph generation places the nodes, their connections and the content they carry. Content generation then builds the world content that players see.

## [Parameters](#parameters)

### [Passes](#passes)

- Type: list of [GraphPass](/hytale-generator/graph-generator/graph-pass/) slots

Lists the passes that build the graph, in the order they run. Each pass can read from and write to the graph. It sees the state of the graph as it was built by all the previous passes. The changes it makes to the graph are seen by the following pass in the list.

### [ExportName](#exportname)

- Type: string
- Safe value: `""`

Exports this Graph Generator.

### [ImportName](#importname)

- Type: string
- Safe value: `""`

Imports an exported Graph Generator.

### [CacheCellSize](#cachecellsize)

- Type: decimal Vector3d. Every component must be greater than `0`.
- Safe value: `{"X": 320, "Y": 320, "Z": 320}`

Sets the size of a single cell in the cache grid. There are pros and cons to having larger or smaller cells. The optimal cell size can be found by tweaking and benchmarking. A good general starting size for most applications is from `{320, 320, 320}` to `{700, 320, 700}`.

If the cell size is too small:

- Graph generation overhead is paid more often, which can lower average content generation performance.
- The cache covers a smaller total area, because CacheCapacity counts cells and not volume. Cells are then evicted and regenerated more often.

If the cell size is too large:

- Each cell takes longer to generate. Content generation waits whenever it queries an uncached cell, so its performance becomes uneven.
- Each cached cell costs more memory.
- More of the generated graph area is never explored by players.

### [CacheCapacity](#cachecapacity)

- Type: integer greater than or equal to `1`
- Safe value: `50`

Sets how many cells can stay in memory at once. This value doesn't affect the graph delivery performance, but it can affect the content generation performance.

If the capacity is too small, then the graph gets generated a lot more times than necessary and can dramatically slow down content generation performance.

If the capacity is too large, it can use up a lot more memory than necessary with minimal benefit to performance. Memory usage can be exacerbated by a very large CacheCellSize and/or a graph with a high node density.

A good starting value is `50`.

### [PrintStats](#printstats)

- Type: boolean
- Safe value: `false`

Prints the graph generation performance report to the logs.

### [StatsLabel](#statslabel)

- Type: string
- Safe value: `"UNNAMED"`

Identifies this Graph Generator in the performance report.

### [StatsPrintInterval](#statsprintinterval)

- Type: integer greater than or equal to `0`
- Safe value: `50`

Prints the performance report after this many cells are generated.

## [Performance](#performance)

The Graph Generator's performance has three parts. Together they make up the total cost of using a graph to place content.

> Set PrintStats to `true` to see your own numbers. The Graph Generator then writes a performance report to the logs. The examples below are extracts from that report.

### [1. Building the Graph](#1-building-the-graph)

The total time it takes to generate the graph is the sum of all its passes' execution times. The report gives the times for each pass:

```
Pass 3 :
    Average : 41083 ns / 0.041083 ms
    Max : 96741 ns / 0.096741 ms
    Min : 12309 ns / 0.012309 ms
```

- A pass's input bounds are the region of the graph it needs from its previous passes.
- A pass's output bounds are the region of the graph it is asked to generate.
- A pass's output bounds are directly affected by the input bounds of the following pass. For example, Pass 2 will have the same output bounds as Pass 3's input bounds.
- A pass's work bounds are the region it actually generates: its output bounds plus the context that it and every later pass need.

The features used inside each pass dictate what the pass's context requirements are, which determine how big the input bounds must be. Larger context requirements result in larger input bounds for that pass. See each feature's documentation for more info on how the feature affects the pass's context requirements.

Each pass's execution time scales with the volume of its work bounds. The report gives those bounds for each pass under `Context Bounds`:

```
Context Bounds:
    Total Cell Output Bounds (Blocks) : {x=100.0, y=100.0, z=100.0}
    Pass 1 :
        Increase : {x=120.0, y=120.0, z=120.0}
        Work : {x=380.0, y=380.0, z=380.0}
```

- Total Cell Output Bounds: the size of the cell the Graph Generator must produce. Read the values below relative to it.
- Increase: how much this pass adds to the work bounds, in blocks per axis. It adds to this pass and to every pass that runs before it.
- Work: the size of the region this pass generates, in blocks per axis. It holds the cell, the Increase of this pass, the Increase of every later pass, and the reach of the longest edge.

In the example the cell is 100 blocks per axis and Pass 1 generates 380 blocks per axis. That is 55 times the volume of the cell. The work grows on all three axes, so one large context requirement in a late pass can dominate the cost of every earlier pass.

### [2. Caching and Retrieving the Graph](#2-caching-and-retrieving-the-graph)

The graph is practically infinite, but it is generated in cells of limited size which get cached. An optimal cache configuration reduces the number of times each cell needs to be generated. Ideally each cell needs to generate only once, after which it is retrieved from the cache.

### [3. Generating the Content](#3-generating-the-content)

Finally, the content generation has its own performance cost on top of the graph generation. Several things affect the content generation performance:

- A larger Range on the [content that a node carries](/hytale-generator/graph-generator/graph-content/). Each sampled point then has to consider nodes that are further away.
- Greater number of graph nodes per unit of space (higher graph node density).
- The content itself. For example if the content uses expensive noise or expensive Props.

## [Performance caps](#performance-caps)

Several actions move or place nodes at a distance that a child provider computes while the graph generates. The Graph Generator must know the largest distance an action can reach before it runs. That distance sets how large an area every earlier pass has to generate. Actions of this kind therefore take a cap, such as MaxDistance, MaxOffsetExclusive or MaxProxyDistance.

Set a cap to the smallest value that is still above the largest value its provider can return. A cap that is too large makes every earlier pass generate more than it needs.



======================================================================
## Graph Pass
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/graph-pass/
======================================================================

# Graph Pass

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/graph-pass/](https://docs.hytale.com/hytale-generator/graph-generator/graph-pass/)

**Description:** Builds the graph step by step. Each pass reads and edits the whole graph.

---

Graph Passes read the graph and edit it. The passes run one after another, each on the whole graph.

Depending on its type and configuration, a Graph Pass can expand its input bounds. The input bounds are the region a pass must read. An earlier pass must generate whatever a later pass reads. Expanding the input bounds therefore increases the output bounds of every preceding pass. See [Graph Generator](/hytale-generator/graph-generator/graph-generator/#1-building-the-graph) for more.

Parameters common to all GraphPass types:

- Skip: boolean. Skips this pass and its children. The graph is unchanged.
- ExportAs: string. Exports this GraphPass under this name, so an Imported GraphPass can reference it.
- StatsLabel: string. The label used to identify this Graph Pass in the performance report.

---

### [Manual](#manual)

The Manual pass lets you place nodes manually.

Names resolve inside this pass. The pass ignores any other name, and it ignores a node that names itself. The order of the list does not matter, so a connection can name a node that appears later.

Names do not have to be unique, and a duplicate causes no error. The pass still creates every node in the list. Only the last node with a given name can receive connections. All the nodes with an empty Name collide in the same way.

The pass creates one edge for each connected pair. A connection listed on both nodes therefore creates a single edge.

The pass skips a node when its Position is outside the region it is asked to generate. Connections to a skipped node are dropped.

Performance impact: Low. Scales with the number of nodes.

Input bounds impact: None.

Parameters:

- Nodes: list of Node entries.


  - Position: decimal Vector3d.
  - Content: [GraphContent](/hytale-generator/graph-generator/graph-content/) slot.
  - Name: string. Identifies this node inside this pass.
  - Connections: optional list of strings. Names of the nodes this node connects to.

---

### [Empty](#empty)

Does nothing.

Performance impact: None.

Input bounds impact: None.

No parameters.

---

### [NodeAction](#nodeaction)

Runs a [NodeAction](/hytale-generator/graph-generator/node-action/) on the nodes.

Performance impact: Depends on the performance cost of the child NodeAction.

Input bounds impact: Depends on the input bounds of the child NodeAction.

Parameters:

- NodeAction: NodeAction slot.

---

### [EdgeAction](#edgeaction)

Runs an [EdgeAction](/hytale-generator/graph-generator/edge-action/) on the edges.

Performance impact: Depends on the performance cost of the child EdgeAction.

Input bounds impact: Depends on the input bounds of the child EdgeAction.

Parameters:

- EdgeAction: EdgeAction slot.

---

### [Positions](#positions)

Places a node for each position. The new nodes have empty content.

Performance impact: Depends on the performance cost of the child PositionProvider and scales with the number of positions.

Input bounds impact: None.

Parameters:

- Positions: [PositionProvider](/hytale-generator/positions-provider/) slot. Determines where to place the new nodes.

---

### [Imported](#imported)

Imports an exported GraphPass.

Performance impact: Inherits the performance cost of the imported asset.

Input bounds impact: Inherits the input bounds of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Graph Content
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/graph-content/
======================================================================

# Graph Content

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/graph-content/](https://docs.hytale.com/hytale-generator/graph-generator/graph-content/)

**Description:** The content a graph node carries, split by content type into named layers.

---

A node carries its content as a GraphContent asset. A [ContentSupplier](/hytale-generator/graph-generator/content-supplier/) decides which GraphContent a node gets, and a [GraphPass](/hytale-generator/graph-generator/graph-pass/) attaches it. The node editor shows this asset as a NodeContent node.

GraphContent holds four separate lists, one for each content type: Density, Material, Prop Distribution and Positions. A node can carry several entries of the same type, so one graph can feed several readers.

## [Content entries](#content-entries)

All four lists hold entries with the same three parameters:

- The content itself, in a slot named after its type: Density, MaterialProvider, PropDistribution or Positions.
- Range: decimal number greater than or equal to `0`. The radius around the node where the content applies.
- ContentLayer: string. The layer that this entry belongs to.

Range is a radius in blocks. A reader ignores the entry at any position further from the node than Range. A Range of `0` therefore reaches nowhere.

## [Layers](#layers)

A layer is a name. The content entry carries one and the asset that reads the graph carries one. The two names must be equal for the reader to find the entry. The comparison is exact and case sensitive.

The default layer name is `""` on both sides, so an unnamed entry pairs with an unnamed reader.

Layers let one graph serve several readers. A node can carry one Density entry named `caves` and another named `overhangs`. Each Graph Density then reads only its own layer.

Layer names are scoped per content type. Each list is searched on its own, so a Density layer named `caves` and a Material layer named `caves` are unrelated.

If one list holds two entries with the same layer name, the reader uses the first entry.

> If no entry matches the reader's layer name, the reader gets empty content. Nothing is logged and generation continues. A misspelled layer name therefore gives you a graph that generates as normal and adds nothing to the world.

## [Readers](#readers)

One asset per content type reads the content from a graph. Each one is the `Graph` type of its own family, and each one takes a ContentLayer:

- [Graph Density](/hytale-generator/density/#graph) blends every entry in range. Entries closer to the sampled position get more weight.
- [Graph Material Provider](/hytale-generator/material-provider/#graph) uses the entry of the nearest node in range.
- [Graph Prop Distribution](/hytale-generator/prop-distribution/#graph) combines the props from every entry in range.
- [Graph Positions Provider](/hytale-generator/positions-provider/#graph) combines the positions from every entry in range.

## [Parameters](#parameters)

### [ExportName](#exportname)

- Type: string
- Safe value: `""`

Exports this GraphContent under this name. Another GraphContent can then set ImportName to the same name. Two exports under one name log a warning.

### [ImportName](#importname)

- Type: string
- Safe value: `""`

Imports the GraphContent exported under this name. The imported content replaces every other parameter on this asset. If no export matches the name, the node gets the default Content.

### [DensityContent](#densitycontent)

- Type: list of content entries
- Safe value: `[]`

Holds the Density fields that the node carries. A Graph Density reads them.

### [MaterialContent](#materialcontent)

- Type: list of content entries
- Safe value: `[]`

Holds the Material Providers that the node carries. A Graph Material Provider reads them.

### [PropDistributionContent](#propdistributioncontent)

- Type: list of content entries
- Safe value: `[]`

Holds the Prop Distributions that the node carries. A Graph Prop Distribution reads them.

### [PositionsContent](#positionscontent)

- Type: list of content entries
- Safe value: `[]`

Holds the Positions assets that the node carries. A Graph Positions Provider reads them.

### [ContentTags](#contenttags)

- Type: list of strings
- Safe value: `[""]`

Tags the content, so a Set [ContentPredicate](/hytale-generator/graph-generator/content-predicate/) can test for it. The default is one empty tag.



======================================================================
## Node Selector
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/node-selector/
======================================================================

# Node Selector

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/node-selector/](https://docs.hytale.com/hytale-generator/graph-generator/node-selector/)

**Description:** Defines a subset of nodes.

---

Defines a subset of nodes. Actions and other selectors use a NodeSelector to narrow which nodes they run on.

Parameters common to all NodeSelector types:

- Skip: boolean. Skips this selector and its children. Every node is selected.
- ExportAs: string. Exports this NodeSelector under this name, so an Imported NodeSelector can reference it.

---

### [All](#all)

Selects all nodes.

Performance impact: None.

Input bounds impact: None.

No parameters.

---

### [ConnectionCount](#connectioncount)

Selects the nodes whose connection count is in the Counts list.

Performance impact: Low.

Input bounds impact: Scales with the length of the longest possible edge at this [Graph Pass](/hytale-generator/graph-generator/graph-pass/).

Parameters:

- Counts: list of integers. List of selectable count values.

---

### [Content](#content)

Selects the nodes with Content approved by the [ContentPredicate](/hytale-generator/graph-generator/content-predicate/).

Performance impact: Low.

Input bounds impact: None.

Parameters:

- ContentPredicate: ContentPredicate slot. Determines which nodes are selected.

---

### [DensityDelimited](#densitydelimited)

Selects the nodes where Density's value falls within the Delimiters.

Performance impact: Scales with the performance cost of the child Density and the number of Delimiters.

Input bounds impact: None.

Parameters:

- Density: [Density](/hytale-generator/density/) slot. Determines the Density value at each node's position.
- Delimiters: list of decimal Ranges. Defines the valid Density values.

---

### [NeighborNodes](#neighbornodes)

Selects the node when at least SelectedNeighborsThreshold of its neighbors are selected by the child NodeSelector.

Performance impact: Depends on the performance cost of the child NodeSelector and the average number of edges per node.

Input bounds impact: Scales with the length of the longest possible edge at this Graph Pass and the input bounds of the child NodeSelector.

Parameters:

- NodeSelector: NodeSelector slot. Selects the node's neighbors.
- SelectedNeighborsThreshold: integer. Determines the minimum number of neighbors that must be selected to select this node. Use `-1` to require all of them, which never selects a node without neighbors.

---

### [NeighborEdges](#neighboredges)

Works like NeighborNodes, except the child selector runs on the connected edges of the node instead of the neighbors.

Performance impact: Depends on the performance cost of the child EdgeSelector and the average number of edges per node.

Input bounds impact: Scales with the length of the longest possible edge at this Graph Pass and the input bounds of the child EdgeSelector.

Parameters:

- EdgeSelector: [EdgeSelector](/hytale-generator/graph-generator/edge-selector/) slot. Selects the node's connected edges.
- SelectedNeighborsThreshold: integer. Counts connected edges here, not neighbors. Use `-1` to require all of them.

---

### [Random](#random)

Gives every node an independent chance of being selected.

Performance impact: Low.

Input bounds impact: None.

Parameters:

- Chance: decimal number greater than or equal to `0.0`. The probability that any given node is selected, from `0.0` to `1.0`. A value of `1.0` or more selects every node.
- Seed: string.

---

### [Not](#not)

Inverts the child's selection.

Performance impact: Inherits the performance cost of the child NodeSelector.

Input bounds impact: Inherits the input bounds of the child NodeSelector.

Parameters:

- NodeSelector: NodeSelector slot.

---

### [And](#and)

Selects the node if and only if all of its children select it.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to fail, because this NodeSelector stops calling children once one fails to select the node.

Input bounds impact: Inherits the largest input bounds of all its child NodeSelectors.

Parameters:

- NodeSelectors: list of NodeSelector slots.

---

### [Or](#or)

Selects the node if and only if any one of its children selects it.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to succeed, because this NodeSelector stops calling children once one selects the node.

Input bounds impact: Inherits the largest input bounds of all its child NodeSelectors.

Parameters:

- NodeSelectors: list of NodeSelector slots.

---

### [Imported](#imported)

Imports an exported NodeSelector.

Performance impact: Inherits the performance cost of the imported asset.

Input bounds impact: Inherits the input bounds of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Node Action
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/node-action/
======================================================================

# Node Action

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/node-action/](https://docs.hytale.com/hytale-generator/graph-generator/node-action/)

**Description:** Actions that run on graph nodes.

---

Actions that run on graph nodes. Each NodeAction runs on one node at a time. The text below refers to it as the node.

Parameters common to all NodeAction types:

- Skip: boolean. Skips this action and its children. It does nothing.
- ExportAs: string. Exports this NodeAction under this name, so an Imported NodeAction can reference it.

---

### [ConnectedEdges](#connectededges)

Runs the [EdgeAction](/hytale-generator/graph-generator/edge-action/) on a fraction of the node's connected edges, set by the Ratio.

Performance impact: Low to moderate. Scales with the average number of edges per node and the performance cost of the child EdgeAction.

Input bounds impact: Depends on the input bounds of the child EdgeAction.

Parameters:

- EdgeAction: EdgeAction slot. Runs on the connected edges.
- Ratio: optional decimal number greater than or equal to `0.0`. Default `1.0`. The fraction of connected edges the EdgeAction runs on, rounded down. A Ratio of `0.5` on a node with 5 connected edges runs on 2 edges. A value of `1.0` or more runs on all of them.
- Seed: optional string. Determines which connected edges are randomly picked to run on if the ratio is less than `1.0`.

---

### [ConnectedNodes](#connectednodes)

Runs the NodeAction on a fraction of the node's connected nodes, set by the Ratio.

Performance impact: Low to moderate. Scales with the average number of edges per node and the performance cost of the child NodeAction.

Input bounds impact: Depends on the input bounds of the child NodeAction.

Parameters:

- NodeAction: NodeAction slot. Runs on the connected nodes.
- Ratio: optional decimal number greater than or equal to `0.0`. Default `1.0`. The fraction of connected nodes the NodeAction runs on, rounded down. A Ratio of `0.5` on a node with 5 connected nodes runs on 2 nodes. A value of `1.0` or more runs on all of them.
- Seed: optional string. Determines which connected nodes are randomly picked to run on if the ratio is less than `1.0`.

---

### [Content](#content)

Attaches Content to nodes.

Performance impact: Low.

Input bounds impact: None.

Parameters:

- Content: [ContentSupplier](/hytale-generator/graph-generator/content-supplier/) slot. The Content to attach to the nodes.

---

### [ContentCopy](#contentcopy)

Copies the Content of a connected node onto the node. If more than one connected node passes the predicate, the Content to copy is picked at random.

Performance impact: Low to moderate. Scales with the average number of connected nodes each node has.

Input bounds impact: Scales with the length of the longest possible edge at this [Graph Pass](/hytale-generator/graph-generator/graph-pass/).

Parameters:

- ContentPredicate: [ContentPredicate](/hytale-generator/graph-generator/content-predicate/) slot. Filters which nodes can be copied from.
- Seed: string. Determines the random outcome.

---

### [Delete](#delete)

Deletes the node.

Performance impact: Low.

Input bounds impact: None.

No parameters.

---

### [Empty](#empty)

Does nothing.

Performance impact: None.

Input bounds impact: None.

No parameters.

---

### [Jitter2d](#jitter2d)

Moves the node a random distance in a random horizontal direction, up to the Magnitude.

Performance impact: Low.

Input bounds impact: Scales with the Magnitude.

Parameters:

- Magnitude: decimal number greater than or equal to `0.0`. The maximum distance the node can move.
- Seed: string.

---

### [Jitter3d](#jitter3d)

Moves the node a random distance in a random direction, up to the Magnitude.

Performance impact: Low.

Input bounds impact: Scales with the Magnitude.

Parameters:

- Magnitude: decimal number greater than or equal to `0.0`. The maximum distance the node can move.
- Seed: string.

---

### [Move](#move)

Moves the node by the vector.

MaxDistance is a performance cap on the vector's length. If the vector reaches MaxDistance, the node does not move at all and nothing is logged. A MaxDistance set too low therefore disables the action silently.

Performance impact: Low on its own. Depends on the performance cost of the child VectorProvider.

Input bounds impact: Scales with the MaxDistance.

Parameters:

- Vector: [VectorProvider](/hytale-generator/vector-provider/) slot. Moves the node.
- MaxDistance: decimal number greater than or equal to `0.0`. Performance cap on the vector's length. The node does not move if the vector reaches this length.

---

### [ProximityConnector](#proximityconnector)

Connects the node to the neighboring nodes that are selected and within range. Cap sets how many connections it can create.

Performance impact: High. Scales with the number of nodes in the graph and with the Range.

Input bounds impact: Scales with the Range.

Parameters:

- Range: decimal number greater than or equal to `0.0`. Maximum connection distance.
- NodeSelector: [NodeSelector](/hytale-generator/graph-generator/node-selector/) slot. Filters which neighboring nodes can be connected to.
- Cap: optional integer greater than or equal to `-1`. Default `-1`. The maximum number of connections allowed, or `-1` for uncapped.
- Seed: optional string. Determines the random picking outcome when capped.

---

### [Selector](#selector)

Runs the NodeAction on a subset of nodes defined by the NodeSelector.

Performance impact: Depends on the performance cost of the child NodeSelector and NodeAction.

Input bounds impact: Depends on the input bounds of the child NodeSelector and NodeAction.

Parameters:

- NodeSelector: NodeSelector slot. Defines the subset of nodes.
- NodeAction: NodeAction slot. Runs on each node in the subset.

---

### [Spawner](#spawner)

Spawns a cluster of new nodes around the node. If CreateEdges is enabled, an edge connects the node to every new node in the cluster.

The cluster has an origin of its own. The Offset vector sets where that origin sits relative to the node. The Positions then place the new nodes relative to the origin.

MaxOffsetExclusive is a performance cap on the Offset length. The cap is exclusive, so an Offset equal to it is already out of range. If Offset reaches MaxOffsetExclusive, the cluster does not spawn at all and nothing is logged. A MaxOffsetExclusive set too low therefore disables the action silently.

Performance impact: Medium to high. Depends heavily on the size of the cluster and on the performance cost of the Positions and Offset slots.

Input bounds impact: Scales with MaxOffsetExclusive and the size of ClusterBounds.

Parameters:

- Offset: VectorProvider slot. Offsets the cluster's origin relative to the node.
- Positions: [PositionProvider](/hytale-generator/positions-provider/) slot. Determines the new nodes' positions relative to the cluster's origin.
- Content: ContentSupplier slot. The Content attached to the new nodes.
- ClusterBounds: decimal Bounds3d. Bounds the cluster around its origin. New nodes appear only inside these bounds, so keep them as small as the cluster needs.
- MaxOffsetExclusive: decimal number greater than or equal to `0.0`. Performance cap on the Offset length.
- CreateEdges: boolean. If enabled, an edge connects the node to every new node.

---

### [Weighted](#weighted)

Picks a NodeAction to run from a weighted list.

Performance impact: Inherits the performance cost of its child NodeActions, averaged by weight.

Input bounds impact: Inherits the largest input bounds of all its child NodeActions.

Parameters:

- Entries: list of entries.


  - Weight: decimal number greater than or equal to `0.0`. The chance to pick this entry, relative to the sum of every Weight in the list.
  - NodeAction: NodeAction slot.
- Seed: string.

---

### [Imported](#imported)

Imports an exported NodeAction.

Performance impact: Inherits the performance cost of the imported asset.

Input bounds impact: Inherits the input bounds of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Edge Selector
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/edge-selector/
======================================================================

# Edge Selector

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/edge-selector/](https://docs.hytale.com/hytale-generator/graph-generator/edge-selector/)

**Description:** Defines a subset of edges.

---

Defines a subset of edges. Actions and other selectors use an EdgeSelector to narrow which edges they run on.

Parameters common to all EdgeSelector types:

- Skip: boolean. Skips this selector and its children. Every edge is selected.
- ExportAs: string. Exports this EdgeSelector under this name, so an Imported EdgeSelector can reference it.

---

### [All](#all)

Selects all edges.

Performance impact: None.

Input bounds impact: None.

No parameters.

---

### [Angle](#angle)

Selects the edges whose angle to the Reference vector is within the Delimiters.

The measurement ignores the direction of the edge. An angle above `90` degrees folds to `180` minus itself, so both ends of an edge give the same result.

Performance impact: Low.

Input bounds impact: Scales with the length of the longest possible edge at this [Graph Pass](/hytale-generator/graph-generator/graph-pass/).

Parameters:

- Reference: [VectorProvider](/hytale-generator/vector-provider/) slot. The vector to measure against, sampled at the middle of the edge.
- Delimiters: list of decimal Ranges. Valid angles in degrees, from `0` to `90`.

---

### [Length](#length)

Selects the edges whose length is within the Delimiters.

Performance impact: Low.

Input bounds impact: Scales with the length of the longest possible edge at this Graph Pass.

Parameters:

- Delimiters: list of decimal Ranges. Valid lengths.

---

### [Nodes](#nodes)

Selects the edge if its two nodes are also selected according to the Operator.

- If Operator is AND: both nodes need to be selected.
- If Operator is OR: only one node needs to be selected.

Performance impact: Depends on the performance cost of the child NodeSelector.

Input bounds impact: Depends on the input bounds of the child NodeSelector.

Parameters:

- NodeSelector: [NodeSelector](/hytale-generator/graph-generator/node-selector/) slot. Selects the nodes of the edge.
- Operator: string (`AND` | `OR`). Sets whether both nodes must be selected or only one.

---

### [Random](#random)

Gives every edge an independent chance of being selected.

Performance impact: Low.

Input bounds impact: None.

Parameters:

- Chance: decimal number greater than or equal to `0.0`. The probability that any given edge is selected, from `0.0` to `1.0`. A value of `1.0` or more selects every edge.
- Seed: string.

---

### [Not](#not)

Inverts the child's selection.

Performance impact: Inherits the performance cost of the child EdgeSelector.

Input bounds impact: Inherits the input bounds of the child EdgeSelector.

Parameters:

- EdgeSelector: EdgeSelector slot.

---

### [And](#and)

Selects the edge if and only if all of its children select it.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to fail, because this EdgeSelector stops calling children once one fails to select the edge.

Input bounds impact: Inherits the largest input bounds of all its child EdgeSelectors.

Parameters:

- EdgeSelectors: list of EdgeSelector slots.

---

### [Or](#or)

Selects the edge if and only if any one of its children selects it.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to succeed, because this EdgeSelector stops calling children once one selects the edge.

Input bounds impact: Inherits the largest input bounds of all its child EdgeSelectors.

Parameters:

- EdgeSelectors: list of EdgeSelector slots.

---

### [Imported](#imported)

Imports an exported EdgeSelector.

Performance impact: Inherits the performance cost of the imported asset.

Input bounds impact: Inherits the input bounds of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Edge Action
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/edge-action/
======================================================================

# Edge Action

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/edge-action/](https://docs.hytale.com/hytale-generator/graph-generator/edge-action/)

**Description:** Actions that run on graph edges (connections between two nodes).

---

Actions that run on graph edges (connections between two nodes). Each EdgeAction runs on one edge at a time. The text below refers to it as the edge.

Parameters common to all EdgeAction types:

- Skip: boolean. Skips this action and its children. It does nothing.
- ExportAs: string. Exports this EdgeAction under this name, so an Imported EdgeAction can reference it.

---

### [Delete](#delete)

Deletes the edge.

Performance impact: Low.

Input bounds impact: None.

No parameters.

---

### [Empty](#empty)

Does nothing.

Performance impact: None.

Input bounds impact: None.

No parameters.

---

### [Nodes](#nodes)

Runs a [NodeAction](/hytale-generator/graph-generator/node-action/) on the two nodes of the edge.

Performance impact: Low.

Input bounds impact: Depends on the input bounds of the child NodeAction.

Parameters:

- NodeAction: NodeAction slot. Runs on both nodes of the edge.

---

### [Proxy](#proxy)

Inserts a proxy node positioned relative to a host node. This splits the edge in two. The action controls the angle at which an edge connects to the host node, and it sets the content of the proxy node.

Example use-cases:

- A cavern room that tunnels only enter through its horizontal sides, never from a sharp angle at the top.
- The roads approaching a POI are thick and reinforced while the roads farther away are thin and patchy.

The HostNodeSelector picks the host nodes. The action needs one host, so it skips an edge where neither of the two nodes is selected. If both nodes are selected, the action inserts one proxy node for each of them, which splits the edge in three.

The Normal is a vector from the host node, and it sets the reference axis for both angles. If the Normal is a zero vector, the action uses straight up instead.

AngleToNormal, SpinAngle and ProxyDistance are [Density](/hytale-generator/density/) slots. A Density is a field of decimal values, so a Density slot supplies a number wherever one is needed.

The AngleToNormal is the angle between the Normal and the new edge from the host node to the proxy node.

The SpinAngle rotates the proxy node around the Normal. The angle is measured from the direction of the original edge.

The ProxyDistance determines how far the proxy node is from the host node. MaxProxyDistance is a performance cap on that distance. A ProxyDistance above the cap is clamped to it, so the proxy node is still created.

Performance impact: Moderate to high. Depends on the performance cost of the Normal, AngleToNormal, SpinAngle and ProxyDistance slots.

Input bounds impact: Scales with the MaxProxyDistance and the input bounds of the child HostNodeSelector.

Parameters:

- HostNodeSelector: [NodeSelector](/hytale-generator/graph-generator/node-selector/) slot. Determines the host nodes.
- Normal: [VectorProvider](/hytale-generator/vector-provider/) slot. Sets the reference axis, as a vector from the host node.
- AngleToNormal: Density slot. Angle in degrees from the Normal.
- SpinAngle: Density slot. Angle in degrees around the Normal.
- ProxyDistance: Density slot. Distance between the proxy node and the host node.
- MaxProxyDistance: decimal number greater than or equal to `0.0`. Performance cap on the proxy distance. Larger distances are clamped to this value.
- ProxyContent: [ContentSupplier](/hytale-generator/graph-generator/content-supplier/) slot. Content to use for the proxy node.
- Seed: string.

---

### [Selector](#selector)

Runs the child EdgeAction on a subset of the edges defined by its [EdgeSelector](/hytale-generator/graph-generator/edge-selector/).

Performance impact: Depends on the performance cost of the child EdgeSelector and EdgeAction.

Input bounds impact: Depends on the input bounds of the child EdgeSelector and EdgeAction.

Parameters:

- EdgeSelector: EdgeSelector slot. Defines the edge subset.
- EdgeAction: EdgeAction slot. Action to run on the edge subset.

---

### [Splitter](#splitter)

Inserts new nodes along the edge, evenly spaced, and connects them in a chain. One node therefore replaces the edge with two edges, two nodes with three, and so on.

Performance impact: Low to moderate. Scales with the NodeCount.

Input bounds impact: None.

Parameters:

- NodeCount: integer greater than or equal to `0`. Number of nodes to insert. A NodeCount of `0` leaves the edge unchanged.
- Content: ContentSupplier slot. Content of the newly inserted nodes.

---

### [Imported](#imported)

Imports an exported EdgeAction.

Performance impact: Inherits the performance cost of the imported asset.

Input bounds impact: Inherits the input bounds of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Content Predicate
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/content-predicate/
======================================================================

# Content Predicate

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/content-predicate/](https://docs.hytale.com/hytale-generator/graph-generator/content-predicate/)

**Description:** Tests the Content attached to a graph node.

---

Tests the Content attached to a graph node. A ContentPredicate reads only the node's ContentTags, so it never samples the world. No type on this page affects the input bounds, so the entries below list only a performance impact.

Parameters common to all ContentPredicate types:

- Skip: boolean. Skips this predicate and its children. The test always passes.
- ExportAs: string. Exports this ContentPredicate under this name, so an Imported ContentPredicate can reference it.

---

### [Constant](#constant)

Always returns the Value, whatever the node's Content is.

Performance impact: None.

Parameters:

- Value: boolean. The result this predicate always returns.

---

### [Set](#set)

Passes when the node's Content carries any one of the ContentTags.

Performance impact: Low.

Parameters:

- ContentTags: list of strings. The tags to look for on the node's [GraphContent](/hytale-generator/graph-generator/graph-content/).

---

### [Not](#not)

Inverts the child's result.

Performance impact: None.

Parameters:

- ContentPredicate: ContentPredicate slot.

---

### [And](#and)

Passes if and only if all of its children pass.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to fail, because this ContentPredicate stops calling children once one fails.

Parameters:

- ContentPredicates: list of ContentPredicate slots.

---

### [Or](#or)

Passes if and only if any one of its children passes.

Performance impact: In the worst case, the sum of its children's costs. Put the cheapest children first, and those most likely to pass, because this ContentPredicate stops calling children once one passes.

Parameters:

- ContentPredicates: list of ContentPredicate slots.

---

### [Imported](#imported)

Imports an exported ContentPredicate.

Performance impact: Inherits the performance cost of the imported asset.

Parameters:

- Name: string. Name of the exported asset.



======================================================================
## Content Supplier
Official URL: https://docs.hytale.com/hytale-generator/graph-generator/content-supplier/
======================================================================

# Content Supplier

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/content-supplier/](https://docs.hytale.com/hytale-generator/graph-generator/content-supplier/)

**Description:** Resolves what Content to supply for a given context.

---

Resolves what Content to supply for a given context. A ContentSupplier reads only the node it supplies, so it never samples the world. No type on this page affects the input bounds, so the entries below list only a performance impact.

Parameters common to all ContentSupplier types:

- Skip: boolean. Skips this supplier and its children. It supplies the default Content.
- ExportAs: string. Exports this ContentSupplier under this name, so an Imported ContentSupplier can reference it.

---

### [Constant](#constant)

Always supplies the same Content.

Performance impact: None.

Parameters:

- Content: [GraphContent](/hytale-generator/graph-generator/graph-content/) slot. The Content to supply.

---

### [Weighted](#weighted)

Picks the Content from a weighted list of ContentSuppliers.

Performance impact: Inherits the performance cost of its child ContentSuppliers, averaged by weight.

Parameters:

- Entries: list of entries.


  - Weight: decimal number greater than or equal to `0.0`. The chance to pick this entry, relative to the sum of every Weight in the list.
  - ContentSupplier: ContentSupplier slot.
- Seed: string.

---

### [Imported](#imported)

Imports an exported ContentSupplier.

Performance impact: Inherits the performance cost of the imported asset.

Parameters:

- Name: string. Name of the exported asset.

