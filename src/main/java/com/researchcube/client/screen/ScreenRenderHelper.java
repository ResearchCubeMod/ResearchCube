package com.researchcube.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared rendering utilities for bevelled panels, slot backgrounds, and mini gauges.
 * Used by screens, JEI categories, and EMI recipes to maintain consistent visual style.
 */
public final class ScreenRenderHelper {

    // ── Color Palette ──
    public static final int BG_OUTER = 0xFFC6C6C6;
    public static final int PANEL_BG = 0xFF4A4F60;
    public static final int PANEL_INNER = 0xFF2E3342;
    public static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    public static final int PANEL_BORDER_LIGHT = 0xFF7E87A6;
    public static final int SLOT_BORDER = 0xFF8B8B8B;
    public static final int SLOT_INNER = 0xFF373737;

    private ScreenRenderHelper() {}

    /**
     * Draw a dark recessed panel with a 1px bevel border.
     */
    public static void drawInsetPanel(GuiGraphics g, int px, int py, int pw, int ph) {
        // Dark fill
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL_INNER);
        // Top/left shadow (darker)
        g.fill(px, py, px + pw, py + 1, PANEL_BORDER_DARK);
        g.fill(px, py, px + 1, py + ph, PANEL_BORDER_DARK);
        // Bottom/right highlight (lighter)
        g.fill(px + pw - 1, py, px + pw, py + ph, PANEL_BORDER_LIGHT);
        g.fill(px, py + ph - 1, px + pw, py + ph, PANEL_BORDER_LIGHT);
    }

    /**
     * Draw an 18×18 slot background with inset bevel.
     */
    public static void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_BORDER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
        // Top-left darker edge
        g.fill(x, y, x + 18, y + 1, PANEL_BORDER_DARK);
        g.fill(x, y, x + 1, y + 18, PANEL_BORDER_DARK);
        // Bottom-right lighter edge
        g.fill(x + 17, y, x + 18, y + 18, PANEL_BORDER_LIGHT);
        g.fill(x, y + 17, x + 18, y + 18, PANEL_BORDER_LIGHT);
    }

    /**
     * Draw a mini fluid gauge bar (vertical, fills bottom-to-top).
     *
     * @param g         graphics context
     * @param x         left edge
     * @param y         top edge
     * @param w         gauge width
     * @param h         gauge height
     * @param amount    current amount
     * @param maxAmount capacity
     * @param color     ARGB fill color
     */
    public static void drawFluidMiniGauge(GuiGraphics g, int x, int y, int w, int h, int amount, int maxAmount, int color) {
        // Background
        g.fill(x, y, x + w, y + h, 0xFF222222);
        // Border
        g.fill(x, y, x + w, y + 1, PANEL_BORDER_DARK);
        g.fill(x, y, x + 1, y + h, PANEL_BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER_LIGHT);

        if (amount > 0 && maxAmount > 0) {
            int fillH = (int) ((float) amount / maxAmount * (h - 2));
            fillH = Math.min(fillH, h - 2);
            g.fill(x + 1, y + h - 1 - fillH, x + w - 1, y + h - 1, color);
        }
    }

    /**
     * Draw a processing arrow (right-pointing) at the given position.
     */
    public static void drawArrow(GuiGraphics g, int x, int y, int color) {
        // Simple right-pointing arrow: horizontal bar + chevron
        g.fill(x, y + 3, x + 16, y + 5, color);
        g.fill(x + 12, y + 1, x + 14, y + 3, color);
        g.fill(x + 14, y + 3, x + 16, y + 5, color);
        g.fill(x + 12, y + 5, x + 14, y + 7, color);
    }
}
