package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all custom network packets for the mod.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ResearchCubeMod.MOD_ID).versioned("1.0");

        registrar.playToServer(
                StartResearchPacket.TYPE,
                StartResearchPacket.STREAM_CODEC,
                StartResearchPacket::handle
        );

        registrar.playToServer(
                CancelResearchPacket.TYPE,
                CancelResearchPacket.STREAM_CODEC,
                CancelResearchPacket::handle
        );

        registrar.playToServer(
                WipeTankPacket.TYPE,
                WipeTankPacket.STREAM_CODEC,
                WipeTankPacket::handle
        );

        registrar.playToServer(
                StartProcessingPacket.TYPE,
                StartProcessingPacket.STREAM_CODEC,
                StartProcessingPacket::handle
        );

        registrar.playToClient(
                OpenResearchBookPacket.TYPE,
                OpenResearchBookPacket.STREAM_CODEC,
                OpenResearchBookPacket::handle
        );

        registrar.playToClient(
                SyncResearchProgressPacket.TYPE,
                SyncResearchProgressPacket.STREAM_CODEC,
                SyncResearchProgressPacket::handle
        );
    }
}
