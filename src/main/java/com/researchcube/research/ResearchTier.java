package com.researchcube.research;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Defines the tier hierarchy for the research system.
 * Higher ordinal = higher tier. IRRECOVERABLE is decorative only (broken).
 */
public enum ResearchTier implements StringRepresentable {
    IRRECOVERABLE(0, 0x888888, "Irrecoverable"),  // gray, broken/decorative
    UNSTABLE     (1, 0xFFFFFF, "Unstable"),        // white
    BASIC        (2, 0x55FF55, "Basic"),            // green
    ADVANCED     (3, 0x5555FF, "Advanced"),         // blue
    PRECISE      (4, 0xFFAA00, "Precise"),          // gold
    FLAWLESS     (5, 0xAA00AA, "Flawless"),         // purple
    SELF_AWARE   (6, 0xFF5555, "Self-Aware");       // red

    private final int level;
    private final int color;
    private final String displayName;

    ResearchTier(int level, int color, String displayName) {
        this.level = level;
        this.color = color;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public int getColor() {
        return color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this tier is at least as high as the given tier.
     */
    public boolean isAtLeast(ResearchTier other) {
        return this.level >= other.level;
    }

    /**
     * Returns true if this tier is a functional (non-broken) tier.
     */
    public boolean isFunctional() {
        return this != IRRECOVERABLE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a tier from a string (case-insensitive).
     */
    public static ResearchTier fromString(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
