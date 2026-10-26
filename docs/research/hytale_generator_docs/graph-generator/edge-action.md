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
