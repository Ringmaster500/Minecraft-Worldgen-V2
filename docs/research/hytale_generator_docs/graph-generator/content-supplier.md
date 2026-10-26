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
