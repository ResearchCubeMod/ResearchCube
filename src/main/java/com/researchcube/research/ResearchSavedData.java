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
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Server-side per-player research progress tracker.
 * Stores which research each player (by UUID) has completed.
 * Persisted to the world's data folder.
 */
public class ResearchSavedData extends SavedData {

    private static final String DATA_NAME = ResearchCubeMod.MOD_ID + "_research";

    private final Map<UUID, Set<ResourceLocation>> completedResearch = new HashMap<>();

    public ResearchSavedData() {
    }

    // ── Query ──

    /**
     * Get the set of completed research IDs for a player.
     */
    public Set<ResourceLocation> getCompletedResearch(UUID playerUUID) {
        return completedResearch.getOrDefault(playerUUID, Collections.emptySet());
    }

    /**
     * Get completed research as string set (for Prerequisite.isSatisfied).
     */
    public Set<String> getCompletedResearchStrings(UUID playerUUID) {
        Set<ResourceLocation> completed = getCompletedResearch(playerUUID);
        Set<String> result = new HashSet<>(completed.size());
        for (ResourceLocation rl : completed) {
            result.add(rl.toString());
        }
        return result;
    }

    /**
     * Check if a player has completed a specific research.
     */
    public boolean hasCompleted(UUID playerUUID, ResourceLocation researchId) {
        return getCompletedResearch(playerUUID).contains(researchId);
    }

    // ── Mutation ──

    /**
     * Mark a research as completed for a player.
     */
    public void addCompleted(UUID playerUUID, ResourceLocation researchId) {
        completedResearch.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(researchId);
        setDirty();
    }

    /**
     * Remove a completed research entry (for admin/debug use).
     */
    public void removeCompleted(UUID playerUUID, ResourceLocation researchId) {
        Set<ResourceLocation> set = completedResearch.get(playerUUID);
        if (set != null) {
            set.remove(researchId);
            if (set.isEmpty()) {
                completedResearch.remove(playerUUID);
            }
            setDirty();
        }
    }

    /**
     * Clear all completed research for a player.
     */
    public void clearPlayer(UUID playerUUID) {
        if (completedResearch.remove(playerUUID) != null) {
            setDirty();
        }
    }

    // ── NBT Persistence ──

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : completedResearch.entrySet()) {
            ListTag list = new ListTag();
            for (ResourceLocation rl : entry.getValue()) {
                list.add(StringTag.valueOf(rl.toString()));
            }
            players.put(entry.getKey().toString(), list);
        }
        tag.put("Players", players);
        return tag;
    }

    private static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        CompoundTag players = tag.getCompound("Players");
        for (String uuidStr : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag list = players.getList(uuidStr, Tag.TAG_STRING);
                Set<ResourceLocation> set = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    set.add(ResourceLocation.parse(list.getString(i)));
                }
                data.completedResearch.put(uuid, set);
            } catch (Exception e) {
                ResearchCubeMod.LOGGER.warn("Failed to parse research data for UUID '{}': {}", uuidStr, e.getMessage());
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
