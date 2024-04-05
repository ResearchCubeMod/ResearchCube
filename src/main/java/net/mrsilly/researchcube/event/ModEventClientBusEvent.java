package net.mrsilly.researchcube.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mrsilly.researchcube.ResearchCube;
import net.mrsilly.researchcube.block.entity.ModBlockEntities;
import net.mrsilly.researchcube.block.entity.client.ResearchStationBlockRenderer;

@Mod.EventBusSubscriber(modid = ResearchCube.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEventClientBusEvent {
    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {

        event.registerBlockEntityRenderer(ModBlockEntities.RESEARCH_STATION_BLOCK_ENTITY.get(), ResearchStationBlockRenderer::new);
    }
}
