# Graph Generator

> Source: [https://docs.hytale.com/hytale-generator/graph-generator/graph-generator/](https://docs.hytale.com/hytale-generator/graph-generator/graph-generator/)

**Description:** Generates a field of interconnected nodes that carry content such as Density, Prop Distribution, Positions and Material Provider.

---

The Graph Generator builds a procedural graph of interconnected nodes. Each node carries content, such as a [Density](/hytale-generator/density/) or a [Prop Distribution](/hytale-generator/prop-distribution/). With a graph you control where that content sits and how it connects.

The graph is built step by step by a list of [Graph Passes](/hytale-generator/graph-generator/graph-pass/). Each pass modifies the graph until it reaches its final form. For example:

1. The first pass places some empty nodes.
2. The second pass connects nodes that are close to each other.
3. The third pass puts content on the nodes.

You control what the graph looks like by editing the Graph Passes and defining their order.

Depending on your Graph Passes, the graph can extend to infinity or stay inside a given area. The Graph Generator splits the graph into a grid of cells. Once generated, each cell is cached to improve performance.

> Graph generation and content generation are two separate steps. Graph generation places the nodes, their connections and the content they carry. Content generation then builds the world content that players see.

## [Parameters](#parameters)

### [Passes](#passes)

- Type: list of [GraphPass](/hytale-generator/graph-generator/graph-pass/) slots

Lists the passes that build the graph, in the order they run. Each pass can read from and write to the graph. It sees the state of the graph as it was built by all the previous passes. The changes it makes to the graph are seen by the following pass in the list.

### [ExportName](#exportname)

- Type: string
- Safe value: `""`

Exports this Graph Generator.

### [ImportName](#importname)

- Type: string
- Safe value: `""`

Imports an exported Graph Generator.

### [CacheCellSize](#cachecellsize)

- Type: decimal Vector3d. Every component must be greater than `0`.
- Safe value: `{"X": 320, "Y": 320, "Z": 320}`

Sets the size of a single cell in the cache grid. There are pros and cons to having larger or smaller cells. The optimal cell size can be found by tweaking and benchmarking. A good general starting size for most applications is from `{320, 320, 320}` to `{700, 320, 700}`.

If the cell size is too small:

- Graph generation overhead is paid more often, which can lower average content generation performance.
- The cache covers a smaller total area, because CacheCapacity counts cells and not volume. Cells are then evicted and regenerated more often.

If the cell size is too large:

- Each cell takes longer to generate. Content generation waits whenever it queries an uncached cell, so its performance becomes uneven.
- Each cached cell costs more memory.
- More of the generated graph area is never explored by players.

### [CacheCapacity](#cachecapacity)

- Type: integer greater than or equal to `1`
- Safe value: `50`

Sets how many cells can stay in memory at once. This value doesn't affect the graph delivery performance, but it can affect the content generation performance.

If the capacity is too small, then the graph gets generated a lot more times than necessary and can dramatically slow down content generation performance.

If the capacity is too large, it can use up a lot more memory than necessary with minimal benefit to performance. Memory usage can be exacerbated by a very large CacheCellSize and/or a graph with a high node density.

A good starting value is `50`.

### [PrintStats](#printstats)

- Type: boolean
- Safe value: `false`

Prints the graph generation performance report to the logs.

### [StatsLabel](#statslabel)

- Type: string
- Safe value: `"UNNAMED"`

Identifies this Graph Generator in the performance report.

### [StatsPrintInterval](#statsprintinterval)

- Type: integer greater than or equal to `0`
- Safe value: `50`

Prints the performance report after this many cells are generated.

## [Performance](#performance)

The Graph Generator's performance has three parts. Together they make up the total cost of using a graph to place content.

> Set PrintStats to `true` to see your own numbers. The Graph Generator then writes a performance report to the logs. The examples below are extracts from that report.

### [1. Building the Graph](#1-building-the-graph)

The total time it takes to generate the graph is the sum of all its passes' execution times. The report gives the times for each pass:

```
Pass 3 :
    Average : 41083 ns / 0.041083 ms
    Max : 96741 ns / 0.096741 ms
    Min : 12309 ns / 0.012309 ms
```

- A pass's input bounds are the region of the graph it needs from its previous passes.
- A pass's output bounds are the region of the graph it is asked to generate.
- A pass's output bounds are directly affected by the input bounds of the following pass. For example, Pass 2 will have the same output bounds as Pass 3's input bounds.
- A pass's work bounds are the region it actually generates: its output bounds plus the context that it and every later pass need.

The features used inside each pass dictate what the pass's context requirements are, which determine how big the input bounds must be. Larger context requirements result in larger input bounds for that pass. See each feature's documentation for more info on how the feature affects the pass's context requirements.

Each pass's execution time scales with the volume of its work bounds. The report gives those bounds for each pass under `Context Bounds`:

```
Context Bounds:
    Total Cell Output Bounds (Blocks) : {x=100.0, y=100.0, z=100.0}
    Pass 1 :
        Increase : {x=120.0, y=120.0, z=120.0}
        Work : {x=380.0, y=380.0, z=380.0}
```

- Total Cell Output Bounds: the size of the cell the Graph Generator must produce. Read the values below relative to it.
- Increase: how much this pass adds to the work bounds, in blocks per axis. It adds to this pass and to every pass that runs before it.
- Work: the size of the region this pass generates, in blocks per axis. It holds the cell, the Increase of this pass, the Increase of every later pass, and the reach of the longest edge.

In the example the cell is 100 blocks per axis and Pass 1 generates 380 blocks per axis. That is 55 times the volume of the cell. The work grows on all three axes, so one large context requirement in a late pass can dominate the cost of every earlier pass.

### [2. Caching and Retrieving the Graph](#2-caching-and-retrieving-the-graph)

The graph is practically infinite, but it is generated in cells of limited size which get cached. An optimal cache configuration reduces the number of times each cell needs to be generated. Ideally each cell needs to generate only once, after which it is retrieved from the cache.

### [3. Generating the Content](#3-generating-the-content)

Finally, the content generation has its own performance cost on top of the graph generation. Several things affect the content generation performance:

- A larger Range on the [content that a node carries](/hytale-generator/graph-generator/graph-content/). Each sampled point then has to consider nodes that are further away.
- Greater number of graph nodes per unit of space (higher graph node density).
- The content itself. For example if the content uses expensive noise or expensive Props.

## [Performance caps](#performance-caps)

Several actions move or place nodes at a distance that a child provider computes while the graph generates. The Graph Generator must know the largest distance an action can reach before it runs. That distance sets how large an area every earlier pass has to generate. Actions of this kind therefore take a cap, such as MaxDistance, MaxOffsetExclusive or MaxProxyDistance.

Set a cap to the smallest value that is still above the largest value its provider can return. A cap that is too large makes every earlier pass generate more than it needs.
