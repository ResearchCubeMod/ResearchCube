package com.researchcube.research;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Defines the tier hierarchy for the research system.
 * Higher ordinal = higher tier. IRRECOVERABLE is decorative only (broken).
 */
public enum ResearchTier implements StringRepresentable {
    IRRECOVERABLE(0, 0x888888, "Irrecoverable", 0),   // gray, broken/decorative
    UNSTABLE     (1, 0xFFFFFF, "Unstable",       2),   // white
    BASIC        (2, 0x55FF55, "Basic",          4),   // green
    ADVANCED     (3, 0x5555FF, "Advanced",       8),   // blue
    PRECISE      (4, 0xAA00AA, "Precise",       12),   // purple
    FLAWLESS     (5, 0xFFAA00, "Flawless",      16),   // gold
    SELF_AWARE   (6, 0xFF5555, "Self-Aware",    -1);   // red, unlimited

    private final int level;
    private final int color;
    private final String displayName;
    private final int maxRecipes; // -1 = unlimited

    ResearchTier(int level, int color, String displayName, int maxRecipes) {
        this.level = level;
        this.color = color;
        this.displayName = displayName;
        this.maxRecipes = maxRecipes;
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
     * Maximum number of recipes a drive of this tier can hold.
     * Returns -1 for unlimited (SELF_AWARE).
     */
    public int getMaxRecipes() {
        return maxRecipes;
    }

    /**
     * Whether this tier has a recipe capacity limit.
     */
    public boolean hasRecipeLimit() {
        return maxRecipes >= 0;
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
