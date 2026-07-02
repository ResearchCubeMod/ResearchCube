package com.researchcube.research;

import com.researchcube.ResearchCubeMod;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Central registry for all loaded research definitions.
 *
 * On the (logical) server this is populated by {@link ResearchManager} on datapack reload.
 * On remote clients it is populated by SyncResearchDefinitionsPacket whenever the server
 * (re)loads its datapacks — screens, JEI/EMI and tooltips therefore see the same data on
 * both sides, including on dedicated servers.
 *
 * The backing map is an immutable snapshot that is swapped atomically, so readers on
 * other threads (render thread in singleplayer during /reload) never observe a
 * half-populated registry.
 */
public class ResearchRegistry {

    /** Immutable snapshot, replaced atomically on reload/sync. */
    private static volatile Map<ResourceLocation, ResearchDefinition> snapshot = Map.of();

    private ResearchRegistry() {}

    /**
     * Replace the entire registry contents with a new set of definitions.
     * Called by ResearchManager (server, on datapack reload) and by the
     * definition sync packet handler (client).
     */
    public static void setAll(Collection<ResearchDefinition> definitions) {
        Map<ResourceLocation, ResearchDefinition> map = new LinkedHashMap<>();
        for (ResearchDefinition def : definitions) {
            ResearchDefinition previous = map.put(def.getId(), def);
            if (previous != null) {
                ResearchCubeMod.LOGGER.warn("Duplicate research definition '{}' — keeping the last one.", def.getId());
            }
        }
        snapshot = Collections.unmodifiableMap(map);
        ResearchCubeMod.LOGGER.debug("ResearchRegistry snapshot replaced: {} definitions.", map.size());
    }

    /**
     * Clear all definitions.
     */
    public static void clear() {
        snapshot = Map.of();
        ResearchCubeMod.LOGGER.debug("ResearchRegistry cleared.");
    }

    /**
     * Get a research definition by its ResourceLocation ID.
     */
    @Nullable
    public static ResearchDefinition get(ResourceLocation id) {
        return snapshot.get(id);
    }

    /**
     * Get a research definition by its string ID (e.g., "researchcube:advanced_processor").
     * Returns null for malformed IDs instead of throwing.
     */
    @Nullable
    public static ResearchDefinition get(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null ? snapshot.get(rl) : null;
    }

    /**
     * Returns all registered research definitions (immutable view, load order preserved).
     */
    public static Collection<ResearchDefinition> getAll() {
        return snapshot.values();
    }

    /**
     * Returns all registered research IDs (immutable view).
     */
    public static Set<ResourceLocation> getAllIds() {
        return snapshot.keySet();
    }

    /**
     * Returns all research definitions matching a specific tier.
     */
    public static List<ResearchDefinition> getByTier(ResearchTier tier) {
        List<ResearchDefinition> result = new ArrayList<>();
        for (ResearchDefinition def : snapshot.values()) {
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
        for (ResearchDefinition def : snapshot.values()) {
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
        return snapshot.size();
    }

    /**
     * Check if a research ID is registered.
     */
    public static boolean contains(ResourceLocation id) {
        return snapshot.containsKey(id);
    }
}
