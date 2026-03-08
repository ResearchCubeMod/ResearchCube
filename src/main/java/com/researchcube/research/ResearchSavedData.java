package com.researchcube.research;

import com.researchcube.ResearchCubeMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;

import java.util.*;

/**
 * Server-side research progress tracker.
 * Stores which research each "research key" has completed.
 *
 * A research key is:
 *   - The player's scoreboard team name, if they are on a team (shared progress)
 *   - The player's UUID as a string, if they are not on a team (solo progress)
 *
 * Persisted to the world's data folder.
 */
public class ResearchSavedData extends SavedData {

    private static final String DATA_NAME = ResearchCubeMod.MOD_ID + "_research";

    /** Map from research key (team name or UUID string) to completed research IDs. */
    private final Map<String, Set<ResourceLocation>> completedResearch = new HashMap<>();

    public ResearchSavedData() {
    }

    // ── Key Resolution ──

    /**
     * Resolve the research key for a player.
     * If the player is on a scoreboard team, returns the team name (shared pool).
     * Otherwise, returns the player's UUID as a string (isolated pool).
     */
    public static String getResearchKey(ServerPlayer player) {
        PlayerTeam team = player.getTeam() instanceof PlayerTeam pt ? pt : null;
        if (team != null) {
            return "team:" + team.getName();
        }
        return player.getUUID().toString();
    }

    // ── Query ──

    /**
     * Get the set of completed research IDs for a research key.
     */
    public Set<ResourceLocation> getCompletedResearch(String researchKey) {
        return completedResearch.getOrDefault(researchKey, Collections.emptySet());
    }

    /**
     * Get completed research as string set (for Prerequisite.isSatisfied).
     */
    public Set<String> getCompletedResearchStrings(String researchKey) {
        Set<ResourceLocation> completed = getCompletedResearch(researchKey);
        Set<String> result = new HashSet<>(completed.size());
        for (ResourceLocation rl : completed) {
            result.add(rl.toString());
        }
        return result;
    }

    /**
     * Check if a research key has completed a specific research.
     */
    public boolean hasCompleted(String researchKey, ResourceLocation researchId) {
        return getCompletedResearch(researchKey).contains(researchId);
    }

    // ── Mutation ──

    /**
     * Mark a research as completed for a research key.
     */
    public void addCompleted(String researchKey, ResourceLocation researchId) {
        completedResearch.computeIfAbsent(researchKey, k -> new HashSet<>()).add(researchId);
        setDirty();
    }

    /**
     * Remove a completed research entry (for admin/debug use).
     */
    public void removeCompleted(String researchKey, ResourceLocation researchId) {
        Set<ResourceLocation> set = completedResearch.get(researchKey);
        if (set != null) {
            set.remove(researchId);
            if (set.isEmpty()) {
                completedResearch.remove(researchKey);
            }
            setDirty();
        }
    }

    /**
     * Clear all completed research for a research key.
     */
    public void clearKey(String researchKey) {
        if (completedResearch.remove(researchKey) != null) {
            setDirty();
        }
    }

    // ── NBT Persistence ──

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag keys = new CompoundTag();
        for (Map.Entry<String, Set<ResourceLocation>> entry : completedResearch.entrySet()) {
            ListTag list = new ListTag();
            for (ResourceLocation rl : entry.getValue()) {
                list.add(StringTag.valueOf(rl.toString()));
            }
            keys.put(entry.getKey(), list);
        }
        tag.put("Players", keys);
        return tag;
    }

    private static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        CompoundTag keys = tag.getCompound("Players");
        for (String key : keys.getAllKeys()) {
            try {
                ListTag list = keys.getList(key, Tag.TAG_STRING);
                Set<ResourceLocation> set = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    set.add(ResourceLocation.parse(list.getString(i)));
                }
                data.completedResearch.put(key, set);
            } catch (Exception e) {
                ResearchCubeMod.LOGGER.warn("Failed to parse research data for key '{}': {}", key, e.getMessage());
            }
        }
        return data;
    }

    // ── Factory / Access ──

    private static final SavedData.Factory<ResearchSavedData> FACTORY = new SavedData.Factory<>(
            ResearchSavedData::new,
            ResearchSavedData::load
    );

    /**
     * Get the ResearchSavedData for the given server, creating it if needed.
     */
    public static ResearchSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Convenience: get from a ServerLevel.
     */
    public static ResearchSavedData get(ServerLevel level) {
        return get(level.getServer());
    }
}
