package com.researchcube.sideio;

import net.minecraft.core.Direction;

/**
 * The six faces of a block expressed relative to the block's horizontal facing.
 *
 * <p>{@link #FRONT} always points in the block's facing direction. The remaining
 * horizontal sides ({@link #BACK}, {@link #LEFT}, {@link #RIGHT}) rotate with the
 * facing, while {@link #TOP} and {@link #BOTTOM} are always absolute up/down.
 *
 * <p>This lets a side-IO configuration follow the block when it is rotated: a side
 * the player configured as "left" stays visually on the left regardless of which
 * absolute direction the block ends up facing.
 */
public enum RelativeSide {
    FRONT("front"),
    BACK("back"),
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom");

    private final String displayId;

    RelativeSide(String displayId) {
        this.displayId = displayId;
    }

    /** Lowercase identifier used for translation keys (front/back/left/right/top/bottom). */
    public String getDisplayId() {
        return displayId;
    }

    /** Translation key for this side's display name. */
    public String getTranslationKey() {
        return "gui.researchcube.side." + displayId;
    }

    /**
     * Resolve the absolute {@link Direction} this relative side points to for a block
     * with the given horizontal facing.
     *
     * @param facing the block's horizontal facing (FRONT direction); must be a horizontal direction
     */
    public Direction toAbsolute(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();       // left when looking at the front face
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /**
     * Determine which relative side the given absolute {@link Direction} corresponds to
     * for a block with the given horizontal facing. Inverse of {@link #toAbsolute(Direction)}.
     *
     * @param facing   the block's horizontal facing (FRONT direction)
     * @param absolute the absolute world direction being queried
     */
    public static RelativeSide fromAbsolute(Direction facing, Direction absolute) {
        if (absolute == Direction.UP) return TOP;
        if (absolute == Direction.DOWN) return BOTTOM;
        if (absolute == facing) return FRONT;
        if (absolute == facing.getOpposite()) return BACK;
        if (absolute == facing.getClockWise()) return LEFT;
        return RIGHT;
    }
}
