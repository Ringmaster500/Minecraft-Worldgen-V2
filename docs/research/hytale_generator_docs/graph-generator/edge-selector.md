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
