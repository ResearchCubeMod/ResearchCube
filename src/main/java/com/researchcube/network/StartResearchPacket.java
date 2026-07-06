package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.research.ResearchSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

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

            // Validate before touching the world: reject if too far away (vanilla
            // 8-block container reach) or if the chunk isn't loaded (a getBlockEntity
            // lookup would otherwise force-load it).
            if (player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0) {
                return;
            }
            if (!player.level().isLoaded(packet.pos())) {
                return;
            }

            if (player.level().getBlockEntity(packet.pos()) instanceof ResearchTableBlockEntity be) {
                // Look up completed research using team-aware key from SavedData
                ResearchSavedData savedData = ResearchSavedData.get(player.serverLevel());
                String researchKey = ResearchSavedData.getResearchKey(player);
                Set<String> completed = savedData.getCompletedResearchStrings(researchKey);

                boolean started = be.tryStartResearch(packet.researchId(), completed, researchKey);
                if (started) {
                    ResearchCubeMod.LOGGER.debug("Player {} started research '{}'",
                            player.getName().getString(), packet.researchId());
                }
            }
        });
    }
}
