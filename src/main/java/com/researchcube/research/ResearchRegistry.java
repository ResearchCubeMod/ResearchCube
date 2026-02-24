package com.researchcube.research;

import com.researchcube.ResearchCubeMod;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Central registry for all loaded research definitions.
 * Populated by ResearchManager on datapack reload.
 * Provides lookup by ID and tier filtering.
 */
public class ResearchRegistry {

    private static final Map<ResourceLocation, ResearchDefinition> REGISTRY = new LinkedHashMap<>();

    private ResearchRegistry() {}

    /**
     * Clear all definitions. Called before datapack reload.
     */
    public static void clear() {
        REGISTRY.clear();
        ResearchCubeMod.LOGGER.debug("ResearchRegistry cleared.");
    }

    /**
     * Register a research definition. Overwrites if ID already exists.
     */
    public static void register(ResearchDefinition definition) {
        REGISTRY.put(definition.getId(), definition);
        ResearchCubeMod.LOGGER.debug("Registered research: {}", definition.getId());
    }

    /**
     * Get a research definition by its ResourceLocation ID.
     */
    @Nullable
    public static ResearchDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    /**
     * Get a research definition by its string ID (e.g., "researchcube:advanced_processor").
     */
    @Nullable
    public static ResearchDefinition get(String id) {
        return get(ResourceLocation.parse(id));
    }

    /**
     * Returns all registered research definitions.
     */
    public static Collection<ResearchDefinition> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * Returns all research definitions matching a specific tier.
     */
    public static List<ResearchDefinition> getByTier(ResearchTier tier) {
        List<ResearchDefinition> result = new ArrayList<>();
        for (ResearchDefinition def : REGISTRY.values()) {
            if (def.getTier() == tier) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Returns all research definitions whose tier is at most the given tier.
     * Useful for filtering what research a given cube can support.
     */
    public static List<ResearchDefinition> getUpToTier(ResearchTier maxTier) {
        List<ResearchDefinition> result = new ArrayList<>();
        for (ResearchDefinition def : REGISTRY.values()) {
            if (maxTier.isAtLeast(def.getTier())) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Returns the number of registered definitions.
     */
    public static int size() {
        return REGISTRY.size();
    }

    /**
     * Check if a research ID is registered.
     */
    public static boolean contains(ResourceLocation id) {
        return REGISTRY.containsKey(id);
    }
}
