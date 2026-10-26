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
