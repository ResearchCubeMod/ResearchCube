package com.researchcube.sideio;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable per-side IO configuration for a block entity: stores an {@link IOMode} for
 * every (channel id, {@link RelativeSide}) pair.
 *
 * <p>Constructed from the block entity's declared channel list with each side seeded to
 * the channel's {@link IOChannel#defaultMode()}. Setters validate against the channel's
 * {@link IOChannel#allowedModes()}. NBT (de)serialisation tolerates missing or unknown
 * entries so configurations survive channel-list changes across mod versions.
 */
public class SideIOConfig {

    /** channel id → (relative side → mode) */
    private final Map<String, Map<RelativeSide, IOMode>> modes = new HashMap<>();
    private final Map<String, IOChannel> channelsById = new HashMap<>();

    /**
     * Build a config for the given channels, applying each channel's default mode to
     * all six sides.
     */
    public SideIOConfig(List<IOChannel> channels) {
        for (IOChannel channel : channels) {
            channelsById.put(channel.id(), channel);
            Map<RelativeSide, IOMode> perSide = new HashMap<>();
            for (RelativeSide side : RelativeSide.values()) {
                perSide.put(side, channel.defaultMode());
            }
            modes.put(channel.id(), perSide);
        }
    }

    /**
     * Get the configured mode for a channel on a side. Returns {@link IOMode#NONE} if
     * the channel is unknown (defensive; should not happen for declared channels).
     */
    public IOMode getMode(String channelId, RelativeSide side) {
        Map<RelativeSide, IOMode> perSide = modes.get(channelId);
        if (perSide == null) return IOMode.NONE;
        return perSide.getOrDefault(side, IOMode.NONE);
    }

    /**
     * Set the mode for a channel on a side.
     *
     * @return {@code true} if the change was applied, {@code false} if the channel is
     *         unknown or the mode is not allowed for that channel.
     */
    public boolean setMode(String channelId, RelativeSide side, IOMode mode) {
        IOChannel channel = channelsById.get(channelId);
        if (channel == null || !channel.isModeAllowed(mode)) {
            return false;
        }
        modes.get(channelId).put(side, mode);
        return true;
    }

    // ── NBT Persistence ──

    /**
     * Write the config into a compound: one sub-key per channel id holding a byte per
     * side (indexed by {@link RelativeSide#ordinal()}).
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Map<RelativeSide, IOMode>> entry : modes.entrySet()) {
            byte[] sideModes = new byte[RelativeSide.values().length];
            for (RelativeSide side : RelativeSide.values()) {
                sideModes[side.ordinal()] = (byte) entry.getValue().getOrDefault(side, IOMode.NONE).ordinal();
            }
            tag.putByteArray(entry.getKey(), sideModes);
        }
        return tag;
    }

    /**
     * Load modes from a compound previously written by {@link #save()}. Unknown channels
     * and out-of-range mode/side values are ignored; disallowed modes are dropped so a
     * tightened channel definition never leaves an illegal state loaded.
     */
    public void load(CompoundTag tag) {
        for (String channelId : tag.getAllKeys()) {
            Map<RelativeSide, IOMode> perSide = modes.get(channelId);
            IOChannel channel = channelsById.get(channelId);
            if (perSide == null || channel == null) continue; // unknown channel — ignore

            byte[] sideModes = tag.getByteArray(channelId);
            RelativeSide[] sides = RelativeSide.values();
            IOMode[] allModes = IOMode.values();
            for (int i = 0; i < sideModes.length && i < sides.length; i++) {
                int modeOrdinal = sideModes[i];
                if (modeOrdinal < 0 || modeOrdinal >= allModes.length) continue;
                IOMode mode = allModes[modeOrdinal];
                if (channel.isModeAllowed(mode)) {
                    perSide.put(sides[i], mode);
                }
            }
        }
    }
}
