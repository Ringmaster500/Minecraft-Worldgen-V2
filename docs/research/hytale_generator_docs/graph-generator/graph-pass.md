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
