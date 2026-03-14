# Copilot instructions for ResearchCube

## Big picture
- This is a NeoForge 1.21.1 mod (`Java 21`) with one core gameplay loop: players run research at a `Research Station`, then unlock gated `drive_crafting` recipes.
- Research definitions are **datapack-driven** from `data/*/research/*.json` and loaded on server reload via `ResearchManager`.
- The authoritative flow is server-side: UI click → packet → block entity validation → timed completion → recipe ID imprinted into drive NBT.
- The mod is fully implemented (build system, items, blocks, research system, recipes, menu/screen UI, GeckoLib renderer, per-player tracking, cancel/refund, drive capacity, fluid costs, fluid tank, bucket slot handling, research book, Drive Crafting Table, JEI/EMI integration, ambient sound, HUD overlay, Jade integration, Patchouli guidebook, Processing Station, Research Tree screen, Drive Inspector screen, advancements/criterion triggers). Build is verified `BUILD SUCCESSFUL`.

## Tech stack
- **NeoForge 21.1.219** for Minecraft 1.21.1
- **GeckoLib 4.7.1** — animated Research Station block entity (rotating brain, `animation.researchstation.idle`)
- **Java 21**, Gradle 8.10.2, `net.neoforged.moddev` plugin 2.0.42-beta
- **Parchment mappings** 2024.11.17 for readable parameter names
- **JEI 19.21.0.247** (`mezz.jei`) — `compileOnly` dependency; recipe categories for drive-crafting and processing recipes. Only loaded when JEI is present at runtime.
- **EMI 1.1.18+1.21.1** (`dev.emi`) — `compileOnly` dependency; full parity with JEI plugin. Only loaded when EMI is present.
- **Jade 15.x** (`maven.modrinth:jade`) — `compileOnly`; block overlay for Research Station and Processing Station.
- **Patchouli 1.21.x** — `compileOnly`; guidebook registered at `assets/researchcube/patchouli_books/guide/`.
- `DataComponents.CUSTOM_DATA` / `NbtUtil` for item data (NeoForge 1.21.1 pattern — no raw `CompoundTag` on items)
- `MapCodec` + `StreamCodec` for recipe serialization (NeoForge 1.21.1 pattern — no `fromJson`/`toJson`)
- `SimpleJsonResourceReloadListener` for datapack research loading
- `SavedData` for per-player completed research persistence (`ResearchSavedData`)
- `IMenuTypeExtension.create(...)` with `FriendlyByteBuf` constructor for menu type registration
- `SimpleContainerData` wrapped in anonymous `ContainerData` for correct client-side sync (the `set()` method must store values — a pure read-only anonymous class breaks client sync)
- `FluidTank` (NeoForge) for the Research Station's internal fluid storage (capacity 8000 mB = 8 buckets)
- `IFluidHandler` capability exposed by both the Research Station and Processing Station block entities via `RegisterCapabilitiesEvent`

## Architecture and data flow

### Research loading path
1. `event/ModServerEvents` registers `ResearchManager` as a `AddReloadListenerEvent` listener.
2. `research/ResearchManager` (extends `SimpleJsonResourceReloadListener`) scans `data/{ns}/research/*.json` on every reload, parses them into `ResearchDefinition` objects, and populates `ResearchRegistry`.

### Runtime research path
1. Player right-clicks the Research Station block → `ResearchTableBlock.useWithoutItem` opens the menu via `ServerPlayer.openMenu`, writing `BlockPos` + the player's completed research `Set<ResourceLocation>` into the `FriendlyByteBuf`.
2. `client/screen/ResearchTableScreen` renders the research list (tier-colored, locked with lock icon if prerequisites unmet), slots, active research name, and progress bar; player selects a row then clicks Start.
3. Screen sends `network/StartResearchPacket` (client → server) containing `BlockPos` + research ID string.
4. `StartResearchPacket.handle` (server thread) calls `ResearchTableBlockEntity.tryStartResearch`, which validates tier rules, prerequisites, drive capacity, item costs, and fluid costs — all failures log a `[ResearchCube]` WARN. On success, items are consumed and the fluid is drained from the tank; both are snapshotted for refund.
5. `ResearchTableBlockEntity.serverTick` checks elapsed ticks vs. `definition.getDuration()`. On completion, `completeResearch()` picks a weighted-random recipe from the pool via `WeightedRecipe`, calls `NbtUtil.addRecipe()` to imprint the recipe ID onto the drive stack, and records the research in `ResearchSavedData`. The `CompleteResearchTrigger` criterion is also fired here.
6. `ContainerData` (backed by `SimpleContainerData`) syncs 4 values each tick: `DATA_PROGRESS` (0–1000), `DATA_IS_RESEARCHING` (0/1), `DATA_FLUID_AMOUNT` (0–8000 mB), `DATA_FLUID_TYPE` (0=empty, 1=thinking, 2=pondering, 3=reasoning, 4=imagination). The screen shows the running research name (tier-colored), a gradient progress bar, a fluid gauge, and activates the Stop button.
7. If cancelled via `CancelResearchPacket`, `cancelResearchWithRefund()` returns the consumed item costs into the cost slots.

### Crafting gate path
- `recipe/DriveCraftingRecipe` (type `researchcube:drive_crafting`) checks the crafting grid for a `DriveItem` whose NBT contains the required `recipe_id`, then matches the remaining ingredients shapelessly or shaped. **The drive is returned intact via `getRemainingItems` — the recipe_id is kept so the same recipe can be crafted repeatedly.** Only the other ingredients are consumed.

## Package map

| Package | Purpose |
|---|---|
| `com.researchcube` | `ResearchCubeMod` — entry point, `rl()` helper, DeferredRegister wiring, `IFluidHandler` capability registration |
| `block` | `ResearchTableBlock` (opens menu on right-click), `ResearchTableBlockEntity` (GeoBlockEntity, ticking, 10-slot inventory + FluidTank, `getUpdateTag`/`getUpdatePacket` for client sync), `DriveCraftingTableBlock`, `DriveCraftingTableBlockEntity`, `ProcessingStationBlock`, `ProcessingStationBlockEntity` |
| `item` | `DriveItem` (tiered, stores recipe IDs in CustomData, `isFull()` capacity check, foil effect, opens `DriveInspectorScreen` on right-click), `CubeItem` (tiered, validation only), `ResearchBookItem` (opens research book screen via packet), `ResearchChipItem`, `ResearchFluidBucketItem` (custom bucket for research fluids) |
| `menu` | `ResearchTableMenu` — 10 BE slots + player inventory, 4-value `SimpleContainerData`, `completedResearch` set from buf; `DriveCraftingTableMenu` — drive crafting container; `ProcessingStationMenu` — processing station container |
| `client` | `ModClientEvents` — registers screens + GeckoLib renderer + sound (Dist.CLIENT, MOD bus); `ClientSoundHandler` — starts/stops `ResearchStationSoundInstance`; `ClientResearchData` — client-side cache of completed research for JEI/EMI integration; `ResearchHudOverlay` — on-screen HUD showing active research progress |
| `client/screen` | `ResearchTableScreen` — scrollable research list with tier colors + lock icons, prereq tooltip, fluid gauge, gradient progress bar, Start/Stop buttons; `DriveCraftingTableScreen`; `ProcessingStationScreen`; `ResearchBookScreen` — read-only research encyclopedia; `ResearchTreeScreen` — tree visualization; `DriveInspectorScreen` — shows recipes stored on a drive; `ScreenRenderHelper` — shared rendering utilities |
| `client/renderer` | `ResearchStationModel`, `ResearchStationRenderer` — GeckoLib geo/animation/texture wiring |
| `client/sound` | `ResearchStationSoundInstance` — looping ambient sound while research is active |
| `compat/jei` | `ResearchCubeJEIPlugin` (`@JeiPlugin`), `DriveCraftingCategory`, `ProcessingCategory` — JEI recipe categories |
| `compat/emi` | `ResearchCubeEMIPlugin`, `EmiDriveCraftingRecipe`, `EmiProcessingRecipe` — full EMI parity |
| `compat/jade` | `ResearchCubeJadePlugin`, `ResearchStationProvider`, `ProcessingStationProvider` — Jade block overlays |
| `network` | `StartResearchPacket`, `CancelResearchPacket`, `WipeTankPacket`, `OpenResearchBookPacket`, `StartProcessingPacket` (all client→server); `SyncResearchProgressPacket` (server→client); `ModNetworking` (PayloadRegistrar) |
| `recipe` | `DriveCraftingRecipe`, `DriveCraftingRecipeSerializer`, `ProcessingRecipe`, `ProcessingRecipeSerializer`, `ProcessingFluidStack` |
| `research` | `ResearchDefinition` (id, tier, duration, prerequisites, itemCosts, fluidCost, recipePool, name, description, category), `ResearchRegistry`, `ResearchManager`, `ResearchTier` (with `maxRecipes` and `getColor()`), `ItemCost`, `FluidCost`, `WeightedRecipe`, `ResearchSavedData` |
| `research/prerequisite` | `Prerequisite` interface (with `describe()`), `AndPrerequisite`, `OrPrerequisite`, `SinglePrerequisite`, `NonePrerequisite`, `PrerequisiteParser` |
| `research/criterion` | `CompleteResearchTrigger` — advancement criterion fired on research completion |
| `registry` | `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModMenus`, `ModCreativeTabs`, `ModRecipeTypes`, `ModRecipeSerializers`, `ModFluids`, `ModConfig`, `ModCriterionTriggers` |
| `util` | `NbtUtil` (CustomData read/write), `TierUtil` (canResearch validation), `RecipeOutputResolver` |
| `event` | `ModServerEvents` (AddReloadListenerEvent) |

## Critical workflows
- Build: `.\gradlew.bat build`
- Run client dev instance: `.\gradlew.bat runClient`
- Run dedicated server: `.\gradlew.bat runServer`
- Regenerate data outputs: `.\gradlew.bat runData` (writes into `src/generated/resources`, already included in main resources)
- No test suite is configured; validate changes with `build` + in-game `runClient` behavior.

## Commit message convention
- Use Conventional Commits for any suggested commit message: `type(scope): short imperative summary`.
- Keep the summary concise, lowercase, and focused on the user-visible change or technical root cause.
- Prefer these types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `build`, `ci`, `style`, `perf`.
- Use scope when it adds clarity, usually a package, system, or feature area such as `research`, `ui`, `network`, `recipe`, or `data`.
- Good examples:
  - `fix(research): preserve selected research id during screen refresh`
  - `feat(processing): add fluid tank sync to station menu`
  - `docs(readme): clarify datapack research format`

## Project-specific conventions
- Keep all new content namespaced with `ResearchCubeMod.rl(...)` and mod id `researchcube`.
- Register new game objects using `DeferredRegister` in `registry/Mod*` classes, then ensure they are registered in `ResearchCubeMod` constructor.
- **Server authority**: never start/complete/cancel research from client code. Client screens send packets; server validates.
- Preserve slot semantics in `ResearchTableBlockEntity` / `ResearchTableMenu`:
  - slot 0 = drive, slot 1 = cube, slots 2–7 = item costs, slot 8 = bucket_in, slot 9 = bucket_out (`SLOT_DRIVE=0`, `SLOT_CUBE=1`, `COST_SLOT_START=2`, `SLOT_BUCKET_IN=8`, `SLOT_BUCKET_OUT=9`, `TOTAL_SLOTS=10`).
  - When iterating cost slots, always loop `COST_SLOT_START` to `SLOT_BUCKET_IN` (exclusive), i.e. slots 2–7 only. Never include bucket slots in item-cost validation or consumption.
  - The block entity also holds a `FluidTank` (capacity `TANK_CAPACITY = 8000` mB). Fluid cost is validated and drained separately from item costs.
- Enforce tier rules through `TierUtil.canResearch(cubeTier, driveTier, researchTier)` — cube tier ≥ research tier AND drive tier == research tier.
- Store custom item data through `DataComponents.CUSTOM_DATA` via `NbtUtil`; never write raw `CompoundTag` directly onto an `ItemStack`.
- For new recipes: add type + serializer in `ModRecipeTypes`/`ModRecipeSerializers`, register both in `ResearchCubeMod`, and provide JSON under `data/researchcube/recipe/`.
- Client-only registrations (screens, renderers) belong in `client/ModClientEvents` — annotated `@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)`.
- `@EventBusSubscriber` deprecation warnings for `Bus.MOD` are harmless in the current NeoForge version; leave as-is until the API stabilises.
- **ContainerData sync**: always back `ContainerData` with a `SimpleContainerData` storage so `set()` actually stores client-received values. A pure read-only anonymous `ContainerData` (no-op `set()`) will silently break all client-side sync. `ResearchTableMenu` exposes 4 data slots: `DATA_PROGRESS=0`, `DATA_IS_RESEARCHING=1`, `DATA_FLUID_AMOUNT=2`, `DATA_FLUID_TYPE=3`.
- **Menu buffer pattern**: when opening a menu with `ServerPlayer.openMenu(provider, bufWriter)`, write all extra data (e.g. completed research set) in the buf lambda; read it in the `FriendlyByteBuf` constructor of the menu in the same order.
- **Research failure logging**: all silent validation failures in `tryStartResearch()` must log a `[ResearchCube]` WARN with the specific reason so bugs are diagnosable without a debugger.
- **Research ID tracking in screen**: store `selectedId` (ResourceLocation) not just `selectedIndex` — the list is rebuilt every tick, so an index-only selection is immediately lost.
- **Drive behavior in crafting**: drives are **never consumed**. `DriveCraftingRecipe.getRemainingItems()` returns the drive stack unchanged so the stored recipe IDs persist and the same recipe can be crafted repeatedly.

## ResearchTier enum
Values in ordinal order (0–6): `IRRECOVERABLE`, `UNSTABLE`, `BASIC`, `ADVANCED`, `PRECISE`, `FLAWLESS`, `SELF_AWARE`.
- Drives map: irrecoverable→IRRECOVERABLE, unstable→UNSTABLE, reclaimed→BASIC, enhanced→ADVANCED, elaborate→PRECISE, cybernetic→FLAWLESS, self_aware→SELF_AWARE.
- Cubes exist for UNSTABLE through SELF_AWARE (no IRRECOVERABLE cube).
- Each tier has a `maxRecipes` capacity: IRRECOVERABLE=0, UNSTABLE=2, BASIC=4, ADVANCED=8, PRECISE=12, FLAWLESS=16, SELF_AWARE=-1 (unlimited).
- `getColor()` returns an RGB int used for tier-colored text in the UI:
  - IRRECOVERABLE = `0x888888` (gray)
  - UNSTABLE = `0xFFFFFF` (white)
  - BASIC = `0x55FF55` (green)
  - ADVANCED = `0x5555FF` (blue)
  - PRECISE = `0xFFAA00` (gold)
  - FLAWLESS = `0xAA00AA` (purple)
  - SELF_AWARE = `0xFF5555` (red)
- `hasRecipeLimit()` returns false only for SELF_AWARE. `isFunctional()` returns false only for IRRECOVERABLE.

## JSON schemas

### Research definition (`data/{ns}/research/*.json`)
```json
{
  "name": "Basic Circuit",
  "description": "Short human-readable description shown in tooltip.",
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
- `name`, `description`, `category`, `item_costs`, `fluid_cost`, and `recipe_pool` are all optional.
- `getDisplayName()` falls back to the ID path if `name` is absent.
- `prerequisites` may be: a string ID, `{"type":"AND","values":[...]}`, or `{"type":"OR","values":[...]}` (recursive).
- `recipe_pool` entries may be plain strings (weight defaults to 1) or objects `{"id": "...", "weight": N}`. Selection is weighted-random via `WeightedRecipe`.
- `fluid_cost` specifies the fluid (`researchcube:thinking_fluid`, `pondering_fluid`, `reasoning_fluid`, or `imagination_fluid`) and amount in mB that must be in the Research Station's tank before research can start. The fluid is drained on research start and refunded on cancel.

### Drive crafting recipe (`data/{ns}/recipe/*.json`)
```json
{
  "type": "researchcube:drive_crafting",
  "recipe_id": "researchcube:basic_circuit_recipe_1",
  "ingredients": [{ "item": "minecraft:iron_ingot" }],
  "result": { "id": "minecraft:iron_block", "count": 1 }
}
```
- Drive containing the `recipe_id` must be present in the grid; it is **returned intact after crafting**.
- Up to 8 additional ingredient slots (shapeless).
- For shaped recipes add `"pattern"` and `"key"` fields (standard NeoForge shaped format); drive slot position is identified by its key character.

## Research fluids

Four custom fluids are registered in `ModFluids`, each with a source + flowing variant and a bucket item in `ModItems`. **No liquid block** is registered — these fluids cannot be placed in the world.

| Fluid ID | Bucket item | Color | Typical tier usage |
|---|---|---|---|
| `researchcube:thinking_fluid` | `thinking_fluid_bucket` | Cyan (`#55CCFF`) | UNSTABLE / BASIC |
| `researchcube:pondering_fluid` | `pondering_fluid_bucket` | Purple (`#AA55FF`) | ADVANCED |
| `researchcube:reasoning_fluid` | `reasoning_fluid_bucket` | Gold (`#FFAA00`) | PRECISE / FLAWLESS |
| `researchcube:imagination_fluid` | `imagination_fluid_bucket` | Pink (`#FF5599`) | SELF_AWARE |

- Fluids have a `FluidType` with custom colour registered via `ModFluids`.
- Bucket items use `ResearchFluidBucketItem` (not vanilla `BucketItem`), `stacksTo(1)`, `craftRemainder = Items.BUCKET`.
- The Research Station holds up to `TANK_CAPACITY = 8000` mB in a single `FluidTank`. Place a filled bucket in slot 8 (`SLOT_BUCKET_IN`) to fill the tank; the empty bucket lands in slot 9 (`SLOT_BUCKET_OUT`). Send `WipeTankPacket` to drain the tank.
- `DATA_FLUID_TYPE` encodes the current fluid as an integer (0=empty, 1=thinking, 2=pondering, 3=reasoning, 4=imagination) for `ContainerData` sync.
- Both the Research Station and the Processing Station expose `IFluidHandler` capabilities registered in `ResearchCubeMod.registerCapabilities`. The Processing Station exposes a combined handler for all its tanks via `getCombinedFluidHandler()`.

## GeckoLib assets
- Geo: `assets/researchcube/geo/research_station.geo.json` — identifier `geometry.unknown`, 128×128 UV, root bones: `ResearchStation → base, top, screen, Brain (→ center, b1–b8)`.
- Animation: `assets/researchcube/animations/research_station.animation.json` — animation `animation.researchstation.idle`, loops 24 s, Brain bone rotates 360°/360°/360° and bobs.
- Texture: `assets/researchcube/textures/research_station/research_station.png` — 128×128.
- Renderer uses `ResearchStationModel` + `ResearchStationRenderer`; block entity registers the idle controller unconditionally.

## High-value reference files
- `src/main/java/com/researchcube/ResearchCubeMod.java`
- `src/main/java/com/researchcube/block/ResearchTableBlockEntity.java`
- `src/main/java/com/researchcube/block/ResearchTableBlock.java`
- `src/main/java/com/researchcube/block/DriveCraftingTableBlockEntity.java`
- `src/main/java/com/researchcube/block/ProcessingStationBlockEntity.java`
- `src/main/java/com/researchcube/menu/ResearchTableMenu.java`
- `src/main/java/com/researchcube/menu/DriveCraftingTableMenu.java`
- `src/main/java/com/researchcube/menu/ProcessingStationMenu.java`
- `src/main/java/com/researchcube/client/screen/ResearchTableScreen.java`
- `src/main/java/com/researchcube/client/screen/DriveCraftingTableScreen.java`
- `src/main/java/com/researchcube/client/screen/ProcessingStationScreen.java`
- `src/main/java/com/researchcube/client/screen/ResearchBookScreen.java`
- `src/main/java/com/researchcube/client/screen/DriveInspectorScreen.java`
- `src/main/java/com/researchcube/client/screen/ResearchTreeScreen.java`
- `src/main/java/com/researchcube/client/ResearchHudOverlay.java`
- `src/main/java/com/researchcube/research/ResearchManager.java`
- `src/main/java/com/researchcube/research/ResearchSavedData.java`
- `src/main/java/com/researchcube/research/WeightedRecipe.java`
- `src/main/java/com/researchcube/research/FluidCost.java`
- `src/main/java/com/researchcube/research/criterion/CompleteResearchTrigger.java`
- `src/main/java/com/researchcube/network/StartResearchPacket.java`
- `src/main/java/com/researchcube/network/CancelResearchPacket.java`
- `src/main/java/com/researchcube/network/WipeTankPacket.java`
- `src/main/java/com/researchcube/network/SyncResearchProgressPacket.java`
- `src/main/java/com/researchcube/recipe/DriveCraftingRecipe.java`
- `src/main/java/com/researchcube/recipe/ProcessingRecipe.java`
- `src/main/java/com/researchcube/registry/ModFluids.java`
- `src/main/java/com/researchcube/registry/ModConfig.java`
- `src/main/java/com/researchcube/compat/jei/ResearchCubeJEIPlugin.java`
- `src/main/java/com/researchcube/compat/emi/ResearchCubeEMIPlugin.java`
- `src/main/java/com/researchcube/compat/jade/ResearchCubeJadePlugin.java`
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
