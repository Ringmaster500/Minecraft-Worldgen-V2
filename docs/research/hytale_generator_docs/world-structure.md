# World Structure

> Source: [https://docs.hytale.com/hytale-generator/world-structure/](https://docs.hytale.com/hytale-generator/world-structure/)

**Description:** Defines the contents of an entire world.

---

The World Structure asset defines how every aspect of a world is generated. Below are all entries for this asset.

### [DefaultBiome](#defaultbiome)

- Type: String
- Safe value: `"Basic"`

This Biome is used everywhere outside the delimiters.

### [Density](#density)

- Type: Density slot
- Safe value:




  ```
  "Type": "Constant",
  "Value": 1
  ```

Determines the Biome distribution in the world. The values are interpreted by the `Biomes` delimiter list.

### [Biomes](#biomes)

- Type: list of Biome delimiters
- Safe value: `[]`

Each delimiter assigns a Biome to places in the world where the Density is within its bounds.

### [DefaultTransitionDistance](#defaulttransitiondistance)

- Type: integer
- Safe value: `32`

The distance, in blocks, it takes for one Biome to transition into another. The terrain in that region is interpolated between the two Biomes. Larger distances impact performance.

### [MaxBiomeEdgeDistance](#maxbiomeedgedistance)

- Type: integer
- Safe value: `0`

The maximum distance from any Biome edge that world-gen tracks. This impacts Biomes that use the DistanceFromBiomeEdge Density node. Distances greater than this are read as `-1`.

### [SpawnPositions](#spawnpositions)

- Type: PositionProvider slot
- Safe value:




  ```
  "Type": "List",
  "Positions": [{"X": 0, "Y": 140, "Z": 0}]
  ```

The world's spawn positions.

### [Framework](#framework)

- Type: list of Framework modules
- Safe value: `[]`

The Framework helps define the world's high-level structure. It ensures consistency across multiple Biomes. For this to work, the Biomes in a World Structure must correctly interpret the Framework entries.

```
// Example of a DecimalConstants Framework entry.
"Framework": [
  {
    "Type": "DecimalConstants",
    "Entries": [
      {
        "Name": "Base",
        "Value": 100
      }
    ]
  }
]
```

Below are the types of Framework entries.

#### [Positions](#positions)

A list of named Positions fields for the world.

- Entries: list of entries


  - Name: string. The name to use when referencing this entry from inside the Biomes.
  - Positions: PositionProvider slot.

#### [DecimalConstants](#decimalconstants)

A list of named decimal constants.

- Entries: list of entries


  - Name: string. The constant's name.
  - Value: decimal number.
