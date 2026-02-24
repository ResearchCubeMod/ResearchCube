# Copilot instructions for ResearchCube

## Big picture
- This is a NeoForge 1.21.1 mod (`Java 21`) with one core gameplay loop: players run research at a `Research Station`, then unlock gated `drive_crafting` recipes.
- Research definitions are **datapack-driven** from `data/*/research/*.json` and loaded on server reload via `ResearchManager`.
- The authoritative flow is server-side: UI click -> packet -> block entity validation -> timed completion -> recipe ID imprinted into drive NBT.

## Architecture and data flow
- Mod bootstrap and registry wiring is centralized in `ResearchCubeMod` and `registry/Mod*` classes.
- Research loading path:
  - `event/ModServerEvents` registers `ResearchManager` as reload listener.
  - `research/ResearchManager` parses JSON into `ResearchDefinition` and fills `ResearchRegistry`.
- Runtime research path:
  - `client/screen/ResearchTableScreen` sends `network/StartResearchPacket`.
  - `network/StartResearchPacket.handle` runs on server and calls `block/ResearchTableBlockEntity.tryStartResearch`.
  - `ResearchTableBlockEntity.serverTick` advances progress and completes research by writing a recipe ID to drive NBT (`util/NbtUtil`).
- Crafting gate path:
  - `recipe/DriveCraftingRecipe` checks for one `DriveItem` containing required recipe ID, then matches remaining ingredients shapelessly.

## Critical workflows
- Build: `./gradlew.bat build`
- Run client dev instance: `./gradlew.bat runClient`
- Run dedicated server: `./gradlew.bat runServer`
- Regenerate data outputs: `./gradlew.bat runData` (writes into `src/generated/resources`, already included in main resources).
- No test suite is currently configured; validate changes with `build` + in-game `runClient` behavior.

## Project-specific conventions
- Keep all new content namespaced with `ResearchCubeMod.rl(...)` and mod id `researchcube`.
- Register new game objects using `DeferredRegister` in `registry/Mod*` classes, then ensure they are registered in `ResearchCubeMod` constructor.
- Maintain server authority: do not start/complete research from client code; client screens should only send packets.
- Preserve slot semantics in `ResearchTableBlockEntity`/`ResearchTableMenu`:
  - slot 0 = drive, slot 1 = cube, slots 2-7 = item costs.
- Enforce tier rules through `TierUtil.canResearch` (cube tier >= research tier, drive tier == research tier).
- Store custom item data through Data Components (`DataComponents.CUSTOM_DATA`) via `NbtUtil`; avoid legacy direct stack tag patterns.
- For custom recipes, update both type + serializer (`ModRecipeTypes`, `ModRecipeSerializers`) and provide matching JSON under `data/researchcube/recipe`.
- Client-only registrations (screens/renderers) belong in `client/ModClientEvents` with `Dist.CLIENT` subscriber.

## JSON schemas used here
- Research JSON (`data/researchcube/research/*.json`): `tier`, `duration`, optional `prerequisites`, optional `item_costs`, optional `recipe_pool`.
- Prerequisites support string, nested `AND`/`OR` object trees (see `research/prerequisite/PrerequisiteParser`).
- Drive crafting JSON (`type: researchcube:drive_crafting`): `recipe_id`, `ingredients` (max 8), `result`.

## High-value reference files
- `src/main/java/com/researchcube/ResearchCubeMod.java`
- `src/main/java/com/researchcube/block/ResearchTableBlockEntity.java`
- `src/main/java/com/researchcube/research/ResearchManager.java`
- `src/main/java/com/researchcube/network/StartResearchPacket.java`
- `src/main/java/com/researchcube/recipe/DriveCraftingRecipe.java`
- `src/main/resources/data/researchcube/research/advanced_processor.json`
- `src/main/resources/data/researchcube/recipe/processor_recipe_1.json`
