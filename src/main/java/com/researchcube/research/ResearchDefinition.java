package com.researchcube.research;

import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.NonePrerequisite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Immutable definition of a single research entry, loaded from datapack JSON.
 *
 * JSON structure:
 * {
 *   "tier": "ADVANCED",
 *   "duration": 6000,
 *   "category": "circuits",
 *   "prerequisites": { "type": "AND", "values": [...] },
 *   "item_costs": [ { "item": "minecraft:redstone", "count": 16 } ],
 *   "recipe_pool": [ "researchcube:processor_recipe_1", {"id": "researchcube:processor_recipe_2", "weight": 3} ]
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
    private final List<WeightedRecipe> weightedRecipePool;
    @Nullable
    private final String name;        // human-readable display name (optional)
    @Nullable
    private final String description;  // short description (optional)
    @Nullable
    private final String category;     // optional grouping category (e.g., "circuits", "energy")
    @Nullable
    private final FluidCost fluidCost; // optional fluid cost for this research
    private final Optional<ItemStack> ideaChip; // optional idea chip required to start this research

    public ResearchDefinition(ResourceLocation id, ResearchTier tier, int duration,
                              Prerequisite prerequisites, List<ItemCost> itemCosts,
                              List<WeightedRecipe> weightedRecipePool,
                              @Nullable String name, @Nullable String description,
                              @Nullable String flavorText, @Nullable String category,
                              @Nullable FluidCost fluidCost,
                              Optional<ItemStack> ideaChip) {
        this.id = id;
        this.tier = tier;
        this.duration = duration;
        this.prerequisites = prerequisites != null ? prerequisites : NonePrerequisite.INSTANCE;
        this.itemCosts = itemCosts != null ? List.copyOf(itemCosts) : List.of();
        this.weightedRecipePool = weightedRecipePool != null ? List.copyOf(weightedRecipePool) : List.of();
        this.recipePool = this.weightedRecipePool.stream().map(WeightedRecipe::id).toList();
        this.name = name;
        this.description = description;
        this.flavorText = flavorText;
        this.category = category;
        this.fluidCost = fluidCost;
        this.ideaChip = ideaChip != null ? ideaChip : Optional.empty();
    }

    /**
     * Constructor without ideaChip (backwards-compatible).
     */
    public ResearchDefinition(ResourceLocation id, ResearchTier tier, int duration,
                              Prerequisite prerequisites, List<ItemCost> itemCosts,
                              List<WeightedRecipe> weightedRecipePool,
                              @Nullable String name, @Nullable String description,
                              @Nullable String flavorText, @Nullable String category,
                              @Nullable FluidCost fluidCost) {
        this(id, tier, duration, prerequisites, itemCosts, weightedRecipePool,
                name, description, flavorText, category, fluidCost, Optional.empty());
    }

    /**
     * Constructor without fluidCost or ideaChip (backwards-compatible).
     */
    public ResearchDefinition(ResourceLocation id, ResearchTier tier, int duration,
                              Prerequisite prerequisites, List<ItemCost> itemCosts,
                              List<WeightedRecipe> weightedRecipePool,
                              @Nullable String name, @Nullable String description,
                              @Nullable String flavorText, @Nullable String category) {
        this(id, tier, duration, prerequisites, itemCosts, weightedRecipePool,
                name, description, flavorText, category, null);
    }

    /**
     * Backwards-compatible constructor (no name/description/category/fluidCost, plain ResourceLocation pool).
     */
    public ResearchDefinition(ResourceLocation id, ResearchTier tier, int duration,
                              Prerequisite prerequisites, List<ItemCost> itemCosts,
                              List<ResourceLocation> recipePool) {
        this(id, tier, duration, prerequisites, itemCosts,
                recipePool.stream().map(rl -> new WeightedRecipe(rl, 1)).toList(),
                null, null, null, null, null);
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
     * Pool of recipe ResourceLocations. On completion, one is chosen via weighted random.
     */
    public List<ResourceLocation> getRecipePool() {
        return recipePool;
    }

    /**
     * Pool of weighted recipe entries for weighted random selection.
     */
    public List<WeightedRecipe> getWeightedRecipePool() {
        return weightedRecipePool;
    }

    /**
     * Select a recipe from the pool using weighted random selection.
     * Returns null if the pool is empty.
     */
    @Nullable
    public ResourceLocation pickWeightedRecipe(RandomSource random) {
        if (weightedRecipePool.isEmpty()) return null;
        int totalWeight = 0;
        for (WeightedRecipe wr : weightedRecipePool) {
            totalWeight += wr.weight();
        }
        int roll = random.nextInt(totalWeight);
        for (WeightedRecipe wr : weightedRecipePool) {
            roll -= wr.weight();
            if (roll < 0) {
                return wr.id();
            }
        }
        // Fallback (should never happen)
        return weightedRecipePool.getLast().id();
    }

    /**
     * Optional human-readable name. Falls back to the path of the ID.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the display name: the name field if set, otherwise the ID path.
     */
    public String getDisplayName() {
        return name != null ? name : id.getPath();
    }

    /**
     * Optional short description.
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Optional story/lore flavor text for the detail pane.
     */
    @Nullable
    public String getFlavorText() {
        return flavorText;
    }

    /**
     * Optional category for grouping in the UI (e.g., "circuits", "energy").
     */
    @Nullable
    public String getCategory() {
        return category;
    }

    /**
     * Optional fluid cost for this research (e.g., 1000 mB of Thinking Fluid).
     */
    @Nullable
    public FluidCost getFluidCost() {
        return fluidCost;
    }

    /**
     * Optional idea chip required to start this research.
     * If present, the player must place a matching item in the idea chip slot.
     */
    public Optional<ItemStack> getIdeaChip() {
        return ideaChip;
    }

    /**
     * Returns duration as human-readable seconds.
     */
    public float getDurationSeconds() {
        return duration / 20.0f;
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
                ", category=" + category + ", costs=" + itemCosts.size() + ", recipes=" + recipePool.size() + "}";
    }
}
