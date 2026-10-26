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
