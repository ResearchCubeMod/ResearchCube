package com.researchcube.research.prerequisite;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A prerequisite condition for research. Can be AND, OR, or a single research ID.
 * Evaluated against a set of completed research IDs.
 */
public interface Prerequisite {

    /**
     * Returns true if this prerequisite is satisfied given the set of completed research IDs.
     */
    boolean isSatisfied(Set<String> completedResearch);

    /**
     * Return a human-readable description for tooltips/UI.
     */
    String describe();

    /**
     * Feed every research ID referenced anywhere in this prerequisite tree to the consumer.
     * Used for datapack validation (missing references, cycle detection).
     */
    default void collectResearchIds(Consumer<String> collector) {
    }

    // ── Network serialization ──
    // Prerequisite trees are synced to the client as part of ResearchDefinition
    // so screens work on dedicated servers. Encoded as a type byte + payload.

    byte NET_NONE = 0;
    byte NET_SINGLE = 1;
    byte NET_AND = 2;
    byte NET_OR = 3;
    /** Depth guard against malformed packets. */
    int MAX_NET_DEPTH = 16;

    StreamCodec<FriendlyByteBuf, Prerequisite> STREAM_CODEC = StreamCodec.of(
            Prerequisite::toNetwork,
            buf -> fromNetwork(buf, 0)
    );

    private static void toNetwork(FriendlyByteBuf buf, Prerequisite prerequisite) {
        if (prerequisite instanceof SinglePrerequisite single) {
            buf.writeByte(NET_SINGLE);
            buf.writeUtf(single.getResearchId());
        } else if (prerequisite instanceof AndPrerequisite and) {
            buf.writeByte(NET_AND);
            buf.writeVarInt(and.getChildren().size());
            for (Prerequisite child : and.getChildren()) {
                toNetwork(buf, child);
            }
        } else if (prerequisite instanceof OrPrerequisite or) {
            buf.writeByte(NET_OR);
            buf.writeVarInt(or.getChildren().size());
            for (Prerequisite child : or.getChildren()) {
                toNetwork(buf, child);
            }
        } else {
            buf.writeByte(NET_NONE);
        }
    }

    private static Prerequisite fromNetwork(FriendlyByteBuf buf, int depth) {
        if (depth > MAX_NET_DEPTH) {
            throw new IllegalStateException("Prerequisite tree too deep (max " + MAX_NET_DEPTH + ")");
        }
        byte type = buf.readByte();
        return switch (type) {
            case NET_SINGLE -> new SinglePrerequisite(buf.readUtf());
            case NET_AND -> new AndPrerequisite(readChildren(buf, depth));
            case NET_OR -> new OrPrerequisite(readChildren(buf, depth));
            default -> NonePrerequisite.INSTANCE;
        };
    }

    private static List<Prerequisite> readChildren(FriendlyByteBuf buf, int depth) {
        int count = buf.readVarInt();
        List<Prerequisite> children = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            children.add(fromNetwork(buf, depth + 1));
        }
        return children;
    }
}
