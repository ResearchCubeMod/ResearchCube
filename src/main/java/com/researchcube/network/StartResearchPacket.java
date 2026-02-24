package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;

/**
 * Packet sent from client → server when the player clicks "Start Research" in the UI.
 * Contains the block position and the selected research ID.
 */
public record StartResearchPacket(BlockPos pos, String researchId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartResearchPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("start_research"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartResearchPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StartResearchPacket::pos,
                    ByteBufCodecs.STRING_UTF8, StartResearchPacket::researchId,
                    StartResearchPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler. Validates and starts the research.
     */
    public static void handle(StartResearchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ResearchTableBlockEntity be) {
                // For now, pass empty completed research set.
                // TODO: In a future phase, track per-player completed research.
                boolean started = be.tryStartResearch(packet.researchId(), new HashSet<>());
                if (started) {
                    ResearchCubeMod.LOGGER.debug("Player {} started research '{}'",
                            player.getName().getString(), packet.researchId());
                }
            }
        });
    }
}
