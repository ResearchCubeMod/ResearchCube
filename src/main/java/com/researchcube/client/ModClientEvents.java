package com.researchcube.client;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.renderer.ResearchStationRenderer;
import com.researchcube.client.screen.ResearchTableScreen;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side event handlers: registers screens, block entity renderers, etc.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RESEARCH_STATION.get(),
                ctx -> new ResearchStationRenderer());
    }
}
