package com.researchcube.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.researchcube.ResearchCubeMod;
import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.PrerequisiteParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        // Recipe pool (optional but expected)
        List<ResourceLocation> recipePool = new ArrayList<>();
        if (json.has("recipe_pool")) {
            JsonArray poolArray = json.getAsJsonArray("recipe_pool");
            for (JsonElement poolElement : poolArray) {
                recipePool.add(ResourceLocation.parse(poolElement.getAsString()));
            }
        }

        return new ResearchDefinition(id, tier, duration, prerequisites, itemCosts, recipePool,
                parseName(json), parseDescription(json));
    }

    @Nullable
    private String parseName(JsonObject json) {
        return json.has("name") ? json.get("name").getAsString() : null;
    }

    @Nullable
    private String parseDescription(JsonObject json) {
        return json.has("description") ? json.get("description").getAsString() : null;
    }
}
