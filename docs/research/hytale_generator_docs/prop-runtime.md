# Prop Runtime

> Source: [https://docs.hytale.com/hytale-generator/prop-runtime/](https://docs.hytale.com/hytale-generator/prop-runtime/)

**Description:** Generates a Prop Distribution and controls the order of the Props relative to each other.

---

Runtimes place Props in a given order and let them share context, which allows Props to place on, edit, or avoid each other. Each unique Runtime Index number adds another Prop Stage. A Stage's context size directly impacts the Output Size of its prior Stages (Stage 2's context size impacts the Output Sizes of Stages 0 and 1). A Prop Stage's context size is determined by the horizontal reach of its Props (Scanner, Pattern, and the horizontal size of the Prop's content).

# [Context Precision](#context-precision)

In the Runtime node's `ContextPrecision` drop-down menu, you can select the precision at which the context requirement is calculated for each Chunk. Each Context Precision option has benefits and drawbacks, which make it appropriate for some use cases and not for others. Below are the three Context Precision options and their appropriate use cases.

The overhead of each Context Precision option impacts the `Dynamic Bounds Initialization` performance that you find at the top of HytaleGenerator's performance report. Picking the right option is about finding the best compromise between the performance cost of a larger Context Size and the additional `Dynamic Bounds Initialization` overhead. The source of overhead is documented under each option below.

> [!NOTE]

> [!NOTE]

On this page, "Prop Distribution" means everything attached to the Runtime node's `PropDistribution` pin, taken as one unit. "Props" means every Prop that unit can place. A [Union](/hytale-generator/prop-distribution/#union) on that pin does not split its children up. The Runtime measures all of them together, so it optimizes for the whole group instead of each child. To let it optimize for one child alone, move that child to [its own Runtime node](#optimal-runtime-setup).

## [Option: Biome](#option-biome)

`Biome` is the safe default option to start with.

With `Biome` Context Precision, the Runtime's context size is the maximum for the entire Biome's span. This option doesn't check the Prop Distribution at all, which gives it the lowest overhead possible, but it doesn't optimize the context size at the sub-Biome level.

**Use Cases**

- Use `Biome` Context Precision for Props that don't read at all from the environment because those Props don't need any context.
- This option doesn't call Positions at all, so it could benefit Props with very expensive Positions, where the overhead of `Dynamic Bounds Initialization` exceeds the benefits of reducing the context size.

---

## [Option: Chunk](#option-chunk)

With `Chunk` Context Precision, the context size is determined individually for each Chunk, based on whether there are any Props distributed locally. The overhead comes from a single run of the Prop Distribution, which only asks whether the Chunk contains any Prop at all. One run is enough to answer that, so the overhead stays low.

Example of how this option helps reduce the Context Size: in a forested Biome, a Chunk located inside the forest has a larger context size than a Chunk located in a treeless prairie, because in the prairie this option wouldn't find a single tree in its Prop Distribution.

**Use Cases**

The `Chunk` Context Precision is appropriate for the following situations:

- The Prop Distribution has large multi-Chunk gaps without any Props.
- The Prop Distribution has areas where the Props are a Chunk or less apart.
- The Props must read from the world (that is, they have a Locator or Orienter Prop). If the Props don't read from the world, they should use `Biome` Context Precision.

Some example use cases: grass patches, typical forest trees, boulder clusters, pebbles, small to medium-sized decorations.

Beware that an extremely expensive Prop Distribution could have a significant impact on the `Dynamic Bounds Initialization` performance, in which case the `Biome` Context Precision would be more suitable.

---

## [Option: Prop](#option-prop)

With `Prop` Context Precision, the Runtime's context size extends by the exact size needed by the Props distributed locally in each Chunk. This option is the most precise of all three for reducing context size. On the flip side, `Prop` Context Precision has more overhead for densely distributed Props because it computes the Prop Distribution over the entire Chunk's bounds.

**Use Cases**

- Use `Prop` Context Precision for large Props that are more than a few Chunks apart. Large Props increase the context size a lot, and this option helps the most to reduce it.
- The Props must read from the world (that is, they have a Locator or Orienter Prop). If the Props don't read from the world, they should use `Biome` Context Precision.

If the Positions field is extremely expensive or dense (a Chunk or less apart), the overhead from sampling dense Positions and each Prop's bounds could be greater than the benefit from the additional reduction in Context Size. For dense Positions fields, `Chunk` Context Precision could be more suitable.

# [Optimal Runtime Setup](#optimal-runtime-setup)

You can optimize your Prop Runtimes by using separate Runtime nodes for each unique type of Prop Distribution. Separate nodes let you pick the optimal Context Precision for each Prop Distribution, and they let each Runtime optimize itself more efficiently for its Props.

In short, grouping different Prop Distributions under a single Runtime forces that Runtime to work in the least efficient way across all of them, which results in poorer overall performance. It is especially costly if the Props have different sizes, or if the Positions have different scales and properties.

> [!NOTE]

> [!NOTE]

To start optimizing the Context Precision options for your asset, set all the Runtimes to `Biome`. Then test each Runtime with a different value to see whether it improves or hurts performance.

> [!NOTE]

> [!NOTE]

You can use a different Context Precision for each Runtime node, even if the nodes share the same Runtime index.

## [Decision Helper](#decision-helper)

The decision tree below can help you build an intuition for the options and find the optimal Context Precision for your Prop Distribution. Answer each question and follow the arrows until you reach an option.
