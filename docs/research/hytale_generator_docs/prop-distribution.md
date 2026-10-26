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
