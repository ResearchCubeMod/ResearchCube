package com.researchcube.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModRecipeTypes;
import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.PrerequisiteParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Datapack reload listener that loads research definitions from:
 *   data/{namespace}/research/{name}.json
 *
 * The ResourceLocation ID is derived from namespace + filename, e.g.
 *   data/researchcube/research/advanced_processor.json → "researchcube:advanced_processor"
 *
 * Any datapack (the built-in example pack, world datapacks, other mods) can add,
 * override or (via pack ordering) replace research files. Files whose name starts
 * with '_' are ignored (convention for shared metadata/comments), and NeoForge
 * "neoforge:conditions" are honored so packs can make entries conditional on loaded mods.
 *
 * After loading, the manager runs a validation pass that reports (without crashing):
 *   - unknown prerequisite references and prerequisite cycles
 *   - recipe_pool entries that don't resolve to a loaded recipe
 *   - recipe_pool entries that no drive_crafting/processing recipe would ever match
 *   - unknown items in item_costs and unknown fluids in fluid_cost
 *
 * It also binds every drive_crafting AND processing recipe that omitted the optional
 * "recipe_id" field to its own recipe ID, which removes the old copy-the-filename
 * footgun. Both recipe types are research-locked the same way: the drive must carry
 * the resolved recipe_id.
 */
public class ResearchManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String FOLDER = "research";

    @Nullable
    private final RegistryAccess registryAccess;
    @Nullable
    private final ICondition.IContext conditionContext;
    @Nullable
    private final ReloadableServerResources serverResources;

    public ResearchManager(@Nullable RegistryAccess registryAccess,
                           @Nullable ICondition.IContext conditionContext,
                           @Nullable ReloadableServerResources serverResources) {
        super(GSON, FOLDER);
        this.registryAccess = registryAccess;
        this.conditionContext = conditionContext;
        this.serverResources = serverResources;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        DynamicOps<JsonElement> ops = buildOps();

        Map<ResourceLocation, ResearchDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skippedByCondition = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.getPath().startsWith("_")) {
                continue; // convention: underscore files are metadata/comments
            }
            try {
                if (!(entry.getValue() instanceof JsonObject json)) {
                    throw new IllegalArgumentException("root element must be a JSON object");
                }
                // NeoForge conditions: skip entries whose conditions are not met
                if (!ICondition.conditionsMatched(ops, json)) {
                    skippedByCondition++;
                    ResearchCubeMod.LOGGER.debug("Research '{}' skipped: conditions not met.", id);
                    continue;
                }
                loaded.put(id, parseDefinition(id, json, ops));
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
                ResearchCubeMod.LOGGER.error("Failed to load research definition '{}': {}", id, e.getMessage());
            }
        }

        ResearchRegistry.setAll(loaded.values());

        // Bind drive_crafting + processing recipes without an explicit recipe_id to their
        // own ID, then validate everything that can only be checked after recipes are loaded.
        RecipeManager recipeManager = serverResources != null ? serverResources.getRecipeManager() : null;
        Set<String> driveRecipeIds = bindDriveCraftingRecipes(recipeManager);
        Set<String> processingRecipeIds = bindProcessingRecipes(recipeManager);
        int warnings = validate(loaded, recipeManager, driveRecipeIds, processingRecipeIds);

        ResearchCubeMod.LOGGER.info(
                "[ResearchCube] Loaded {} research definitions ({} failed, {} skipped by conditions, {} validation warnings).",
                loaded.size(), errors.size(), skippedByCondition, warnings);
        if (!errors.isEmpty()) {
            ResearchCubeMod.LOGGER.error("[ResearchCube] Research files with errors:");
            for (String error : errors) {
                ResearchCubeMod.LOGGER.error("  - {}", error);
            }
        }
    }

    private DynamicOps<JsonElement> buildOps() {
        RegistryOps<JsonElement> registryOps = registryAccess != null
                ? RegistryOps.create(JsonOps.INSTANCE, registryAccess)
                : null;
        if (registryOps != null && conditionContext != null) {
            return new ConditionalOps<>(registryOps, conditionContext);
        }
        return registryOps != null ? registryOps : JsonOps.INSTANCE;
    }

    // ── Parsing ──

    /**
     * Parse a single research definition from JSON. Throws with a descriptive
     * message on any malformed field; the caller reports and skips the file.
     */
    private ResearchDefinition parseDefinition(ResourceLocation id, JsonObject json, DynamicOps<JsonElement> ops) {
        // Tier (required)
        if (!json.has("tier")) {
            throw new IllegalArgumentException("missing required field 'tier' (one of: UNSTABLE, BASIC, ADVANCED, PRECISE, FLAWLESS, SELF_AWARE)");
        }
        String tierStr = json.get("tier").getAsString();
        ResearchTier tier = ResearchTier.fromString(tierStr);
        if (tier == null) {
            throw new IllegalArgumentException("unknown tier '" + tierStr + "' (one of: UNSTABLE, BASIC, ADVANCED, PRECISE, FLAWLESS, SELF_AWARE)");
        }

        // Duration in ticks (required)
        if (!json.has("duration")) {
            throw new IllegalArgumentException("missing required field 'duration' (in ticks, 20 ticks = 1 second)");
        }
        int duration = json.get("duration").getAsInt();
        if (duration <= 0) {
            throw new IllegalArgumentException("'duration' must be positive, got: " + duration);
        }

        // Prerequisites (optional)
        Prerequisite prerequisites = PrerequisiteParser.parse(
                json.has("prerequisites") ? json.get("prerequisites") : null
        );

        // Item costs (optional)
        List<ItemCost> itemCosts = new ArrayList<>();
        if (json.has("item_costs")) {
            JsonArray costsArray = json.getAsJsonArray("item_costs");
            for (JsonElement costElement : costsArray) {
                JsonObject costObj = costElement.getAsJsonObject();
                if (!costObj.has("item")) {
                    throw new IllegalArgumentException("item_costs entry is missing the 'item' field");
                }
                ResourceLocation itemId = parseId(costObj.get("item").getAsString(), "item_costs.item");
                int count = costObj.has("count") ? costObj.get("count").getAsInt() : 1;
                if (count <= 0) {
                    throw new IllegalArgumentException("item_costs count for '" + itemId + "' must be positive, got: " + count);
                }
                itemCosts.add(new ItemCost(itemId, count));
            }
        }

        // Recipe pool (optional but expected); supports weighted entries
        List<WeightedRecipe> weightedRecipePool = new ArrayList<>();
        if (json.has("recipe_pool")) {
            JsonArray poolArray = json.getAsJsonArray("recipe_pool");
            for (JsonElement poolElement : poolArray) {
                if (poolElement.isJsonPrimitive()) {
                    // Plain string: "researchcube:some_recipe" → weight 1
                    weightedRecipePool.add(new WeightedRecipe(parseId(poolElement.getAsString(), "recipe_pool"), 1));
                } else if (poolElement.isJsonObject()) {
                    // Object: {"id": "researchcube:some_recipe", "weight": 3}
                    JsonObject obj = poolElement.getAsJsonObject();
                    if (!obj.has("id")) {
                        throw new IllegalArgumentException("recipe_pool object entry is missing the 'id' field");
                    }
                    ResourceLocation recipeId = parseId(obj.get("id").getAsString(), "recipe_pool.id");
                    int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
                    if (weight <= 0) {
                        throw new IllegalArgumentException("recipe_pool weight for '" + recipeId + "' must be positive, got: " + weight);
                    }
                    weightedRecipePool.add(new WeightedRecipe(recipeId, weight));
                } else {
                    throw new IllegalArgumentException("recipe_pool entries must be strings or objects");
                }
            }
        }

        return ResearchDefinition.builder(id, tier, duration)
                .prerequisites(prerequisites)
                .itemCosts(itemCosts)
                .recipePool(weightedRecipePool)
                .name(optionalString(json, "name"))
                .description(optionalString(json, "description"))
                .flavorText(optionalString(json, "flavor_text"))
                .category(optionalString(json, "category"))
                .fluidCost(parseFluidCost(json))
                .ideaChip(parseIdeaChip(id, json, ops))
                .build();
    }

    private static ResourceLocation parseId(String raw, String field) {
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            throw new IllegalArgumentException("'" + field + "' contains an invalid resource location: '" + raw + "'");
        }
        return rl;
    }

    @Nullable
    private static String optionalString(JsonObject json, String field) {
        return json.has(field) ? json.get(field).getAsString() : null;
    }

    /**
     * Parse optional fluid cost: { "fluid": "researchcube:thinking_fluid", "amount": 1000 }
     */
    @Nullable
    private FluidCost parseFluidCost(JsonObject json) {
        if (!json.has("fluid_cost")) return null;
        JsonObject fluidObj = json.getAsJsonObject("fluid_cost");
        if (!fluidObj.has("fluid")) {
            throw new IllegalArgumentException("fluid_cost is missing the 'fluid' field");
        }
        ResourceLocation fluidId = parseId(fluidObj.get("fluid").getAsString(), "fluid_cost.fluid");
        int amount = fluidObj.has("amount") ? fluidObj.get("amount").getAsInt() : 1000;
        if (amount <= 0) {
            throw new IllegalArgumentException("fluid_cost amount must be positive, got: " + amount);
        }
        return new FluidCost(fluidId, amount);
    }

    /**
     * Parse optional idea chip: an ItemStack decoded via ItemStack.CODEC.
     * Example: { "idea_chip": { "id": "researchcube:metadata_irrecoverable", "components": { ... } } }
     */
    private Optional<ItemStack> parseIdeaChip(ResourceLocation id, JsonObject json, DynamicOps<JsonElement> ops) {
        if (!json.has("idea_chip")) return Optional.empty();
        JsonElement chipElement = json.get("idea_chip");
        return ItemStack.CODEC.parse(ops, chipElement)
                .resultOrPartial(error -> ResearchCubeMod.LOGGER.error("Research '{}': failed to parse idea_chip: {}", id, error))
                .filter(stack -> !stack.isEmpty());
    }

    // ── Recipe binding ──

    /**
     * Bind all drive_crafting recipes that omitted "recipe_id" to their own recipe ID
     * and return the set of all recipe_id strings drive recipes respond to.
     */
    private Set<String> bindDriveCraftingRecipes(@Nullable RecipeManager recipeManager) {
        Set<String> boundIds = new HashSet<>();
        if (recipeManager == null) return boundIds;
        try {
            for (RecipeHolder<DriveCraftingRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.DRIVE_CRAFTING.get())) {
                holder.value().bindId(holder.id());
                boundIds.add(holder.value().getRequiredRecipeId());
            }
        } catch (Exception e) {
            ResearchCubeMod.LOGGER.error("Failed to bind drive_crafting recipe IDs: {}", e.getMessage());
        }
        return boundIds;
    }

    /**
     * Bind all processing recipes that omitted "recipe_id" to their own recipe ID
     * and return the set of all recipe_id strings processing recipes respond to.
     * Processing recipes are research-locked exactly like drive_crafting ones.
     */
    private Set<String> bindProcessingRecipes(@Nullable RecipeManager recipeManager) {
        Set<String> boundIds = new HashSet<>();
        if (recipeManager == null) return boundIds;
        try {
            for (RecipeHolder<ProcessingRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.PROCESSING.get())) {
                holder.value().bindId(holder.id());
                boundIds.add(holder.value().getRequiredRecipeId());
            }
        } catch (Exception e) {
            ResearchCubeMod.LOGGER.error("Failed to bind processing recipe IDs: {}", e.getMessage());
        }
        return boundIds;
    }

    // ── Validation ──

    /**
     * Validate cross-references between research definitions, recipes and registries.
     * Only logs warnings; a broken reference never prevents the rest from loading.
     * Returns the number of warnings emitted.
     */
    private int validate(Map<ResourceLocation, ResearchDefinition> definitions,
                         @Nullable RecipeManager recipeManager,
                         Set<String> driveRecipeIds,
                         Set<String> processingRecipeIds) {
        int warnings = 0;

        for (ResearchDefinition def : definitions.values()) {
            // Prerequisite references must point at loaded research
            Set<String> referenced = new HashSet<>();
            def.getPrerequisites().collectResearchIds(referenced::add);
            for (String ref : referenced) {
                ResourceLocation refId = ResourceLocation.tryParse(ref);
                if (refId == null) {
                    ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': prerequisite '{}' is not a valid ID.", def.getId(), ref);
                    warnings++;
                } else if (!definitions.containsKey(refId)) {
                    ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': prerequisite '{}' does not exist; this research can never be started.", def.getId(), ref);
                    warnings++;
                }
            }

            // Item costs must reference registered items
            for (ItemCost cost : def.getItemCosts()) {
                if (!cost.isValid()) {
                    ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': item cost '{}' is not a registered item.", def.getId(), cost.itemId());
                    warnings++;
                }
            }

            // Fluid cost must reference a registered fluid
            if (def.getFluidCost() != null && !def.getFluidCost().isValid()) {
                ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': fluid cost '{}' is not a registered fluid.", def.getId(), def.getFluidCost().fluidId());
                warnings++;
            }

            // Recipe pool entries should resolve to loaded recipes
            if (recipeManager != null) {
                for (WeightedRecipe wr : def.getWeightedRecipePool()) {
                    Optional<RecipeHolder<?>> holder = recipeManager.byKey(wr.id());
                    if (holder.isEmpty()) {
                        ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': recipe_pool entry '{}' does not match any loaded recipe.", def.getId(), wr.id());
                        warnings++;
                    } else if (holder.get().value() instanceof DriveCraftingRecipe && !driveRecipeIds.contains(wr.id().toString())) {
                        // The drive would be imprinted with an ID no drive_crafting recipe answers to
                        ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': recipe_pool entry '{}' is a drive_crafting recipe, but no drive_crafting recipe uses that recipe_id; the imprinted drive would be useless. Remove the explicit \"recipe_id\" from the recipe file or make it match.", def.getId(), wr.id());
                        warnings++;
                    } else if (holder.get().value() instanceof ProcessingRecipe && !processingRecipeIds.contains(wr.id().toString())) {
                        // Same dead-unlock check for processing recipes
                        ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}': recipe_pool entry '{}' is a processing recipe, but no processing recipe uses that recipe_id; the imprinted drive would be useless. Remove the explicit \"recipe_id\" from the recipe file or make it match.", def.getId(), wr.id());
                        warnings++;
                    }
                }
            }

            if (!def.hasRecipePool()) {
                ResearchCubeMod.LOGGER.warn("[ResearchCube] Research '{}' has an empty recipe_pool; completing it will imprint nothing.", def.getId());
                warnings++;
            }
        }

        warnings += detectPrerequisiteCycles(definitions);
        return warnings;
    }

    /**
     * Detect cycles in the prerequisite graph via iterative DFS.
     * A cycle means none of the involved research can ever be started.
     */
    private int detectPrerequisiteCycles(Map<ResourceLocation, ResearchDefinition> definitions) {
        // Build adjacency: research → referenced (existing) prerequisites
        Map<ResourceLocation, List<ResourceLocation>> graph = new HashMap<>();
        for (ResearchDefinition def : definitions.values()) {
            List<ResourceLocation> edges = new ArrayList<>();
            def.getPrerequisites().collectResearchIds(ref -> {
                ResourceLocation refId = ResourceLocation.tryParse(ref);
                if (refId != null && definitions.containsKey(refId)) {
                    edges.add(refId);
                }
            });
            graph.put(def.getId(), edges);
        }

        final int WHITE = 0, GRAY = 1, BLACK = 2;
        Map<ResourceLocation, Integer> state = new HashMap<>();
        int warnings = 0;

        for (ResourceLocation start : graph.keySet()) {
            if (state.getOrDefault(start, WHITE) != WHITE) continue;

            // Iterative DFS with an explicit path stack for cycle reporting
            List<ResourceLocation> path = new ArrayList<>();
            List<java.util.Iterator<ResourceLocation>> iterators = new ArrayList<>();
            state.put(start, GRAY);
            path.add(start);
            iterators.add(graph.get(start).iterator());

            while (!path.isEmpty()) {
                java.util.Iterator<ResourceLocation> it = iterators.get(iterators.size() - 1);
                if (it.hasNext()) {
                    ResourceLocation next = it.next();
                    int nextState = state.getOrDefault(next, WHITE);
                    if (nextState == GRAY) {
                        int cycleStart = path.indexOf(next);
                        List<ResourceLocation> cycle = new ArrayList<>(path.subList(Math.max(cycleStart, 0), path.size()));
                        cycle.add(next);
                        ResearchCubeMod.LOGGER.warn("[ResearchCube] Prerequisite cycle detected; these research entries can never be started: {}", cycle);
                        warnings++;
                    } else if (nextState == WHITE) {
                        state.put(next, GRAY);
                        path.add(next);
                        iterators.add(graph.get(next).iterator());
                    }
                } else {
                    ResourceLocation done = path.remove(path.size() - 1);
                    iterators.remove(iterators.size() - 1);
                    state.put(done, BLACK);
                }
            }
        }
        return warnings;
    }
}
