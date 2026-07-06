package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.ClientTankSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet sent server → client to sync a single fluid tank's full contents
 * (fluid type + amount + components) to the player viewing a machine menu.
 *
 * <p>Sent from {@link com.researchcube.menu.ProcessingStationMenu#broadcastChanges()}
 * whenever a tank's contents change, regardless of whether the fluid arrived via the
 * GUI, a recipe, or an external pipe/hopper. Carrying the whole {@link FluidStack}
 * (rather than a 16-bit ContainerData slot) lets the client render <em>any</em> fluid,
 * not just the mod's own, and avoids the registry-ID truncation ContainerData suffers.
 *
 * @param containerId the menu's container id (matches the open menu on the client)
 * @param tankIndex   0 = input 1, 1 = input 2, 2 = output
 * @param stack       full tank contents (may be empty)
 */
public record SyncTankPacket(int containerId, int tankIndex, FluidStack stack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTankPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("sync_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTankPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncTankPacket::containerId,
                    ByteBufCodecs.VAR_INT, SyncTankPacket::tankIndex,
                    FluidStack.OPTIONAL_STREAM_CODEC, SyncTankPacket::stack,
                    SyncTankPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Updates the cached tank contents on the currently open menu.
     */
    public static void handle(SyncTankPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTankSync.apply(packet.containerId(), packet.tankIndex(), packet.stack()));
    }
}
