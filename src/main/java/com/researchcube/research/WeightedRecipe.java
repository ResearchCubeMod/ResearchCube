package com.researchcube.research;

import net.minecraft.resources.ResourceLocation;

/**
 * A recipe pool entry with an associated weight for weighted random selection.
 * Parsed from JSON recipe_pool entries. Supports both plain string format
 * (weight defaults to 1) and object format: {"id": "...", "weight": 3}.
 */
public record WeightedRecipe(ResourceLocation id, int weight) {

    /**
     * Creates a weighted recipe with the default weight of 1.
     */
    public WeightedRecipe(ResourceLocation id) {
        this(id, 1);
    }

    /**
     * Validates that weight is positive.
     */
    public WeightedRecipe {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive, got: " + weight);
        }
    }
}
