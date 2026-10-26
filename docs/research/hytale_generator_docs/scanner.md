# Scanner

> Source: [https://docs.hytale.com/hytale-generator/scanner/](https://docs.hytale.com/hytale-generator/scanner/)

**Description:** Scans a local part of the world for valid positions.

---

### [Origin](#origin)

Only scans the origin position.

No parameters.

---

### [Linear](#linear)

Scans linearly block by block.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Range: Integer Range. The range of blocks around the anchor to scan.
- Axis: string (`X` | `Y` | `Z`). Axis to scan along.

---

### [Random](#random)

Scans a line of blocks in a random order determined by the seed.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Range: Integer Range. The range of blocks around the anchor to scan.
- Axis: string (`X` | `Y` | `Z`). Axis to scan along.
- Seed: string. Determines the pseudo-random order.

---

### [Radial](#radial)

Starts at the anchor block, then moves outward from closest to farthest.

Parameters:

- Scanner: Optional Scanner slot. The child Scanner to run for every block scanned by this Scanner. Not used if left empty.
- Bounds: Integer Bounds. The reach of this Scanner around the anchor.

---

### [Queue](#queue)

Runs all the Scanners in the Queue, one after another.

Parameters:

- Scanners: list of Scanner slots. Scanners to run, in order.

---

### [Imported](#imported)

Imports an exported Scanner.

Parameters:

- Name: string. The exported Scanner.
