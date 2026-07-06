package com.researchcube.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

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
    public static final int LIST_BG = 0xFF252A3E;
    public static final int GAUGE_BG = 0xFF222222;

    private ScreenRenderHelper() {}

    /**
     * Fill a box and draw a 1px bevel: dark top/left, light bottom/right.
     * Replaces the hand-rolled four-fill border pattern used throughout the screens.
     */
    public static void drawBevelBox(GuiGraphics g, int x, int y, int w, int h, int fillColor) {
        g.fill(x, y, x + w, y + h, fillColor);
        g.fill(x, y, x + w, y + 1, PANEL_BORDER_DARK);
        g.fill(x, y, x + 1, y + h, PANEL_BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER_LIGHT);
    }

    /**
     * Draw the full-size vertical fluid gauge (fills bottom-to-top), with the mod's
     * dark-outer / light-lower bevel. Pass {@code color == 0} (or {@code amount <= 0})
     * to render an empty gauge.
     */
    public static void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh,
                                      int amount, int capacity, int color) {
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + gw, gy + gh, GAUGE_BG);

        if (amount > 0 && color != 0 && capacity > 0) {
            int fillHeight = Math.min(gh, (int) ((float) gh * amount / capacity));
            int fillY = gy + gh - fillHeight;
            g.fill(gx, fillY, gx + gw, gy + gh, color);
            if (fillHeight > 2) {
                int shine = (color & 0x00FFFFFF) | 0x44000000;
                g.fill(gx, fillY, gx + gw, fillY + 1, shine);
            }
        }

        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    /**
     * Draw the full-size vertical fluid gauge using the fluid's real still texture (tinted),
     * tiled bottom-to-top to the fill height. Falls back to {@code fallbackColor} when the
     * sprite cannot be resolved (or the stack is empty). Keeps the same dark-outer / light-lower
     * bevel frame as the flat-color gauge so the two stay visually consistent.
     *
     * @param stack         tank contents (type + amount); empty renders an empty gauge
     * @param capacity      tank capacity in mB
     * @param fallbackColor flat ARGB color used only if the still sprite is missing
     */
    public static void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh,
                                      FluidStack stack, int capacity, int fallbackColor) {
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + gw, gy + gh, GAUGE_BG);

        int amount = stack.getAmount();
        if (amount > 0 && capacity > 0) {
            int fillHeight = Math.min(gh, Math.max(1, (int) ((float) gh * amount / capacity)));
            int fillY = gy + gh - fillHeight;

            TextureAtlasSprite sprite = stillSprite(stack);
            if (sprite != null) {
                IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
                int tint = ext.getTintColor(stack);
                float a = ((tint >> 24) & 0xFF) / 255f;
                if (a <= 0f) a = 1f; // some fluids report a 0 alpha tint; treat as opaque
                float r = ((tint >> 16) & 0xFF) / 255f;
                float gcol = ((tint >> 8) & 0xFF) / 255f;
                float b = (tint & 0xFF) / 255f;
                fillTiledSprite(g, sprite, gx, fillY, gw, fillHeight, r, gcol, b, a);
            } else if (fallbackColor != 0) {
                g.fill(gx, fillY, gx + gw, gy + gh, fallbackColor);
            }

            if (fillHeight > 2) {
                int shineBase = fallbackColor != 0 ? fallbackColor : 0xFFFFFFFF;
                int shine = (shineBase & 0x00FFFFFF) | 0x44000000;
                g.fill(gx, fillY, gx + gw, fillY + 1, shine);
            }
        }

        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    /** Resolve a fluid stack's still texture to an atlas sprite, or null if unavailable. */
    private static TextureAtlasSprite stillSprite(FluidStack stack) {
        if (stack.isEmpty()) return null;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation still = ext.getStillTexture(stack);
        if (still == null) return null;
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
        if (sprite == null) return null;
        // apply() returns the missing-texture sprite when the name is unknown; treat that as no sprite.
        ResourceLocation missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        return missing.equals(sprite.contents().name()) ? null : sprite;
    }

    /**
     * Fill a rect with a 16px fluid sprite tiled vertically (bottom-up). The topmost tile is
     * clipped by sub-sampling the sprite's V range so partial fills don't overflow the rect.
     */
    private static void fillTiledSprite(GuiGraphics g, TextureAtlasSprite sprite,
                                        int x, int y, int w, int h,
                                        float r, float gcol, float b, float a) {
        final int tile = 16;
        int drawn = 0;
        while (drawn < h) {
            int sliceH = Math.min(tile, h - drawn);
            // Draw from the bottom upward so partial tiles clip at the top of the fluid column.
            int sliceY = y + h - drawn - sliceH;
            if (sliceH == tile) {
                g.blit(x, sliceY, 0, w, sliceH, sprite, r, gcol, b, a);
            } else {
                // Partial tile: sub-sample the sprite's V range to the visible slice height.
                float v0 = sprite.getV0();
                float v1 = sprite.getV1();
                float vTop = v0 + (v1 - v0) * (tile - sliceH) / (float) tile;
                blitSubSprite(g, sprite, x, sliceY, w, sliceH, sprite.getU0(), sprite.getU1(), vTop, v1,
                        r, gcol, b, a);
            }
            drawn += sliceH;
        }
    }

    /**
     * Blit a sprite with explicit UVs and a color tint (for clipped partial fluid tiles).
     * Mirrors vanilla {@code GuiGraphics.innerBlit}; the color-tinted sprite blit only takes a
     * whole sprite, so we go one level lower to sub-sample the V range.
     */
    private static void blitSubSprite(GuiGraphics g, TextureAtlasSprite sprite,
                                      int x, int y, int w, int h,
                                      float u0, float u1, float v0, float v1,
                                      float r, float gcol, float b, float a) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        org.joml.Matrix4f matrix = g.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(r, gcol, b, a);
        buffer.addVertex(matrix, x, y + h, 0).setUv(u0, v1).setColor(r, gcol, b, a);
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(u1, v1).setColor(r, gcol, b, a);
        buffer.addVertex(matrix, x + w, y, 0).setUv(u1, v0).setColor(r, gcol, b, a);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

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
     * Draw an 18×18 slot background with inset bevel, using the shared light palette
     * (light border {@link #SLOT_BORDER}, inner {@link #SLOT_INNER}).
     */
    public static void drawSlotBg(GuiGraphics g, int x, int y) {
        drawSlotBg(g, x, y, SLOT_BORDER, SLOT_INNER, PANEL_BORDER_DARK, PANEL_BORDER_LIGHT);
    }

    /**
     * Draw an 18×18 slot background with an explicit bevel palette. Lets a screen match a
     * texture's baked slot frames instead of the default light palette above. Layout is
     * identical to {@link #drawSlotBg(GuiGraphics, int, int)}: a 1px light border, an inner
     * fill, then a dark top/left edge and a light bottom/right edge on top.
     *
     * @param border   the 1px outer border fill
     * @param inner    the interior fill (16×16)
     * @param bevelDark  the top/left bevel edge
     * @param bevelLight the bottom/right bevel edge
     */
    public static void drawSlotBg(GuiGraphics g, int x, int y,
                                  int border, int inner, int bevelDark, int bevelLight) {
        g.fill(x, y, x + 18, y + 18, border);
        g.fill(x + 1, y + 1, x + 17, y + 17, inner);
        // Top-left darker edge
        g.fill(x, y, x + 18, y + 1, bevelDark);
        g.fill(x, y, x + 1, y + 18, bevelDark);
        // Bottom-right lighter edge
        g.fill(x + 17, y, x + 18, y + 18, bevelLight);
        g.fill(x, y + 17, x + 18, y + 18, bevelLight);
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
