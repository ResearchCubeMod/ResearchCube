# ResearchCube Datapack Guide

## Research Definitions

Research definitions are JSON files placed in `data/{namespace}/research/{name}.json`. The file path determines the research ID (e.g., `data/example/research/crystal_analysis.json` → `example:crystal_analysis`).

### JSON Schema

```json
{
  "name": "Display Name",
  "description": "Short description shown in tooltips.",
  "category": "grouping_category",
  "tier": "BASIC",
  "duration": 1200,
  "item_costs": [
    { "item": "minecraft:redstone", "count": 16 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:thinking_fluid",
    "amount": 1000
  },
  "idea_chip": {
    "id": "researchcube:metadata_irrecoverable",
    "components": {
      "minecraft:custom_name": "\"Idea: My Research\"",
      "minecraft:custom_data": { "my_chip_id": "my_research" }
    }
  },
  "prerequisites": {
    "type": "ALL",
    "values": ["namespace:other_research"]
  },
  "recipe_pool": [
    "namespace:recipe_id",
    { "id": "namespace:other_recipe", "weight": 3 }
  ]
}
```

### Fields

| Field | Required | Description |
|---|---|---|
| `tier` | Yes | Research tier: `UNSTABLE`, `BASIC`, `ADVANCED`, `PRECISE`, `FLAWLESS`, `SELF_AWARE` |
| `duration` | Yes | Duration in game ticks (20 ticks = 1 second) |
| `name` | No | Human-readable display name (defaults to file name) |
| `description` | No | Short description shown in tooltips |
| `category` | No | Grouping category for the research list UI |
| `item_costs` | No | Array of `{ "item": "<item_id>", "count": <n> }` |
| `fluid_cost` | No | `{ "fluid": "<fluid_id>", "amount": <mB> }` |
| `idea_chip` | No | ItemStack gating this research (see Idea Chips section) |
| `prerequisites` | No | Prerequisite structure (`ALL`, `ANY`, or single ID) |
| `recipe_pool` | No | Array of recipe IDs (string or `{ "id": ..., "weight": ... }`) |

### Research Tiers

| Tier | Required Fluid | Required Cube |
|---|---|---|
| UNSTABLE | Thinking Fluid | cube_unstable+ |
| BASIC | Thinking Fluid | cube_basic+ |
| ADVANCED | Pondering Fluid | cube_advanced+ |
| PRECISE | Reasoning Fluid | cube_precise+ |
| FLAWLESS | Reasoning Fluid | cube_flawless+ |
| SELF_AWARE | Imagination Fluid | cube_self_aware |

The drive must exactly match the research tier. The cube must be equal or higher tier.

---

## Idea Chips

Idea chips allow pack developers to gate specific research behind a custom item that
is placed in a dedicated slot in the Research Station. The chip is **consumed when
research begins** and is **NOT refunded on cancel**.

### How It Works

1. Add an `"idea_chip"` field to your research definition JSON.
2. The field uses standard `ItemStack` codec format (`"id"` + optional `"components"`).
3. When a player selects that research, the Research Station UI shows the idea chip
   slot with a red border and tooltip indicating what chip is required.
4. The player must place a matching item in the idea chip slot to enable the Start button.
5. On research start, the chip is consumed (stack shrinks by 1).
6. On cancel, item costs and fluid are refunded, but the idea chip is **not** refunded.

### Partial-Match Semantics

The idea chip uses **partial matching**: only the components explicitly declared in
your JSON are checked. Components present on the player's item but not declared in
the JSON are ignored.

This means you can declare just a `custom_name` to match by name, or just
`custom_data` to match by a stable tag, or both. Default item components (like
`max_stack_size`) do not need to be listed.

### Creating Chips for Players

Pack devs distribute idea chips to players via quests, loot tables, or give commands.

**Give command example:**
```
/give @p researchcube:metadata_irrecoverable[custom_name='"Idea: Forbidden Crystal"',custom_data={researchcube_chip_id:"forbidden_crystal"}]
```

**Item modifier (loot function):**
```json
{
  "function": "minecraft:set_components",
  "components": {
    "minecraft:custom_name": "\"Idea: Forbidden Crystal\"",
    "minecraft:custom_data": { "researchcube_chip_id": "forbidden_crystal" }
  }
}
```

### Recommended Practice

- Use a unique `minecraft:custom_data` tag as a **stable ID** for matching
  (e.g., `{ "researchcube_chip_id": "my_research" }`).
- Set a human-readable `minecraft:custom_name` for **player-facing display**
  (e.g., `"Idea: Advanced Alloys"`).
- Using both ensures the chip is identifiable by players and reliably matched by the system.

### Example Research Definition with Idea Chip

```json
{
  "name": "Forbidden Crystal Synthesis",
  "category": "materials",
  "tier": "ADVANCED",
  "duration": 4800,
  "item_costs": [
    { "item": "minecraft:amethyst_shard", "count": 16 },
    { "item": "minecraft:diamond", "count": 4 }
  ],
  "fluid_cost": {
    "fluid": "researchcube:pondering_fluid",
    "amount": 2000
  },
  "idea_chip": {
    "id": "researchcube:metadata_irrecoverable",
    "components": {
      "minecraft:custom_name": "\"Idea: Forbidden Crystal\"",
      "minecraft:custom_data": { "researchcube_chip_id": "forbidden_crystal" }
    }
  },
  "prerequisites": {
    "type": "ALL",
    "values": ["example:crystal_resonance"]
  },
  "recipe_pool": [
    "example:resonant_crystal_recipe"
  ]
}
```
