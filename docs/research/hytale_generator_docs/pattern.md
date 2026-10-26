# Pattern

> Source: [https://docs.hytale.com/hytale-generator/pattern/](https://docs.hytale.com/hytale-generator/pattern/)

**Description:** Validates a world location based on its Material composition and other criteria.

---

### [Constant](#constant)

Gives the same result at every position. It always validates, or it never validates.

Parameters:

- Value: boolean. If `true`, the Pattern validates every position. If `false`, it validates no position.

---

### [BlockType](#blocktype)

Checks against the block's Material.

Parameters:

- Material: block Material.

---

### [BlockSet](#blockset)

Checks if the block's Material belongs to the BlockSet.

Parameters:

- BlockSet: BlockSet slot.

---

### [Offset](#offset)

Offsets the child Pattern by the given vector.

Parameters:

- Pattern: Pattern slot.
- Offset: 3D integer vector. The direction to offset the child Pattern in.

---

### [Rotator](#rotator)

Rotates the child Pattern.

Parameters:

- Pattern: Pattern slot.
- Rotation: Rotation. The child's rotation.

---

### [Floor](#floor)

Checks if there is a floor below the position. The Origin validates the position and the Floor validates right under the position. For a simple Floor configuration, use BlockType or BlockSet Patterns in those slots.

Parameters:

- Floor: Pattern slot. Validates the block under the Origin position.
- Origin: Pattern slot. Validates the block at the Origin position.

---

### [Ceiling](#ceiling)

Checks if there is a ceiling above the world position. The Origin validates the position and the Ceiling validates right above the position. For a simple Ceiling configuration, use BlockType or BlockSet Patterns in those slots.

Parameters:

- Ceiling: Pattern slot. Validates the coordinate above the Origin position.
- Origin: Pattern slot. Validates the coordinate at the Origin position.

---

### [Wall](#wall)

Checks if there is a wall next to the world position. For a simple Wall configuration, use BlockType or BlockSet Patterns in those slots. You can specify which directions are valid for the wall using the `N`, `S`, `E`, `W` string values.

The size of the wall can be 1x1 blocks or larger.

Parameters:

- Wall: Pattern slot. Validates the coordinate next to the Origin position.
- Origin: Pattern slot. Validates the coordinate at the Origin position.
- Directions: list of string directional values. “N”, “S”, “E” or “W”.
- RequireAllDirections: boolean. If `true` then all the directions you put in the list need to validate for the Pattern to validate a position. Otherwise only one of the list directions needs to validate.

---

### [Surface](#surface)

Validates if presented with a surface. A surface is a transition from one set of materials to another. For example, a soil floor to place a large tree on would be a soil surface in an air medium. The surface’s facing, the surface’s area, the medium’s area and the transition gap are configurable.

The surface can face 6 orthogonal directions: up, down, north, south, east and west. A surface’s facing direction is determined by its Facings. Furthermore, you can specify more than one facing and use the RequireAllFacings parameter. When a surface faces up, the medium is at the origin and the surface is below the origin. See the diagram below that shows a side-view of an up-facing surface.

The minimum area required by the surface and medium materials is determined by the SurfaceRadius and the MediumRadius values. A radius of `0.0` would result in an area of 1x1 block, while a radius of `2.0` would result in a disc of a 5 block diameter. The diagram below shows that relationship with a top-down view of an up-facing surface.

You can make the surface detection less strict by configuring a gap between the medium and the surface. That gap would allow some roughness between the medium and the surface. The diagrams below show how that works.

Parameters:

- Surface: Pattern slot. Validates every block of the surface.
- Medium: Pattern slot. Validates every block of the medium.
- SurfaceRadius: positive decimal number (>= `0.0`). Determines the size of the surface area.
- MediumRadius: positive decimal number (>= `0.0`). Determines the size of the medium area.
- SurfaceGap: integer (>= `0`).
- MediumGap: integer (>= `0`).
- Facings: list of string facing values. “N”, “S”, “E”, “W”, “U”, “D”.
- RequireAllFacings: boolean. If `true` then the Pattern will only validate if all Facings validate.

---

### [Cuboid](#cuboid)

Defines a cuboid region relative to the Pattern’s origin. The region is anchored by Min and Max inclusive points. This is similar to the shape of a creative Selection Tool. The SubPattern is tested against every position inside the cuboid. The Cuboid Pattern validates only if all the inner positions are validated by the SubPattern.

Below is a 2D representation of this 3D Cuboid Pattern.

Parameters:

- Min: 3D point. Inclusive minimum point of the cuboid relative to the Pattern’s origin.
- Max: 3D point. Inclusive maximum point of the cuboid relative to the Pattern’s origin.
- SubPattern: Pattern slot. The Cuboid uses this Pattern to validate the positions inside itself.

---

### [And](#and)

Logical operation that validates only if all the Patterns in its list also validate.

Parameters:

- Patterns: list of Pattern asset slots.

---

### [Or](#or)

Logical operation that validates if at least one of the Patterns in its list validates.

Parameters:

- Patterns: list of Pattern asset slots.

---

### [Not](#not)

Logical operation that validates only where the nested Pattern does not validate.

Parameters:

- Pattern: Pattern asset slot.

---

### [FieldFunction](#fieldfunction)

This Pattern validates only if the provided Density field at the position is within the specified delimiters. In combination with an And and Or pattern, this Pattern provides more control over the valid positions.

Important: because this Pattern queries a noise field, it can be expensive if placed high in the hierarchy. I recommend setting your Pattern structure such that this Pattern is the last to be tested. The diagram below shows examples of that.

In the green box, the FieldFunction Pattern runs only on positions with a floor. In the red box, the FieldFunction Pattern runs on every position. The difference between the two scenarios is significant.

Parameters:

- FieldFunction: slot for a noise function RootNode.
- Delimiters: list of noise value delimiters.


  - Min: decimal number. Lower inclusive bound of the delimiter.
  - Max: decimal number. Higher exclusive bound of the delimiter.

---

### [Imported](#imported)

Imports an exported Pattern.

Parameters:

- Name: string. The exported Pattern.
