package com.researchcube.research;

import com.researchcube.network.SyncCompletedResearchPacket;
import com.researchcube.network.SyncResearchDefinitionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

/**
 * Central place for pushing research state to clients.
 *
 * Definitions are synced on join and after every datapack reload; completed
 * research is synced whenever it changes (research completion, admin commands).
 * All client UI (screens, JEI/EMI, HUD) reads exclusively from these synced caches,
 * which is what makes the mod work on dedicated servers.
 */
public final class ResearchSync {

    private ResearchSync() {}

    /**
     * Send both the full definition registry and the player's completed research.
     * Called on join and after datapack reloads.
     */
    public static void syncAll(ServerPlayer player) {
        syncDefinitions(player);
        syncCompleted(player);
    }

    /**
     * Send the full research definition registry to one player.
     */
    public static void syncDefinitions(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new SyncResearchDefinitionsPacket(new ArrayList<>(ResearchRegistry.getAll())));
    }

    /**
     * Send the player's completed research set to that player.
     */
    public static void syncCompleted(ServerPlayer player) {
        ResearchSavedData data = ResearchSavedData.get(player.server);
        String key = ResearchSavedData.getResearchKey(player);
        // Copy: the packet may be handed to the local client without re-encoding
        PacketDistributor.sendToPlayer(player,
                new SyncCompletedResearchPacket(java.util.Set.copyOf(data.getCompletedResearch(key))));
    }

    /**
     * Push the completed set to every online player sharing the given research key
     * (the whole team, or the single player for solo keys).
     */
    public static void syncCompletedForKey(MinecraftServer server, String researchKey) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (researchKey.equals(ResearchSavedData.getResearchKey(player))) {
                syncCompleted(player);
            }
        }
    }
}
