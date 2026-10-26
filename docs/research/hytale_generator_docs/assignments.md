# Assignments

> Source: [https://docs.hytale.com/hytale-generator/assignments/](https://docs.hytale.com/hytale-generator/assignments/)

**Description:** Assigns Props to each position in a Positions field.

---

### [Constant](#constant)

Assigns a Prop to all positions.

Parameters:

- Prop: Prop slot.

---

### [FieldFunction](#fieldfunction)

Allows you to select which Props to assign based on a Density field and your configured delimiters. The Density’s value at each position determines which delimiter’s Prop to assign.

Parameters:

- FieldFunction: Density slot.
- Delimiters: list of delimiters. Attaches Props to value ranges of the Density field.


  - Min: decimal number. Lower inclusive bound of the delimiter.
  - Max: decimal number. Higher exclusive bound of the delimiter.
  - Assignments: Assignments asset slot. The Assignments providing the Props to use.

---

### [Sandwich](#sandwich)

Allows you to select which Props to assign based on their vertical (world Y) position and your configured delimiters. Depending on each position’s world height, the matching delimiter’s Prop is assigned.

Parameters:

- Delimiters: list of height delimiters. Each delimiter defines a world height range and its Assignments.


  - MinY: decimal number. Lower inclusive bound of the delimiter.
  - MaxY: decimal number. Higher exclusive bound of the delimiter.
  - Assignments: Assignments asset slot.

---

### [Weighted](#weighted)

Picks which Prop to assign randomly based on weights and a seed.

Parameters:

- Seed: string.
- SkipChance: decimal number (`0.0`–`1.0`). Chance to skip a position without assigning a Prop.
- WeightedAssignments: list of weighted Assignments slots.
- Assignments: Assignments asset slot.

---

### [Imported](#imported)

Imports exported Assignments.

Parameters:

- Name: string. The exported Assignments name.
