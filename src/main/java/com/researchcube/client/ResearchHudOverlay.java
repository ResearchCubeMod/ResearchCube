package com.researchcube.client;

import com.researchcube.ResearchCubeMod;
import com.researchcube.registry.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Renders a compact research progress overlay in a configurable screen corner.
 * Shows: research name (tier-colored), mini progress bar, and estimated time remaining.
 * Only visible when the player has active research and the config option is enabled.
 */
public class ResearchHudOverlay implements LayeredDraw.Layer {

    private static final int PANEL_W = 140;
    private static final int PANEL_H = 28;
    private static final int MARGIN = 4;

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResearchCubeMod.rl("research_hud"), new ResearchHudOverlay());
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        if (!ModConfig.isShowResearchHUD()) return;
        if (!ClientResearchData.hasActiveResearch()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;

        Font font = mc.font;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        // Determine corner position
        int corner = ModConfig.getResearchHUDCorner();
        int x, y;
        switch (corner) {
            case 0 -> { x = MARGIN; y = MARGIN; }                                              // top-left
            case 2 -> { x = MARGIN; y = screenH - PANEL_H - MARGIN; }                          // bottom-left
            case 3 -> { x = screenW - PANEL_W - MARGIN; y = screenH - PANEL_H - MARGIN; }      // bottom-right
            default -> { x = screenW - PANEL_W - MARGIN; y = MARGIN; }                         // 1 = top-right (default)
        }

        // Panel background
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xCC1A1A2E);
        // Border
        g.fill(x, y, x + PANEL_W, y + 1, 0xFF3A3A5E);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF3A3A5E);
        g.fill(x, y, x + 1, y + PANEL_H, 0xFF3A3A5E);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF3A3A5E);

        String name = ClientResearchData.getActiveResearchName();
        int tierColor = ClientResearchData.getActiveTierColor() | 0xFF000000;

        // Research name (truncated to fit)
        String displayName = name;
        int maxNameW = PANEL_W - 8;
        if (font.width(displayName) > maxNameW) {
            while (displayName.length() > 2 && font.width(displayName + "...") > maxNameW) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName = displayName + "...";
        }
        g.drawString(font, displayName, x + 4, y + 3, tierColor, true);

        // Progress bar
        float progress = ClientResearchData.getActiveProgress();
        int barX = x + 4;
        int barY = y + 15;
        int barW = PANEL_W - 60;
        int barH = 8;
        int filled = (int) (barW * progress);

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF222233);
        if (filled > 0) {
            g.fill(barX, barY, barX + filled, barY + barH, 0xFF22CC55);
        }
        // Bar border
        g.fill(barX, barY, barX + barW, barY + 1, 0xFF444466);
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF111122);

        // ETA text
        int eta = ClientResearchData.getRemainingSeconds();
        String etaText;
        if (eta >= 3600) {
            etaText = (eta / 3600) + "h " + ((eta % 3600) / 60) + "m";
        } else if (eta >= 60) {
            etaText = (eta / 60) + "m " + (eta % 60) + "s";
        } else {
            etaText = eta + "s";
        }
        g.drawString(font, etaText, barX + barW + 4, barY + 1, 0xFF888888, true);
    }
}
