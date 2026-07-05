package com.researchcube.sideio;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Declares one configurable IO channel of a block entity — for example its item
 * inventory or a specific fluid tank. A block entity exposes a fixed list of channels;
 * each side of the block holds an {@link IOMode} per channel.
 *
 * @param id           stable identifier, persisted in NBT and sent over the network (e.g. {@code "items"}, {@code "fluid_input_1"})
 * @param translationKey translation key for the channel's tab/display label
 * @param type         whether this channel exposes items or fluids
 * @param allowedModes the set of modes a side may take for this channel (must include {@code defaultMode})
 * @param defaultMode  the mode applied to every side before the player customises it
 */
public record IOChannel(String id, String translationKey, Type type,
                        EnumSet<IOMode> allowedModes, IOMode defaultMode) {

    /** The kind of resource an {@link IOChannel} carries. */
    public enum Type {
        ITEM,
        FLUID
    }

    public IOChannel {
        if (!allowedModes.contains(defaultMode)) {
            throw new IllegalArgumentException("Default mode " + defaultMode + " not in allowed modes " + allowedModes + " for channel " + id);
        }
    }

    /** Whether the given mode is permitted for this channel. */
    public boolean isModeAllowed(IOMode mode) {
        return allowedModes.contains(mode);
    }

    /**
     * The allowed modes as an ordered list following the natural {@link IOMode} order
     * (NONE, INPUT, OUTPUT, BOTH). Used for forward/backward cycling in the UI.
     */
    public List<IOMode> orderedAllowedModes() {
        List<IOMode> ordered = new ArrayList<>();
        for (IOMode mode : IOMode.values()) {
            if (allowedModes.contains(mode)) {
                ordered.add(mode);
            }
        }
        return ordered;
    }
}
