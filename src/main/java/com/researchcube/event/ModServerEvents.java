package com.researchcube.event;

import com.researchcube.ResearchCubeMod;
import com.researchcube.command.ResearchCubeCommand;
import com.researchcube.research.ResearchManager;
import com.researchcube.research.ResearchSync;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers server-side reload listeners (datapack loading), commands and
 * the research → client sync hooks.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        // Runs after all vanilla listeners (recipes included), so the manager can
        // validate recipe_pool references and bind drive_crafting recipe IDs.
        event.addListener(new ResearchManager(
                event.getRegistryAccess(),
                event.getConditionContext(),
                event.getServerResources()
        ));
        ResearchCubeMod.LOGGER.debug("Registered ResearchManager reload listener.");
    }

    /**
     * Fired on player join and after every datapack reload (/reload,
     * /datapack enable|disable). Pushes the loaded research definitions and the
     * player's completed research to the affected clients.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(ResearchSync::syncAll);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCubeCommand.register(event.getDispatcher());
        ResearchCubeMod.LOGGER.debug("Registered /researchcube command.");
    }
}
