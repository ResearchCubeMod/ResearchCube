package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.sideio.IOChannel;
import com.researchcube.sideio.IOMode;
import com.researchcube.sideio.RelativeSide;
import com.researchcube.sideio.SideConfigurable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet setting one side's {@link IOMode} for one channel of a
 * {@link SideConfigurable} block entity.
 *
 * @param pos       the block entity position
 * @param channelId the target channel id
 * @param side      the relative side ordinal
 * @param mode      the requested mode ordinal
 */
public record SetSideConfigPacket(BlockPos pos, String channelId, byte side, byte mode) implements CustomPacketPayload {

    public static final Type<SetSideConfigPacket> TYPE =
            new Type<>(ResearchCubeMod.rl("set_side_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSideConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetSideConfigPacket::pos,
                    ByteBufCodecs.STRING_UTF8, SetSideConfigPacket::channelId,
                    ByteBufCodecs.BYTE, SetSideConfigPacket::side,
                    ByteBufCodecs.BYTE, SetSideConfigPacket::mode,
                    SetSideConfigPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSideConfigPacket packet, IPayloadContext context) {
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

            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (!(blockEntity instanceof SideConfigurable configurable)) return;

            RelativeSide[] sides = RelativeSide.values();
            IOMode[] modes = IOMode.values();
            if (packet.side() < 0 || packet.side() >= sides.length) return;
            if (packet.mode() < 0 || packet.mode() >= modes.length) return;

            RelativeSide side = sides[packet.side()];
            IOMode mode = modes[packet.mode()];

            IOChannel channel = configurable.getChannel(packet.channelId());
            if (channel == null || !channel.isModeAllowed(mode)) return;

            if (configurable.getSideIOConfig().setMode(packet.channelId(), side, mode)) {
                configurable.onSideConfigChanged();
            }
        });
    }
}
