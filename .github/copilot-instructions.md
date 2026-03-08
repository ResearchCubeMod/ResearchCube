# Copilot instructions for ResearchCube

## Big picture
- This is a NeoForge 1.21.1 mod (`Java 21`) with one core gameplay loop: players run research at a `Research Station`, then unlock gated `drive_crafting` recipes.
- Research definitions are **datapack-driven** from `data/*/research/*.json` and loaded on server reload via `ResearchManager`.
- The authoritative flow is server-side: UI click → packet → block entity validation → timed completion → recipe ID imprinted into drive NBT.
- The mod is fully implemented through Phase 6 (build system, items, blocks, research system, recipes, menu/screen UI, GeckoLib renderer, per-player tracking, cancel/refund, drive capacity, full UI polish). Build is verified `BUILD SUCCESSFUL`.

## Tech stack
- **NeoForge 21.1.219** for Minecraft 1.21.1
- **GeckoLib 4.7.1** — animated Research Station block entity (rotating brain, `animation.researchstation.idle`)
- **Java 21**, Gradle 8.10.2, `net.neoforged.moddev` plugin 2.0.42-beta
- **Parchment mappings** 2024.11.17 for readable parameter names
- `DataComponents.CUSTOM_DATA` / `NbtUtil` for item data (NeoForge 1.21.1 pattern — no raw `CompoundTag` on items)
- `MapCodec` + `StreamCodec` for recipe serialization (NeoForge 1.21.1 pattern — no `fromJson`/`toJson`)
- `SimpleJsonResourceReloadListener` for datapack research loading
- `SavedData` for per-player completed research persistence (`ResearchSavedData`)
- `IMenuTypeExtension.create(...)` with `FriendlyByteBuf` constructor for menu type registration
- `SimpleContainerData` wrapped in anonymous `ContainerData` for correct client-side sync (the `set()` method must store values — a pure read-only anonymous class breaks client sync)

## Architecture and data flow

### Research loading path
1. `event/ModServerEvents` registers `ResearchManager` as a `AddReloadListenerEvent` listener.
2. `research/ResearchManager` (extends `SimpleJsonResourceReloadListener`) scans `data/{ns}/research/*.json` on every reload, parses them into `ResearchDefinition` objects, and populates `ResearchRegistry`.

### Runtime research path
1. Player right-clicks the Research Station block → `ResearchTableBlock.useWithoutItem` opens the menu via `ServerPlayer.openMenu`, writing `BlockPos` + the player's completed research `Set<ResourceLocation>` into the `FriendlyByteBuf`.
2. `client/screen/ResearchTableScreen` renders the research list (tier-colored, locked with lock icon if prerequisites unmet), slots, active research name, and progress bar; player selects a row then clicks Start.
3. Screen sends `network/StartResearchPacket` (client → server) containing `BlockPos` + research ID string.
4. `StartResearchPacket.handle` (server thread) calls `ResearchTableBlockEntity.tryStartResearch`, which validates tier rules, prerequisites, drive capacity, and item costs — all failures log a `[ResearchCube]` WARN. On success, costs are consumed and a snapshot stored for refund.
5. `ResearchTableBlockEntity.serverTick` checks elapsed ticks vs. `definition.getDuration()`. On completion, `completeResearch()` picks a random recipe from the pool, calls `NbtUtil.addRecipe()` to imprint the recipe ID onto the drive stack, and records the research in `ResearchSavedData`.
6. `ContainerData` (backed by `SimpleContainerData` so `set()` actually stores client-side) syncs `progress * 1000` and `isResearching` flag each tick. The screen shows the running research name (tier-colored), a gradient progress bar, and activates the Stop button.
7. If cancelled via `CancelResearchPacket`, `cancelResearchWithRefund()` returns the consumed item costs into the cost slots.

### Crafting gate path
- `recipe/DriveCraftingRecipe` (type `researchcube:drive_crafting`) checks the crafting grid for a `DriveItem` whose NBT contains the required `recipe_id`, then matches the remaining ingredients shapelessly. Drive is consumed (no remainder).

## Package map

| Package | Purpose |
|---|---|
| `com.researchcube` | `ResearchCubeMod` — entry point, `rl()` helper, DeferredRegister wiring |
| `block` | `ResearchTableBlock` (opens menu on right-click, writes completed research into buf), `ResearchTableBlockEntity` (GeoBlockEntity, ticking, slot storage, `getUpdateTag`/`getUpdatePacket` for client sync) |
| `item` | `DriveItem` (tiered, stores recipe IDs in CustomData, `isFull()` capacity check, foil effect), `CubeItem` (tiered, validation only) |
| `menu` | `ResearchTableMenu` — 8 BE slots + player inventory, `SimpleContainerData`-backed ContainerData sync, `completedResearch` set from buf |
| `client` | `ModClientEvents` — registers screen + GeckoLib renderer (Dist.CLIENT, MOD bus) |
| `client/screen` | `ResearchTableScreen` — scrollable research list with tier colors + lock icons, prereq tooltip, active research name, gradient progress bar, Start/Stop buttons |
| `client/renderer` | `ResearchStationModel`, `ResearchStationRenderer` — GeckoLib geo/animation/texture wiring |
| `network` | `StartResearchPacket` (client→server), `CancelResearchPacket` (client→server), `ModNetworking` (PayloadRegistrar) |
| `recipe` | `DriveCraftingRecipe`, `DriveCraftingRecipeSerializer` |
| `research` | `ResearchDefinition` (id, tier, duration, prerequisites, itemCosts, recipePool, name, description), `ResearchRegistry`, `ResearchManager`, `ResearchTier` (with `maxRecipes`), `ItemCost`, `ResearchSavedData` |
| `research/prerequisite` | `Prerequisite` interface (with `describe()`), `AndPrerequisite`, `OrPrerequisite`, `SinglePrerequisite`, `NonePrerequisite`, `PrerequisiteParser` |
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
- **ContainerData sync**: always back `ContainerData` with a `SimpleContainerData` storage so `set()` actually stores client-received values. A pure read-only anonymous `ContainerData` (no-op `set()`) will silently break all client-side sync.
- **Menu buffer pattern**: when opening a menu with `ServerPlayer.openMenu(provider, bufWriter)`, write all extra data (e.g. completed research set) in the buf lambda; read it in the `FriendlyByteBuf` constructor of the menu in the same order.
- **Research failure logging**: all silent validation failures in `tryStartResearch()` must log a `[ResearchCube]` WARN with the specific reason so bugs are diagnosable without a debugger.
- **Research ID tracking in screen**: store `selectedId` (ResourceLocation) not just `selectedIndex` — the list is rebuilt every tick, so an index-only selection is immediately lost.

## ResearchTier enum
Values in ordinal order (0–6): `IRRECOVERABLE`, `UNSTABLE`, `BASIC`, `ADVANCED`, `PRECISE`, `FLAWLESS`, `SELF_AWARE`.
- Drives map: irrecoverable→IRRECOVERABLE, unstable→UNSTABLE, reclaimed→BASIC, enhanced→ADVANCED, elaborate→PRECISE, cybernetic→FLAWLESS, self_aware→SELF_AWARE.
- Cubes exist for UNSTABLE through SELF_AWARE (no IRRECOVERABLE cube).
- Each tier has a `maxRecipes` capacity: IRRECOVERABLE=0, UNSTABLE=2, BASIC=4, ADVANCED=8, PRECISE=12, FLAWLESS=16, SELF_AWARE=-1 (unlimited).
- `getColor()` returns an RGB int used for tier-colored text in the UI.
- `hasRecipeLimit()` returns false only for SELF_AWARE. `isFunctional()` returns false only for IRRECOVERABLE.

## JSON schemas

### Research definition (`data/{ns}/research/*.json`)
```json
{
  "name": "Basic Circuit",
  "description": "Short human-readable description shown in tooltip.",
  "tier": "BASIC",
  "duration": 1200,
  "prerequisites": "other_research_id",
  "item_costs": [{ "item": "minecraft:iron_ingot", "count": 4 }],
  "recipe_pool": ["researchcube:basic_circuit_recipe_1"]
}
```
- `name` and `description` are optional. `getDisplayName()` falls back to the ID path if `name` is absent.
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
- `src/main/java/com/researchcube/block/ResearchTableBlock.java`
- `src/main/java/com/researchcube/menu/ResearchTableMenu.java`
- `src/main/java/com/researchcube/client/screen/ResearchTableScreen.java`
- `src/main/java/com/researchcube/research/ResearchManager.java`
- `src/main/java/com/researchcube/research/ResearchSavedData.java`
- `src/main/java/com/researchcube/network/StartResearchPacket.java`
- `src/main/java/com/researchcube/network/CancelResearchPacket.java`
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
