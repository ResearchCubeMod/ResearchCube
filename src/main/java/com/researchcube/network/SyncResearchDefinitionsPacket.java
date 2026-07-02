package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent server → client with the complete set of loaded research definitions.
 *
 * Sent on player join and after every datapack reload (/reload, /datapack enable|disable),
 * so the client-side {@link ResearchRegistry} always mirrors the server. Without this,
 * research screens and JEI/EMI integration would only work in singleplayer.
 */
public record SyncResearchDefinitionsPacket(List<ResearchDefinition> definitions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncResearchDefinitionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("sync_research_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResearchDefinitionsPacket> STREAM_CODEC =
            StreamCodec.of(SyncResearchDefinitionsPacket::encode, SyncResearchDefinitionsPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SyncResearchDefinitionsPacket packet) {
        buf.writeVarInt(packet.definitions.size());
        for (ResearchDefinition def : packet.definitions) {
            ResearchDefinition.STREAM_CODEC.encode(buf, def);
        }
    }

    private static SyncResearchDefinitionsPacket decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ResearchDefinition> definitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            definitions.add(ResearchDefinition.STREAM_CODEC.decode(buf));
        }
        return new SyncResearchDefinitionsPacket(definitions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Replaces the client's research registry snapshot.
     */
    public static void handle(SyncResearchDefinitionsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ResearchRegistry.setAll(packet.definitions);
            ResearchCubeMod.LOGGER.debug("Received {} research definitions from server.", packet.definitions.size());
        });
    }
}
