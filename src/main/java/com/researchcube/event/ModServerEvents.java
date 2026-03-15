package com.researchcube.event;

import com.researchcube.ResearchCubeMod;
import com.researchcube.command.ResearchCubeCommand;
import com.researchcube.research.ResearchManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers server-side reload listeners (datapack loading) and commands.
 * This hooks ResearchManager into the datapack reload lifecycle.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ResearchManager());
        ResearchCubeMod.LOGGER.debug("Registered ResearchManager reload listener.");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCubeCommand.register(event.getDispatcher());
        ResearchCubeMod.LOGGER.debug("Registered /researchcube command.");
    }
}
