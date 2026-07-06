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
 * Packet sent from client → server when the player clicks the "Wipe" button
 * in the Research Station UI. Voids all fluid in the tank.
 */
public record WipeTankPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WipeTankPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("wipe_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WipeTankPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WipeTankPacket::pos,
                    WipeTankPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler. Validates access and voids all tank fluid.
     */
    public static void handle(WipeTankPacket packet, IPayloadContext context) {
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
                be.wipeTank();
                ResearchCubeMod.LOGGER.debug("Player {} wiped fluid tank at {}",
                        player.getName().getString(), packet.pos());
            }
        });
    }
}
