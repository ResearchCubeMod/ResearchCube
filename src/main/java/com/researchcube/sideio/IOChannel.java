package com.researchcube.sideio;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Declares one configurable IO channel of a block entity, for example its item
 * inventory or a specific fluid tank. A block entity exposes a fixed list of channels;
 * each side of the block holds an {@link IOMode} per channel.
 *
 * <p>Channels may optionally belong to a <em>group</em>. Grouped channels are presented in
 * the side-config UI as a single tab whose per-side cell cycles through a composite state
 * derived from all member channels' modes (for example the Processing Station's three fluid
 * tanks collapse into one "Fluid" tab: None / Input 1 / Input 2 / Output). The underlying
 * per-channel {@link IOMode}s and server-side handlers are unaffected: grouping is purely a
 * presentation concern that the panel translates into ordinary per-channel mode changes.
 *
 * @param id                 stable identifier, persisted in NBT and sent over the network (e.g. {@code "items"}, {@code "fluid_input_1"})
 * @param translationKey     translation key for the channel's tab/display label
 * @param type               whether this channel exposes items or fluids
 * @param allowedModes       the set of modes a side may take for this channel (must include {@code defaultMode})
 * @param defaultMode        the mode applied to every side before the player customises it
 * @param groupId            optional group identifier; channels sharing a non-null id render as one tab (null = ungrouped)
 * @param groupTranslationKey translation key for the group's tab label; used only when {@code groupId} is non-null
 */
public record IOChannel(String id, String translationKey, Type type,
                        EnumSet<IOMode> allowedModes, IOMode defaultMode,
                        @Nullable String groupId, @Nullable String groupTranslationKey) {

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

    /**
     * Convenience constructor for an ungrouped channel (the common case). Equivalent to
     * passing {@code null} group id and group translation key.
     */
    public IOChannel(String id, String translationKey, Type type,
                     EnumSet<IOMode> allowedModes, IOMode defaultMode) {
        this(id, translationKey, type, allowedModes, defaultMode, null, null);
    }

    /** Whether this channel is part of a merged group (renders under a shared tab). */
    public boolean isGrouped() {
        return groupId != null;
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
