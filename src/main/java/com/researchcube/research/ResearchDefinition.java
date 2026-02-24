package com.researchcube.research;

import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.NonePrerequisite;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Immutable definition of a single research entry, loaded from datapack JSON.
 *
 * JSON structure:
 * {
 *   "tier": "ADVANCED",
 *   "duration": 6000,
 *   "prerequisites": { "type": "AND", "values": [...] },
 *   "item_costs": [ { "item": "minecraft:redstone", "count": 16 } ],
 *   "recipe_pool": [ "researchcube:processor_recipe_1", "researchcube:processor_recipe_2" ]
 * }
 *
 * The "id" is derived from the datapack file path (e.g., data/researchcube/research/advanced_processor.json
 * → "researchcube:advanced_processor").
 */
public class ResearchDefinition {

    private final ResourceLocation id;
    private final ResearchTier tier;
    private final int duration; // in ticks
    private final Prerequisite prerequisites;
    private final List<ItemCost> itemCosts;
    private final List<ResourceLocation> recipePool;

    public ResearchDefinition(ResourceLocation id, ResearchTier tier, int duration,
                              Prerequisite prerequisites, List<ItemCost> itemCosts,
                              List<ResourceLocation> recipePool) {
        this.id = id;
        this.tier = tier;
        this.duration = duration;
        this.prerequisites = prerequisites != null ? prerequisites : NonePrerequisite.INSTANCE;
        this.itemCosts = itemCosts != null ? List.copyOf(itemCosts) : List.of();
        this.recipePool = recipePool != null ? List.copyOf(recipePool) : List.of();
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getIdString() {
        return id.toString();
    }

    public ResearchTier getTier() {
        return tier;
    }

    /**
     * Duration in game ticks.
     */
    public int getDuration() {
        return duration;
    }

    public Prerequisite getPrerequisites() {
        return prerequisites;
    }

    public List<ItemCost> getItemCosts() {
        return itemCosts;
    }

    /**
     * Pool of recipe ResourceLocations. On completion, one is chosen uniformly at random.
     */
    public List<ResourceLocation> getRecipePool() {
        return recipePool;
    }

    /**
     * Returns true if this definition has a non-empty recipe pool.
     */
    public boolean hasRecipePool() {
        return !recipePool.isEmpty();
    }

    @Override
    public String toString() {
        return "ResearchDefinition{" + id + ", tier=" + tier + ", duration=" + duration +
                ", costs=" + itemCosts.size() + ", recipes=" + recipePool.size() + "}";
    }
}
