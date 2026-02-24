package com.researchcube.research;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Represents a single item cost for research.
 * Parsed from JSON: { "item": "minecraft:redstone", "count": 16 }
 */
public record ItemCost(ResourceLocation itemId, int count) {

    /**
     * Resolve the Item from the registry. Returns null if not found.
     */
    public Item getItem() {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    /**
     * Returns true if this item cost references a valid registered item.
     */
    public boolean isValid() {
        return BuiltInRegistries.ITEM.containsKey(itemId);
    }
}
