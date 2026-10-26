# Vector Provider

> Source: [https://docs.hytale.com/hytale-generator/vector-provider/](https://docs.hytale.com/hytale-generator/vector-provider/)

**Description:** Defines a 3D decimal vector {x, y, z}.

---

### [Constant](#constant)

Generates the provided vector.

Parameters:

- Vector: 3D decimal vector.

---

### [SetX](#setx)

Sets the value of the vector's X component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [SetY](#sety)

Sets the value of the vector's Y component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [SetZ](#setz)

Sets the value of the vector's Z component.

Parameters:

- Vector: VectorProvider slot.
- Value: Density slot.

---

### [Adder](#adder)

Adds all the input vectors.

This is component-wise addition. If the inputs are `v_1, v_2, ..., v_n`, the result is `sum of all v_i`, so each output component is the sum of the matching input components: `x = sum of all x_i`, `y = sum of all y_i`, `z = sum of all z_i`.

Parameters:

- Vectors: list of VectorProvider slots.

---

### [Subtracter](#subtracter)

Subtracts vector B from vector A.

This is component-wise subtraction. If `A = (a_x, a_y, a_z)` and `B = (b_x, b_y, b_z)`, the result is `A - B = (a_x - b_x, a_y - b_y, a_z - b_z)`, which you can read as the vector that points from **B** to **A**.

Parameters:

- Minuend: VectorProvider slot. Vector A.
- Subtrahend: VectorProvider slot. Vector B.

---

### [Random](#random)

Generates a unit vector in a random direction.

Parameters:

- Seed: String. Determines pseudo-random outcome.

---

### [Normalizer](#normalizer)

Sets the vector's length to the provided magnitude. The vector keeps its direction.

For any non-zero vector `v`, this computes `m * (v / |v|)`, where `|v| = sqrt(x^2 + y^2 + z^2)` and `m` is the target magnitude. In other words, it keeps the direction and rescales the length to exactly match the provided value.

Parameters:

- Vector: VectorProvider slot.
- Magnitude: Density slot. Determines the length of the vector.

---

### [ScalarMultiplier](#scalarmultiplier)

Multiplies the vector by a scalar.

If the input vector is `v = (x, y, z)` and the scalar is `k`, the result is `kv = (kx, ky, kz)`. This scales the length by `|k|`, and a negative scalar also flips the direction.

Parameters:

- Vector: VectorProvider slot.
- Scalar: Density slot.

---

### [Multiplier](#multiplier)

Multiplies all the provided vectors.

This is component-wise multiplication, not a dot or cross product. For vectors `A = (a_x, a_y, a_z)` and `B = (b_x, b_y, b_z)`, the result is `(a_x * b_x, a_y * b_y, a_z * b_z)`, and the same rule extends across any number of inputs.

Parameters:

- Vectors: list of VectorProvider slots. Vectors to multiply.

---

### [Cross](#cross)

Finds the cross product of the provided vectors: A cross B.

The cross product produces a vector perpendicular to both inputs. Its magnitude is `|A| * |B| * sin(theta)`, where `theta` is the angle between them, so it is largest for perpendicular vectors and zero for parallel ones.

Parameters:

- VectorA: VectorProvider slot.
- VectorB: VectorProvider slot.

---

### [PlaneProjector](#planeprojector)

Projects the vector onto a plane defined by the vectors PlaneA and PlaneB.

This keeps only the part of the vector that lies in the plane spanned by **PlaneA** and **PlaneB**. If `n` is the plane normal, usually from `n = (A cross B) / |A cross B|`, then the projection is `v - (v dot n) n`, which removes the component orthogonal to the plane.

Parameters:

- Vector: VectorProvider slot. The vector to project.
- PlaneA: VectorProvider slot. Defines the plane.
- PlaneB: VectorProvider slot. Defines the plane.

---

### [VectorProjector](#vectorprojector)

Projects the vector onto another vector.

This keeps only the component of **Source** that points along **Target**. For non-zero target vector `t` and source vector `s`, the projection is `proj_t(s) = ((s dot t) / (t dot t)) t`.

Parameters:

- Source: VectorProvider slot.
- Target: VectorProvider slot.

---

### [DensityGradient](#densitygradient)

Generates the gradient of the provided Density field. The resulting gradient vector shows which direction the Density field increases and how quickly it changes.

For more information about gradient vectors, see: https://en.wikipedia.org/wiki/Gradient

Parameters:

- SampleDistance: positive decimal value. How far apart the Density field value samples are taken. The optimal value for performance is 1.0. Greater values could be useful for smoothing out results for specific applications.
- Density: Density slot.

---

### [Cache](#cache)

Caches the input vector for each position.

Use this only if the downstream (child) VectorProvider is expensive and the same coordinate is queried more than once.

Parameters:

- Vector: VectorProvider slot. The output of this slot is cached.

---

### [Exported](#exported)

Allows exporting a VectorProvider as a single instance. Enabling `SingleInstance` on this node ensures all importers share the same logic.

By default, a completely different instance is created for every Imported node. When multiple Imported nodes import the same exported key, a new instance of that exported VectorProvider tree is created for each Imported node. `SingleInstance` ensures all importers share the same underlying instance of the node tree.

This node can be used to optimize caching when an exported VectorProvider is imported multiple times in the same context and contains caches. The caches would be shared between the different imported instances.

*Important: This is still an experimental feature and could cause unexpected behaviors if misused.*

Expected Inputs: `1`

Parameters:

- SingleInstance: boolean. Enable this to share the export across all Imported nodes that reference this key.
- VectorProvider: VectorProvider slot.

---

### [Imported](#imported)

Imports an exported VectorProvider.

Parameters:

- Name: string. The exported VectorProvider.
