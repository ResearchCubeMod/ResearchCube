package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet sent from client → server when the player clicks "Cancel" in the Research Table UI.
 * Cancels the active research and refunds item costs back into the cost slots.
 */
public record CancelResearchPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelResearchPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("cancel_research"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelResearchPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CancelResearchPacket::pos,
                    CancelResearchPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler. Cancels research and refunds item costs.
     */
    public static void handle(CancelResearchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ResearchTableBlockEntity be) {
                if (be.isResearching()) {
                    be.cancelResearchWithRefund();
                    ResearchCubeMod.LOGGER.debug("Player {} cancelled research at {}",
                            player.getName().getString(), packet.pos());
                }
            }
        });
    }
}
