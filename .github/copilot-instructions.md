# Copilot instructions for ResearchCube

## Project at a glance
- NeoForge 1.21.1 mod using Java 21.
- Core loop: run research at Research Station, then unlock gated `drive_crafting` recipes.
- Research is datapack-driven from `data/*/research/*.json` and loaded on server reload.
- Server-authoritative flow: UI click -> packet -> server validation -> timed completion -> recipe ID written to drive NBT.
- Implementation status: complete and playable (build, UI, networking, fluids, compat integrations, guidebook, advancements).
- Key blocks: **Research Station** (research + unlock recipes), **Drive Crafting Table** (research-gated crafting), **Processing Station** (fluid + item processing).

## Stack and key APIs
- NeoForge `21.1.219`
- GeckoLib `4.7.1`
- Java `21`, Gradle `8.10.2`
- Parchment mappings `2024.11.17`
- Optional compat (`compileOnly`): JEI, EMI, Jade, Patchouli
- Item data: `DataComponents.CUSTOM_DATA` via `NbtUtil` (never raw item `CompoundTag`)
- Recipe codecs: `MapCodec` + `StreamCodec`
- Datapack loading: `SimpleJsonResourceReloadListener`
- Research persistence: `ResearchSavedData` keyed by `team:<teamname>` with UUID fallback
- Menu creation: `IMenuTypeExtension.create(...)` with `FriendlyByteBuf`
- Menu sync storage: `SimpleContainerData` must back `ContainerData`
- Research Station tank: `FluidTank` capacity `8000` mB

## GUI Architecture
- **ResearchTableScreen**: Main research station GUI with slot display, fluid tank, progress bar, and "View Research Tree" button.
  - View modes: `NORMAL` (see slots + controls) and `TREE` (research tree overlay).
  - Control buttons: Start/Cancel research, View Tree, Wipe Tank (when not researching).
- **ResearchTreeScreen**: Displays available research nodes in a tree structure.
  - Shows prerequisites, tier, costs, completion status.
  - Click to select research; "Start Research" button navigates back with selection.
  - Control buttons: Back to table, Start Research (when valid research selected).
- GUI packet flow: Client screen -> packet -> server handler -> block entity logic -> menu sync -> client screen update.

## Recipe Types
Three distinct recipe systems:
1. **`researchcube:drive_crafting`** — Drive Crafting Table recipes (research-locked)
   - Shaped: `pattern` (3-row string array) + `key` (char → item map) + `result`
   - Shapeless: `ingredients` (list) + `result`
   - **Always needs `recipe_id` field** matching `"researchcube:<filename>"`
   - Requires drive with matching `recipe_id` in NBT from research unlock
2. **`researchcube:processing`** — Processing Station recipes (freely available unless in research pool)
   - Fields: `inputs`, `fluid_inputs`, `outputs`, `fluid_output` (optional), `duration`
   - **NO `recipe_id` field** — ID comes from filename
3. **`minecraft:crafting_shaped` / `minecraft:crafting_shapeless`** — vanilla crafting (always available)

## Non-negotiable rules
- Keep all registrations namespaced with `ResearchCubeMod.rl(...)` and mod id `researchcube`.
- Use DeferredRegister in `registry/Mod*` classes and wire in `ResearchCubeMod`.
- Never perform research start/complete/cancel logic on the client.
- Never write raw `CompoundTag` directly on `ItemStack`; use `NbtUtil`.
- Drives are never consumed by drive crafting; `recipe_id` remains on drive.
- Always log silent research validation failures in `tryStartResearch()` with `[ResearchCube] WARN` and reason.
- For `ResearchTableMenu` sync, use writable `SimpleContainerData` storage; no-op `set()` breaks client state.
- `textures/block/baum.txt` is an inside joke. **Never delete or modify it.**

## Slot and data sync contracts
- Research Station slots:
  - `SLOT_DRIVE=0`
  - `SLOT_CUBE=1`
  - `COST_SLOT_START=2`
  - `SLOT_BUCKET_IN=8`
  - `SLOT_BUCKET_OUT=9`
  - `SLOT_IDEA_CHIP=10`
  - `TOTAL_SLOTS=11`
- Item costs are only slots `2..7` (iterate from `COST_SLOT_START` to `SLOT_BUCKET_IN` exclusive).
- Tank capacity is `TANK_CAPACITY=8000` mB.
- `ResearchTableMenu` data indexes:
  - `DATA_PROGRESS=0` (`0..1000`)
  - `DATA_IS_RESEARCHING=1` (`0/1`)
  - `DATA_FLUID_AMOUNT=2` (`0..8000`)
  - `DATA_FLUID_TYPE=3` (`0 empty, 1 thinking, 2 pondering, 3 reasoning, 4 imagination`)
- Menu buffer pattern: write extra fields in `openMenu(..., bufWriter)` and read in same order in menu buffer constructor.

## Research sharing scope
- Completed research is team-shared:
  - key format `team:<teamname>` when player has scoreboard team
  - UUID-string fallback when no team
- Use `ResearchSavedData.getResearchKey(ServerPlayer)` consistently.

## Runtime flow (authoritative)
1. `ResearchTableBlock.useWithoutItem` opens menu and writes `BlockPos` plus completed set for current research key.
2. `ResearchTableScreen` sends `StartResearchPacket` (client -> server).
3. `StartResearchPacket.handle` calls `ResearchTableBlockEntity.tryStartResearch` (tier/prereq/drive/cost/fluid checks).
4. On success, costs are consumed and snapshot for refund.
5. `serverTick` tracks progress; completion writes weighted recipe ID into drive and records completed research.
6. `CancelResearchPacket` triggers `cancelResearchWithRefund()`.

## Tier rules
- Enforce with `TierUtil.canResearch(cubeTier, driveTier, researchTier)`:
  - cube tier must be >= research tier
  - drive tier must be == research tier

## ResearchTier reference
Order: `IRRECOVERABLE`, `UNSTABLE`, `BASIC`, `ADVANCED`, `PRECISE`, `FLAWLESS`, `SELF_AWARE`

Capacity by tier:
- IRRECOVERABLE `0`
- UNSTABLE `2`
- BASIC `4`
- ADVANCED `8`
- PRECISE `12`
- FLAWLESS `16`
- SELF_AWARE `-1` (unlimited)

Tier colors (`getColor()`):
- IRRECOVERABLE `0x888888`
- UNSTABLE `0xFFFFFF`
- BASIC `0x55FF55`
- ADVANCED `0x5555FF`
- PRECISE `0xAA00AA`
- FLAWLESS `0xFFAA00`
- SELF_AWARE `0xFF5555`

## Data formats
### Research definition (`data/{ns}/research/*.json`)
```json
{
  "name": "Basic Circuit",
  "description": "Shown in tooltips/UI.",
  "category": "circuits",
  "tier": "BASIC",
  "duration": 1200,
  "prerequisites": "other_research_id",
  "item_costs": [{ "item": "minecraft:iron_ingot", "count": 4 }],
  "fluid_cost": { "fluid": "researchcube:thinking_fluid", "amount": 1000 },
  "recipe_pool": [
    "researchcube:basic_circuit_recipe_1",
    { "id": "researchcube:basic_circuit_recipe_2", "weight": 3 }
  ]
}
```
- Optional fields: `name`, `description`, `category`, `item_costs`, `fluid_cost`, `recipe_pool`.
- `prerequisites` supports string, recursive `AND`, recursive `OR`.
- `recipe_pool` supports string (weight 1) or object with `weight`.

### Drive crafting recipe (`data/{ns}/recipe/*.json`)
```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "researchcube:basic_circuit_recipe_1",
  "ingredients": [{ "item": "minecraft:iron_ingot" }],
  "result": { "id": "minecraft:iron_block", "count": 1 }
}
```
- **CRITICAL**: Drive crafting recipes MUST have `recipe_id` field matching `"researchcube:<filename>"`
- Drive with matching `recipe_id` in NBT is required and returned unchanged
- Supports shapeless (ingredient list) and shaped mode (`pattern` + `key`)
- Player must have completed research that includes this `recipe_id` in its `recipe_pool`

### Processing recipe (`data/{ns}/recipe/*.json`)
```json
{
  "type": "researchcube:processing",
  "inputs": [{ "item": "minecraft:iron_ingot", "count": 2 }],
  "fluid_inputs": [{ "fluid": "researchcube:thinking_fluid", "amount": 100 }],
  "outputs": [{ "item": "researchcube:research_chip", "count": 1 }],
  "duration": 200
}
```
- **NO `recipe_id` field** — recipe ID comes from filename only
- Most processing recipes are freely available (no research lock)
- Some may be gated via research `recipe_pool` if needed

## Research fluids
Registered fluids (source + flowing + bucket item, no placeable liquid block):
- `researchcube:thinking_fluid` (cyan) — for UNSTABLE/BASIC tier research
- `researchcube:pondering_fluid` (purple) — for ADVANCED tier research
- `researchcube:reasoning_fluid` (gold) — for PRECISE/FLAWLESS tier research
- `researchcube:imagination_fluid` (pink) — for SELF_AWARE tier research

Fluid behavior:
- Bucket class: `ResearchFluidBucketItem` (`stacksTo(1)`, remainder `Items.BUCKET`).
- Station fills from slot 8, outputs empty bucket to slot 9.
- `WipeTankPacket` drains the tank.
- Both Research Station and Processing Station expose `IFluidHandler` capability.

## Mod Items Reference
- **Drives**: `metadata_unstable`, `metadata_reclaimed`, `metadata_enhanced`, `metadata_elaborate`, `metadata_cybernetic`, `metadata_self_aware`
- **Cubes**: `cube_unstable`, `cube_basic`, `cube_advanced`, `cube_precise`, `cube_flawless`, `cube_self_aware`
- **Blocks**: `research_station_item`, `drive_crafting_table`, `processing_station`
- **Other**: `research_chip`, `research_book`, `*_fluid_bucket` (thinking/pondering/reasoning/imagination)

## Where to look first
### Core Logic
- `src/main/java/com/researchcube/ResearchCubeMod.java`
- `src/main/java/com/researchcube/block/ResearchTableBlockEntity.java`
- `src/main/java/com/researchcube/block/ResearchTableBlock.java`
- `src/main/java/com/researchcube/menu/ResearchTableMenu.java`
- `src/main/java/com/researchcube/research/ResearchManager.java`
- `src/main/java/com/researchcube/research/ResearchSavedData.java`

### GUI and Screens
- `src/main/java/com/researchcube/client/screen/ResearchTableScreen.java`
- `src/main/java/com/researchcube/client/screen/ResearchTreeScreen.java`

### Recipes
- `src/main/java/com/researchcube/recipe/DriveCraftingRecipe.java`
- `src/main/java/com/researchcube/recipe/ProcessingRecipe.java`

### Networking
- `src/main/java/com/researchcube/network/StartResearchPacket.java`
- `src/main/java/com/researchcube/network/CancelResearchPacket.java`
- `src/main/java/com/researchcube/network/WipeTankPacket.java`

### Registry and Fluids
- `src/main/java/com/researchcube/registry/ModFluids.java`
- `src/main/java/com/researchcube/registry/ModBlocks.java`
- `src/main/java/com/researchcube/registry/ModItems.java`

### Data Files
- Research definitions: `src/main/resources/data/researchcube/research/`
- Recipe files: `src/main/resources/data/researchcube/recipe/`

## Build and validation
- Build: `./gradlew.bat build`
- Client dev run: `./gradlew.bat runClient`
- Server dev run: `./gradlew.bat runServer`
- Data generation: `./gradlew.bat runData`
- No automated test suite: validate with build + in-game behavior.

## Commit message convention
Use Conventional Commits:
- format: `type(scope): short imperative summary`
- preferred types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `build`, `ci`, `style`, `perf`
- examples:
  - `fix(research): preserve selected research id during screen refresh`
  - `feat(processing): add fluid tank sync to station menu`
  - `docs(readme): clarify datapack research format`

## Task management rules (`todo.lock`)
- Read `[AI INSTRUCTIONS]` at the top before starting work.
- Do not touch tasks marked `[DONE]`.
- `[MAJOR]` and `[DANGER]` items require explicit user approval.
- `[LOW PRIO]` items can be deferred.
- If adding AI-created tasks, prefix with `[AI]`.

## Common Patterns
### Accessing Drive/Cube NBT
- Use `NbtUtil.getRecipeId(ItemStack)` to read drive recipe ID
- Use `NbtUtil.setRecipeId(ItemStack, ResourceLocation)` to write drive recipe ID
- Use `TierUtil.getCubeTier(ItemStack)` / `getDriveTier(ItemStack)` for tier checks

### Research Validation Flow
1. Check drive tier matches research tier exactly
2. Check cube tier >= research tier
3. Verify prerequisites are completed (check saved data)
4. Verify sufficient item costs in slots 2-7
5. Verify sufficient fluid in tank (matches research fluid type)
6. If any check fails, log warning and return false

### Menu Synchronization
- Always use `SimpleContainerData` with writable backing storage
- Update `containerData.set(index, value)` on server side
- Client reads via `containerData.get(index)` in screen
- Never use no-op storage that ignores `set()` calls

### Packet Handling
- Packets arrive on network thread; always use `source.enqueueWork(() -> {...})`
- Validate player permissions and world state before proceeding
- Return `InteractionResult.SUCCESS` only after successful operation
- Log failures for debugging

### Resource Location Handling
- Use `ResearchCubeMod.rl("path")` for mod namespace
- Recipe IDs must match research `recipe_pool` entries exactly
- Research IDs come from filename (e.g., `basic_circuit.json` → `researchcube:basic_circuit`)

## Debug Tips
- Research validation failures are logged as `[ResearchCube] WARN` with reason
- Check server logs for packet handling issues
- Use `/researchcube debug` commands to inspect saved data (if implemented)
- Tank sync issues: verify `DATA_FLUID_AMOUNT` and `DATA_FLUID_TYPE` are updated
- Drive not working: verify `recipe_id` exists in drive NBT and matches recipe file
