# Copilot instructions for ResearchCube

## Big picture
- This is a NeoForge 1.21.1 mod (`Java 21`) with one core gameplay loop: players run research at a `Research Station`, then unlock gated `drive_crafting` recipes.
- Research definitions are **datapack-driven** from `data/*/research/*.json` and loaded on server reload via `ResearchManager`.
- The authoritative flow is server-side: UI click → packet → block entity validation → timed completion → recipe ID imprinted into drive NBT.
- The mod is fully implemented through Phase 4 (build system, items, blocks, research system, recipes, menu/screen UI, GeckoLib renderer). Build is verified `BUILD SUCCESSFUL`.

## Tech stack
- **NeoForge 21.1.219** for Minecraft 1.21.1
- **GeckoLib 4.7.1** — animated Research Station block entity (rotating brain, `animation.researchstation.idle`)
- **Java 21**, Gradle 8.10.2, `net.neoforged.moddev` plugin 2.0.42-beta
- **Parchment mappings** 2024.11.17 for readable parameter names
- `DataComponents.CUSTOM_DATA` / `NbtUtil` for item data (NeoForge 1.21.1 pattern — no raw `CompoundTag` on items)
- `MapCodec` + `StreamCodec` for recipe serialization (NeoForge 1.21.1 pattern — no `fromJson`/`toJson`)
- `SimpleJsonResourceReloadListener` for datapack research loading
- `IMenuTypeExtension.create(...)` with `FriendlyByteBuf` constructor for menu type registration

## Architecture and data flow

### Research loading path
1. `event/ModServerEvents` registers `ResearchManager` as a `AddReloadListenerEvent` listener.
2. `research/ResearchManager` (extends `SimpleJsonResourceReloadListener`) scans `data/{ns}/research/*.json` on every reload, parses them into `ResearchDefinition` objects, and populates `ResearchRegistry`.

### Runtime research path
1. Player right-clicks the Research Station block → `ResearchTableBlock.useWithoutItem` opens the menu via `ServerPlayer.openMenu`.
2. `client/screen/ResearchTableScreen` renders the research list, slots, and progress bar; player clicks Start.
3. Screen sends `network/StartResearchPacket` (client → server) containing `BlockPos` + research ID string.
4. `StartResearchPacket.handle` (server thread) calls `ResearchTableBlockEntity.tryStartResearch`, which validates tier rules, prerequisites, and item costs, then consumes costs and sets `activeResearchId`/`startTime`.
5. `ResearchTableBlockEntity.serverTick` checks elapsed ticks vs. `definition.getDuration()`. On completion, `completeResearch()` picks a random recipe from the pool and calls `NbtUtil.addRecipe()` to imprint the recipe ID onto the drive stack.
6. `ContainerData` slots in `ResearchTableMenu` sync `progress * 1000` and `isResearching` flag to the client each tick for the progress bar.

### Crafting gate path
- `recipe/DriveCraftingRecipe` (type `researchcube:drive_crafting`) checks the crafting grid for a `DriveItem` whose NBT contains the required `recipe_id`, then matches the remaining ingredients shapelessly. Drive is consumed (no remainder).

## Package map

| Package | Purpose |
|---|---|
| `com.researchcube` | `ResearchCubeMod` — entry point, `rl()` helper, DeferredRegister wiring |
| `block` | `ResearchTableBlock` (opens menu on right-click), `ResearchTableBlockEntity` (GeoBlockEntity, ticking, slot storage) |
| `item` | `DriveItem` (tiered, stores recipe IDs in CustomData, shows foil), `CubeItem` (tiered, validation only) |
| `menu` | `ResearchTableMenu` — 8 BE slots + player inventory, ContainerData progress sync |
| `client` | `ModClientEvents` — registers screen + GeckoLib renderer (Dist.CLIENT, MOD bus) |
| `client/screen` | `ResearchTableScreen` — scrollable research list, Start button, progress bar |
| `client/renderer` | `ResearchStationModel`, `ResearchStationRenderer` — GeckoLib geo/animation/texture wiring |
| `network` | `StartResearchPacket` (client→server), `ModNetworking` (PayloadRegistrar) |
| `recipe` | `DriveCraftingRecipe`, `DriveCraftingRecipeSerializer` |
| `research` | `ResearchDefinition`, `ResearchRegistry`, `ResearchManager`, `ResearchTier`, `ItemCost` |
| `research/prerequisite` | `Prerequisite` interface, `AndPrerequisite`, `OrPrerequisite`, `SinglePrerequisite`, `NonePrerequisite`, `PrerequisiteParser` |
| `registry` | `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModMenus`, `ModCreativeTabs`, `ModRecipeTypes`, `ModRecipeSerializers` |
| `util` | `NbtUtil` (CustomData read/write), `TierUtil` (canResearch validation) |
| `event` | `ModServerEvents` (AddReloadListenerEvent) |

## Critical workflows
- Build: `.\gradlew.bat build`
- Run client dev instance: `.\gradlew.bat runClient`
- Run dedicated server: `.\gradlew.bat runServer`
- Regenerate data outputs: `.\gradlew.bat runData` (writes into `src/generated/resources`, already included in main resources)
- No test suite is configured; validate changes with `build` + in-game `runClient` behavior.

## Project-specific conventions
- Keep all new content namespaced with `ResearchCubeMod.rl(...)` and mod id `researchcube`.
- Register new game objects using `DeferredRegister` in `registry/Mod*` classes, then ensure they are registered in `ResearchCubeMod` constructor.
- **Server authority**: never start/complete/cancel research from client code. Client screens send packets; server validates.
- Preserve slot semantics in `ResearchTableBlockEntity` / `ResearchTableMenu`:
  - slot 0 = drive, slot 1 = cube, slots 2–7 = item costs (`SLOT_DRIVE`, `SLOT_CUBE`, `COST_SLOT_START` constants).
- Enforce tier rules through `TierUtil.canResearch(cubeTier, driveTier, researchTier)` — cube tier ≥ research tier AND drive tier == research tier.
- Store custom item data through `DataComponents.CUSTOM_DATA` via `NbtUtil`; never write raw `CompoundTag` directly onto an `ItemStack`.
- For new recipes: add type + serializer in `ModRecipeTypes`/`ModRecipeSerializers`, register both in `ResearchCubeMod`, and provide JSON under `data/researchcube/recipe/`.
- Client-only registrations (screens, renderers) belong in `client/ModClientEvents` — annotated `@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)`.
- `@EventBusSubscriber` deprecation warnings for `Bus.MOD` are harmless in the current NeoForge version; leave as-is until the API stabilises.

## ResearchTier enum
Values in ordinal order (0–6): `IRRECOVERABLE`, `UNSTABLE`, `BASIC`, `ADVANCED`, `PRECISE`, `FLAWLESS`, `SELF_AWARE`.
- Drives map: irrecoverable→IRRECOVERABLE, unstable→UNSTABLE, reclaimed→BASIC, enhanced→ADVANCED, elaborate→PRECISE, cybernetic→FLAWLESS, self_aware→SELF_AWARE.
- Cubes exist for UNSTABLE through SELF_AWARE (no IRRECOVERABLE cube).

## JSON schemas

### Research definition (`data/{ns}/research/*.json`)
```json
{
  "tier": "BASIC",
  "duration": 1200,
  "prerequisites": "other_research_id",
  "item_costs": [{ "item": "minecraft:iron_ingot", "count": 4 }],
  "recipe_pool": ["researchcube:basic_circuit_recipe_1"]
}
```
- `prerequisites` may be: a string ID, `{"type":"AND","values":[...]}`, or `{"type":"OR","values":[...]}` (recursive).
- `item_costs` and `recipe_pool` are optional.

### Drive crafting recipe (`data/{ns}/recipe/*.json`)
```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "researchcube:basic_circuit_recipe_1",
  "ingredients": [{ "item": "minecraft:iron_ingot" }],
  "result": { "id": "minecraft:iron_block", "count": 1 }
}
```
- Drive containing the `recipe_id` must be present in the grid (it is consumed).
- Up to 8 additional ingredient slots (shapeless).

## GeckoLib assets
- Geo: `assets/researchcube/geo/research_station.geo.json` — identifier `geometry.unknown`, 128×128 UV, root bones: `ResearchStation → base, top, screen, Brain (→ center, b1–b8)`.
- Animation: `assets/researchcube/animations/research_station.animation.json` — animation `animation.researchstation.idle`, loops 24 s, Brain bone rotates 360°/360°/360° and bobs.
- Texture: `assets/researchcube/textures/research_station/research_station.png` — 128×128.
- Renderer uses `ResearchStationModel` + `ResearchStationRenderer`; block entity registers the idle controller unconditionally.

## High-value reference files
- `src/main/java/com/researchcube/ResearchCubeMod.java`
- `src/main/java/com/researchcube/block/ResearchTableBlockEntity.java`
- `src/main/java/com/researchcube/menu/ResearchTableMenu.java`
- `src/main/java/com/researchcube/client/screen/ResearchTableScreen.java`
- `src/main/java/com/researchcube/research/ResearchManager.java`
- `src/main/java/com/researchcube/network/StartResearchPacket.java`
- `src/main/java/com/researchcube/recipe/DriveCraftingRecipe.java`
- `src/main/resources/data/researchcube/research/advanced_processor.json`
- `src/main/resources/data/researchcube/recipe/processor_recipe_1.json`

## Project Management

### todo.lock File
- **Location**: `todo.lock` at the workspace root contains the project's task tracker with explicit instructions for AI agents.
- **AI Instructions** (lines 1–6): Read the `[AI INSTRUCTIONS]` block at the top—it specifies task priorities and restrictions.
- **Task Prefixes**:
  - `[MAJOR]` or `[DANGER]`: Requires explicit user approval before touching.
  - `[LOW PRIO]`: Can be deferred if necessary.
  - `[DONE]`: Completed; do not suggest or implement again.
- **AI Agent Convention**: When adding new tasks to `todo.lock`, prefix them with `[AI]` so they are distinguishable from user-created tasks.
- Always check task status before starting work — don't re-implement completed tasks or violate approval requirements.
