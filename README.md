# ResearchCube

A Minecraft NeoForge mod (1.21.1) that adds a research-gated crafting system. Perform research at a Research Station to unlock special recipes that can be crafted using imprinted drives.

## Features

- **Tiered Research System** — 7 tiers from Unstable to Self-Aware, each requiring matching drives and cubes
- **Research Station** — Animated GeckoLib block where research is performed
- **Drive Crafting** — Special recipes unlocked through research, crafted at the Drive Crafting Table
- **Fluid System** — Four research fluids (Thinking, Pondering, Reasoning, Imagination) consumed during research
- **Team Sharing** — Players on the same scoreboard team share research progress
- **JEI Integration** — Full recipe support showing requirements and unlocking research
- **Datapack Support** — Fully customizable research trees and recipes via JSON

## Quick Start for Players

### Getting Started

1. **Craft a Research Station** — The central block for all research
2. **Obtain Drives and Cubes** — Each tier has matching drive and cube items
3. **Create Research Fluids** — Craft fluid buckets using the progression recipes:
   - Water Bucket + 2 Redstone → Thinking Fluid Bucket
   - Thinking Fluid Bucket + 2 Glowstone Dust → Pondering Fluid Bucket
   - Pondering Fluid Bucket + 2 Blaze Powder → Reasoning Fluid Bucket
   - Reasoning Fluid Bucket + Ender Pearl + Chorus Fruit → Imagination Fluid Bucket

### Using the Research Station

1. Place a **Drive** in the drive slot (left side, top)
2. Place a **Cube** in the cube slot (left side, bottom)
3. Fill the **fluid tank** by placing fluid buckets in the bucket slot
4. Add any required **item costs** in the cost slots
5. Select a research from the list and click **Start**
6. Wait for the progress bar to complete
7. The drive is now imprinted with a recipe!

### Using the Drive Crafting Table

1. Place your imprinted **Drive** anywhere in the 3×3 grid
2. Add the required **ingredients** in the remaining slots
3. Take the **result** from the output slot
4. The drive is NOT consumed — use it for multiple crafts!

### Key Items

| Item | Purpose |
|------|---------|
| Research Station | Perform research to unlock recipes |
| Drive Crafting Table | Craft recipes using imprinted drives |
| Drives (7 tiers) | Store recipe IDs from research |
| Cubes (6 tiers) | Required to perform research of that tier or lower |
| Research Book | Encyclopedia showing all research and completion status |
| Research Chip | Transfer completed research between players |
| Fluid Buckets | Fill the Research Station's tank |

## Quick Start for Pack Developers

### Adding Custom Research

Create a JSON file in `data/<your_pack>/research/`:

```json
{
  "name": "My Research",
  "description": "Description shown in tooltips",
  "category": "my_category",
  "tier": "BASIC",
  "duration": 1200,
  "item_costs": [
    { "item": "minecraft:iron_ingot", "count": 4 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:thinking_fluid",
    "amount": 1000
  },
  "recipe_pool": [
    "mypack:my_recipe"
  ]
}
```

### Adding Drive Crafting Recipes

Create a JSON file in `data/<your_pack>/recipe/`:

```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "mypack:my_recipe",
  "ingredients": [
    { "item": "minecraft:iron_ingot" }
  ],
  "result": {
    "id": "minecraft:iron_block",
    "count": 1
  }
}
```

### Full Documentation

See **[DATAPACK.md](DATAPACK.md)** for the complete datapack developer guide including:
- Full JSON schemas
- Prerequisites syntax (AND/OR trees)
- Weighted recipe pools
- Tier reference table
- Examples and troubleshooting

### Example Datapack

An example datapack with a 3-research tree is included in the `example_datapack/` folder.

## Configuration

Edit `config/researchcube-common.toml`:

```toml
# Research duration multiplier (1.0 = normal)
researchDurationMultiplier = 1.0

# Item cost multiplier (1.0 = normal)
researchCostMultiplier = 1.0

# Team sharing enabled
enableTeamSharing = true
```

## Building from Source

```bash
# Build the mod
./gradlew build

# Run the client
./gradlew runClient

# Run the server
./gradlew runServer
```

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- GeckoLib 4.7.x (bundled)
- JEI (optional, for recipe viewing)

## License

See [LICENSE.md](LICENSE.md)