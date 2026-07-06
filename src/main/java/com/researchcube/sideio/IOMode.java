package com.researchcube.sideio;

import java.util.List;

/**
 * How a given channel behaves on a given side of a block.
 *
 * <ul>
 *   <li>{@link #NONE}: the side is closed for this channel; the capability reports nothing.</li>
 *   <li>{@link #INPUT}: external automation may insert only.</li>
 *   <li>{@link #OUTPUT}: external automation may extract only.</li>
 *   <li>{@link #BOTH}: external automation may insert and extract.</li>
 * </ul>
 */
public enum IOMode {
    NONE("none"),
    INPUT("input"),
    OUTPUT("output"),
    BOTH("both");

    private final String displayId;

    IOMode(String displayId) {
        this.displayId = displayId;
    }

    public String getDisplayId() {
        return displayId;
    }

    /** Translation key for this mode's display name. */
    public String getTranslationKey() {
        return "gui.researchcube.iomode." + displayId;
    }

    /** Whether external automation may insert through a side set to this mode. */
    public boolean canInsert() {
        return this == INPUT || this == BOTH;
    }

    /** Whether external automation may extract through a side set to this mode. */
    public boolean canExtract() {
        return this == OUTPUT || this == BOTH;
    }

    /**
     * Cycle forward to the next mode within the given ordered allowed subset.
     * If this mode is not part of {@code allowed}, the first allowed mode is returned.
     *
     * @param allowed ordered list of modes the channel permits (must be non-empty)
     */
    public IOMode next(List<IOMode> allowed) {
        return step(allowed, +1);
    }

    /**
     * Cycle backward to the previous mode within the given ordered allowed subset.
     * If this mode is not part of {@code allowed}, the last allowed mode is returned.
     *
     * @param allowed ordered list of modes the channel permits (must be non-empty)
     */
    public IOMode previous(List<IOMode> allowed) {
        return step(allowed, -1);
    }

    private IOMode step(List<IOMode> allowed, int direction) {
        if (allowed.isEmpty()) return this;
        int idx = allowed.indexOf(this);
        if (idx < 0) {
            // Current mode not allowed here: snap into the allowed set.
            return direction > 0 ? allowed.get(0) : allowed.get(allowed.size() - 1);
        }
        int size = allowed.size();
        int nextIdx = ((idx + direction) % size + size) % size;
        return allowed.get(nextIdx);
    }
}
