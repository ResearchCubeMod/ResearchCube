# ResearchCube Datapack Guide

This guide is for **pack developers** who want to create custom research and recipes for ResearchCube.

## Table of Contents

1. [Overview](#overview)
2. [Research Definitions](#research-definitions)
3. [Drive Crafting Recipes](#drive-crafting-recipes)
4. [Prerequisites Syntax](#prerequisites-syntax)
5. [Weighted Recipe Pools](#weighted-recipe-pools)
6. [Tier Reference](#tier-reference)
7. [Configuration](#configuration)
8. [Examples](#examples)
9. [Troubleshooting](#troubleshooting)

---

## Overview

ResearchCube provides two datapack-driven systems:

1. **Research Definitions** — JSON files that define what research can be performed at a Research Station
2. **Drive Crafting Recipes** — Special crafting recipes that require a drive with a specific recipe ID imprinted

### File Locations

```
data/<namespace>/research/*.json       → Research definitions
data/<namespace>/recipe/*.json          → Drive crafting recipes (and vanilla recipes)
```

---

## Research Definitions

Research definitions are placed in `data/<namespace>/research/`.

### Full Schema

```json
{
  "name": "Display Name",
  "description": "Human-readable description shown in tooltips.",
  "category": "category_name",
  "tier": "BASIC",
  "duration": 1200,
  "prerequisites": "other_research_id",
  "item_costs": [
    { "item": "minecraft:iron_ingot", "count": 4 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:thinking_fluid",
    "amount": 1000
  },
  "recipe_pool": [
    "namespace:recipe_id_1",
    { "id": "namespace:recipe_id_2", "weight": 3 }
  ]
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | No | Display name. Falls back to file name if omitted. |
| `description` | string | No | Description shown in Research Book and tooltips. |
| `category` | string | No | Groups research in the UI (e.g., "circuits", "energy"). |
| `tier` | string | **Yes** | Research tier. See [Tier Reference](#tier-reference). |
| `duration` | integer | **Yes** | Time in ticks (20 ticks = 1 second). |
| `prerequisites` | varies | No | Research that must be completed first. See [Prerequisites](#prerequisites-syntax). |
| `item_costs` | array | No | Items consumed when starting research. |
| `fluid_cost` | object | No | Fluid consumed from the Research Station's tank. |
| `recipe_pool` | array | No | Recipe IDs to imprint on the drive when complete. |

### Item Costs

Each item cost is an object with:
- `item`: Item ID (e.g., `"minecraft:diamond"`)
- `count`: Number required (default: 1)

```json
"item_costs": [
  { "item": "minecraft:redstone", "count": 8 },
  { "item": "minecraft:gold_ingot", "count": 2 }
]
```

### Fluid Costs

Available fluids:
- `researchcube:thinking_fluid` — Basic tier
- `researchcube:pondering_fluid` — Advanced tier
- `researchcube:reasoning_fluid` — Precise/Flawless tier
- `researchcube:imagination_fluid` — Self-Aware tier

```json
"fluid_cost": {
  "fluid": "researchcube:pondering_fluid",
  "amount": 2000
}
```

---

## Drive Crafting Recipes

Drive crafting recipes allow crafting items using a drive that has been imprinted with a specific recipe ID from research.

### File Location

Place in `data/<namespace>/recipe/`.

### Shapeless Recipe

```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "namespace:recipe_identifier",
  "ingredients": [
    { "item": "minecraft:iron_ingot" },
    { "item": "minecraft:redstone" }
  ],
  "result": {
    "id": "minecraft:repeater",
    "count": 4
  }
}
```

### Shaped Recipe

```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "namespace:recipe_identifier",
  "pattern": [
    "RIR",
    " I ",
    "RIR"
  ],
  "key": {
    "R": { "item": "minecraft:redstone" },
    "I": { "item": "minecraft:iron_ingot" }
  },
  "result": {
    "id": "minecraft:piston",
    "count": 1
  }
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | **Yes** | Must be `"researchcube:drive_crafting"` |
| `recipe_id` | string | **Yes** | The ID that must be on the drive |
| `ingredients` | array | Shapeless | List of ingredients (shapeless mode) |
| `pattern` | array | Shaped | 3x3 pattern strings (shaped mode) |
| `key` | object | Shaped | Character-to-ingredient mapping |
| `result` | object | **Yes** | Output item with `id` and `count` |

**Note:** The drive itself must be placed in the crafting grid alongside ingredients. It is **not consumed** — the same drive can be used for multiple crafts.

---

## Prerequisites Syntax

Prerequisites can be specified in three forms:

### Single Research

```json
"prerequisites": "researchcube:basic_circuit"
```

### AND (All Required)

```json
"prerequisites": {
  "type": "AND",
  "values": [
    "researchcube:basic_circuit",
    "researchcube:energy_handling"
  ]
}
```

### OR (Any Required)

```json
"prerequisites": {
  "type": "OR",
  "values": [
    "researchcube:basic_circuit",
    "researchcube:material_synthesis"
  ]
}
```

### Nested Prerequisites

AND/OR can be nested recursively:

```json
"prerequisites": {
  "type": "AND",
  "values": [
    "researchcube:basic_circuit",
    {
      "type": "OR",
      "values": [
        "researchcube:energy_handling",
        "researchcube:material_synthesis"
      ]
    }
  ]
}
```

---

## Weighted Recipe Pools

When research completes, one recipe is randomly selected from the pool and imprinted on the drive.

### Uniform Random

All recipes have equal chance:

```json
"recipe_pool": [
  "namespace:recipe_a",
  "namespace:recipe_b",
  "namespace:recipe_c"
]
```

### Weighted Selection

Specify weights for different probabilities:

```json
"recipe_pool": [
  { "id": "namespace:common_recipe", "weight": 10 },
  { "id": "namespace:uncommon_recipe", "weight": 5 },
  { "id": "namespace:rare_recipe", "weight": 1 }
]
```

In this example:
- Common: 10/16 = 62.5% chance
- Uncommon: 5/16 = 31.25% chance
- Rare: 1/16 = 6.25% chance

You can mix plain strings (weight = 1) with weighted objects.

---

## Tier Reference

| Tier | Drive Item | Cube Item | Max Recipes | Typical Fluid |
|------|------------|-----------|-------------|---------------|
| IRRECOVERABLE | metadata_irrecoverable | — | 0 (non-functional) | — |
| UNSTABLE | metadata_unstable | cube_unstable | 2 | Thinking |
| BASIC | metadata_reclaimed | cube_basic | 4 | Thinking |
| ADVANCED | metadata_enhanced | cube_advanced | 8 | Pondering |
| PRECISE | metadata_elaborate | cube_precise | 12 | Reasoning |
| FLAWLESS | metadata_cybernetic | cube_flawless | 16 | Reasoning |
| SELF_AWARE | metadata_self_aware | cube_self_aware | ∞ | Imagination |

### Tier Rules

1. **Drive tier must EQUAL research tier** — You cannot use a higher-tier drive for lower-tier research
2. **Cube tier must be AT LEAST research tier** — Higher-tier cubes work for lower tiers
3. **Max Recipes** — Each drive tier has a recipe capacity limit

---

## Configuration

ResearchCube has a config file at `config/researchcube-common.toml`:

```toml
# Multiplier for research duration (1.0 = normal, 2.0 = double time)
researchDurationMultiplier = 1.0

# Multiplier for item cost amounts (1.0 = normal, 0.5 = half costs)
researchCostMultiplier = 1.0

# Whether team members share research progress
enableTeamSharing = true

# Multiplier for processing recipe duration
processingDurationMultiplier = 1.0
```

---

## Examples

### Example 1: Basic Research (No Prerequisites)

`data/mypack/research/copper_wiring.json`:
```json
{
  "name": "Copper Wiring",
  "description": "Learn to create efficient copper conductors.",
  "category": "electronics",
  "tier": "BASIC",
  "duration": 600,
  "item_costs": [
    { "item": "minecraft:copper_ingot", "count": 8 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:thinking_fluid",
    "amount": 500
  },
  "recipe_pool": [
    "mypack:copper_wire_recipe"
  ]
}
```

`data/mypack/recipe/copper_wire_recipe.json`:
```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "mypack:copper_wire_recipe",
  "ingredients": [
    { "item": "minecraft:copper_ingot" }
  ],
  "result": {
    "id": "minecraft:lightning_rod",
    "count": 2
  }
}
```

### Example 2: Research with Prerequisites

`data/mypack/research/advanced_wiring.json`:
```json
{
  "name": "Advanced Wiring",
  "description": "Complex wiring techniques for advanced machines.",
  "category": "electronics",
  "tier": "ADVANCED",
  "duration": 2400,
  "prerequisites": "mypack:copper_wiring",
  "item_costs": [
    { "item": "minecraft:copper_ingot", "count": 16 },
    { "item": "minecraft:gold_ingot", "count": 4 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:pondering_fluid",
    "amount": 1000
  },
  "recipe_pool": [
    { "id": "mypack:advanced_wire_a", "weight": 3 },
    { "id": "mypack:advanced_wire_b", "weight": 1 }
  ]
}
```

### Example 3: Research Tree with Multiple Branches

```
                    ┌── energy_core ──┐
copper_wiring ──────┤                 ├── fusion_reactor
                    └── shielding ────┘
```

`data/mypack/research/fusion_reactor.json`:
```json
{
  "name": "Fusion Reactor",
  "tier": "FLAWLESS",
  "duration": 12000,
  "prerequisites": {
    "type": "AND",
    "values": [
      "mypack:energy_core",
      "mypack:shielding"
    ]
  },
  "item_costs": [
    { "item": "minecraft:nether_star", "count": 1 },
    { "item": "minecraft:netherite_ingot", "count": 4 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:reasoning_fluid",
    "amount": 4000
  },
  "recipe_pool": [
    "mypack:fusion_reactor_recipe"
  ]
}
```

---

## Troubleshooting

### Research doesn't appear in the list

1. Check that the JSON is valid (no trailing commas, proper brackets)
2. Ensure the `tier` field is a valid tier name (case-sensitive: `BASIC`, not `basic`)
3. Check the game log for parsing errors
4. Verify you've reloaded datapacks (`/reload`)

### "Prerequisites not met" error

1. Verify prerequisite research IDs are correct and include namespace
2. Check that required research has actually been completed
3. For team play, ensure all players are on the same scoreboard team

### Drive doesn't work in crafting

1. Verify the recipe's `recipe_id` matches exactly what's on the drive
2. Check the drive tier matches the research tier that produced the recipe
3. Ensure the drive isn't full (has room for the recipe)

### Research starts but doesn't complete

1. Check that the drive remains in the Research Station
2. Ensure server hasn't restarted (research state is saved but definition must exist)
3. Verify the research definition still exists after datapack reload

### Fluid not being consumed

1. Verify the fluid ID is correct (e.g., `researchcube:thinking_fluid`)
2. Check the tank has enough fluid (shown in UI)
3. Ensure you're using the correct fluid tier for the research

---

## Best Practices

1. **Namespace your content** — Use your pack ID as namespace to avoid conflicts
2. **Test incrementally** — Add one research at a time and verify it works
3. **Balance duration and costs** — Consider the progression curve
4. **Use meaningful categories** — Groups help organize large research trees
5. **Document dependencies** — Keep a diagram of your research prerequisite tree
6. **Use weighted pools sparingly** — Too much RNG can frustrate players

---

## Quick Reference

### Research JSON Template

```json
{
  "name": "",
  "description": "",
  "category": "",
  "tier": "BASIC",
  "duration": 1200,
  "prerequisites": "",
  "item_costs": [],
  "fluid_cost": { "fluid": "", "amount": 0 },
  "recipe_pool": []
}
```

### Drive Crafting Recipe Template (Shapeless)

```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "",
  "ingredients": [],
  "result": { "id": "", "count": 1 }
}
```

### Drive Crafting Recipe Template (Shaped)

```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "",
  "pattern": ["   ", "   ", "   "],
  "key": {},
  "result": { "id": "", "count": 1 }
}
```
