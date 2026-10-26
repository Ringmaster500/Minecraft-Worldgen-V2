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
