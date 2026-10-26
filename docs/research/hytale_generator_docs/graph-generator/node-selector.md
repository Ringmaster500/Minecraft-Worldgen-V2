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
