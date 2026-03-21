package com.researchcube.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.researchcube.ResearchCubeMod;
import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.PrerequisiteParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack reload listener that loads research definitions from:
 *   data/{namespace}/research/{name}.json
 *
 * The resource path "research" is the folder under each namespace.
 * The ResourceLocation ID is derived from namespace + filename.
 *
 * Example: data/researchcube/research/advanced_processor.json
 *   → ID: researchcube:advanced_processor
 */
public class ResearchManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ResearchManager() {
        super(GSON, "research");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        ResearchRegistry.clear();

        int loaded = 0;
        int failed = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResearchDefinition definition = parseDefinition(id, json);
                ResearchRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                ResearchCubeMod.LOGGER.error("Failed to load research definition '{}': {}", id, e.getMessage());
                failed++;
            }
        }

        ResearchCubeMod.LOGGER.info("Loaded {} research definitions ({} failed)", loaded, failed);
    }

    /**
     * Parse a single research definition from JSON.
     */
    private ResearchDefinition parseDefinition(ResourceLocation id, JsonObject json) {
        // Tier (required)
        String tierStr = json.get("tier").getAsString();
        ResearchTier tier = ResearchTier.fromString(tierStr);
        if (tier == null) {
            throw new IllegalArgumentException("Unknown tier: " + tierStr);
        }

        // Duration in ticks (required)
        int duration = json.get("duration").getAsInt();
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be positive, got: " + duration);
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
                ResourceLocation itemId = ResourceLocation.parse(costObj.get("item").getAsString());
                int count = costObj.has("count") ? costObj.get("count").getAsInt() : 1;
                itemCosts.add(new ItemCost(itemId, count));
            }
        }

        // Recipe pool (optional but expected) — supports weighted entries
        List<WeightedRecipe> weightedRecipePool = new ArrayList<>();
        if (json.has("recipe_pool")) {
            JsonArray poolArray = json.getAsJsonArray("recipe_pool");
            for (JsonElement poolElement : poolArray) {
                if (poolElement.isJsonPrimitive()) {
                    // Plain string: "researchcube:some_recipe" → weight 1
                    weightedRecipePool.add(new WeightedRecipe(ResourceLocation.parse(poolElement.getAsString()), 1));
                } else if (poolElement.isJsonObject()) {
                    // Object: {"id": "researchcube:some_recipe", "weight": 3}
                    JsonObject obj = poolElement.getAsJsonObject();
                    ResourceLocation recipeId = ResourceLocation.parse(obj.get("id").getAsString());
                    int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
                    weightedRecipePool.add(new WeightedRecipe(recipeId, weight));
                }
            }
        }

        return new ResearchDefinition(id, tier, duration, prerequisites, itemCosts, weightedRecipePool,
                parseName(json), parseDescription(json), parseCategory(json),
                parseFluidCost(json), parseIdeaChip(json));
    }

    @Nullable
    private String parseName(JsonObject json) {
        return json.has("name") ? json.get("name").getAsString() : null;
    }

    @Nullable
    private String parseDescription(JsonObject json) {
        return json.has("description") ? json.get("description").getAsString() : null;
    }

    @Nullable
    private String parseCategory(JsonObject json) {
        return json.has("category") ? json.get("category").getAsString() : null;
    }

    /**
     * Parse optional fluid cost: { "fluid": "researchcube:thinking_fluid", "amount": 1000 }
     */
    @Nullable
    private FluidCost parseFluidCost(JsonObject json) {
        if (!json.has("fluid_cost")) return null;
        JsonObject fluidObj = json.getAsJsonObject("fluid_cost");
        ResourceLocation fluidId = ResourceLocation.parse(fluidObj.get("fluid").getAsString());
        int amount = fluidObj.has("amount") ? fluidObj.get("amount").getAsInt() : 1000;
        return new FluidCost(fluidId, amount);
    }

    /**
     * Parse optional idea chip: an ItemStack decoded via ItemStack.CODEC.
     * Example: { "idea_chip": { "id": "researchcube:metadata_irrecoverable", "components": { ... } } }
     */
    private Optional<ItemStack> parseIdeaChip(JsonObject json) {
        if (!json.has("idea_chip")) return Optional.empty();
        JsonElement chipElement = json.get("idea_chip");
        return ItemStack.CODEC.parse(JsonOps.INSTANCE, chipElement)
                .resultOrPartial(error -> ResearchCubeMod.LOGGER.error("Failed to parse idea_chip: {}", error))
                .filter(stack -> !stack.isEmpty());
    }
}
