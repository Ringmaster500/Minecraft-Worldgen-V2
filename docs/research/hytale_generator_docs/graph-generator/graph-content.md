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
