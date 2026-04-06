package com.researchcube.client.screen;

import com.researchcube.network.EncodeChipPacket;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Client-side screen for selecting which completed research to encode onto a Research Chip.
 * Opened via {@link com.researchcube.network.OpenChipEncoderPacket}.
 */
public class ChipEncoderScreen extends Screen {

    private final List<ResearchEntry> entries = new ArrayList<>();
    private final boolean mainHand;
    private int scrollOffset = 0;
    private int selectedIndex = -1;

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 16;
    private static final int VISIBLE_ROWS = 8;

    private int panelX, panelY;
    private Button encodeButton;

    private record ResearchEntry(ResourceLocation id, String displayName, String tierName, int tierColor) {}

    public ChipEncoderScreen(Set<ResourceLocation> completedResearch, boolean mainHand) {
        super(Component.literal("Encode Research Chip"));
        this.mainHand = mainHand;

        // Build sorted list of completed research that still exists in the registry
        for (ResourceLocation rl : completedResearch) {
            ResearchDefinition def = ResearchRegistry.get(rl.toString());
            if (def != null) {
                entries.add(new ResearchEntry(
                        rl,
                        def.getDisplayName(),
                        def.getTier().getDisplayName(),
                        def.getTier().getColor() | 0xFF000000
                ));
            }
        }
        entries.sort(Comparator.comparing(ResearchEntry::displayName, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        encodeButton = addRenderableWidget(Button.builder(Component.literal("Encode"), btn -> {
            if (selectedIndex >= 0 && selectedIndex < entries.size()) {
                ResearchEntry entry = entries.get(selectedIndex);
                PacketDistributor.sendToServer(new EncodeChipPacket(entry.id, mainHand));
                onClose();
            }
        }).bounds(panelX + PANEL_W / 2 - 40, panelY + PANEL_H - 24, 80, 18).build());
        encodeButton.active = false;

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(panelX + PANEL_W / 2 + 50, panelY + PANEL_H - 24, 60, 18)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Panel background
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF293047);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, 0xFF1E2435);
        // Border
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF3A4466);
        g.fill(panelX, panelY, panelX + 1, panelY + PANEL_H, 0xFF3A4466);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF3A4466);
        g.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF3A4466);

        // Title
        g.drawCenteredString(font, "Select Research to Encode", panelX + PANEL_W / 2, panelY + 6, 0xFFFFAA);

        // Count
        String countStr = entries.size() + " completed research available";
        g.drawCenteredString(font, countStr, panelX + PANEL_W / 2, panelY + 18, 0xFFAAAAAA);

        // List area
        int listX = panelX + 8;
        int listY = panelY + 32;
        int listW = PANEL_W - 16;

        ScreenRenderHelper.drawInsetPanel(g, listX, listY, listW, VISIBLE_ROWS * ROW_H + 4);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) break;

            ResearchEntry entry = entries.get(idx);
            int rowY = listY + 2 + i * ROW_H;

            // Selection highlight
            if (idx == selectedIndex) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, 0x40FFFFFF);
            } else if (i % 2 == 1) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, 0x10FFFFFF);
            }

            // Research name
            g.drawString(font, entry.displayName, listX + 6, rowY + 4, entry.tierColor, false);

            // Tier tag (right-aligned)
            String tierTag = "[" + entry.tierName + "]";
            int tagW = font.width(tierTag);
            g.drawString(font, tierTag, listX + listW - 6 - tagW, rowY + 4, 0xFF888888, false);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            g.drawCenteredString(font, "\u25B2", listX + listW / 2, listY - 8, 0xFFAAAAAA);
        }
        if (scrollOffset + VISIBLE_ROWS < entries.size()) {
            g.drawCenteredString(font, "\u25BC", listX + listW / 2, listY + VISIBLE_ROWS * ROW_H + 6, 0xFFAAAAAA);
        }

        // Tooltip on hover
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) break;
            int rowY = listY + 2 + i * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                ResearchEntry entry = entries.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(entry.displayName).withStyle(s -> s.withColor(entry.tierColor)));
                tooltip.add(Component.literal("Tier: " + entry.tierName).withStyle(s -> s.withColor(0x888888)));
                tooltip.add(Component.literal("ID: " + entry.id).withStyle(s -> s.withColor(0x666666)));
                g.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = panelX + 8;
        int listY = panelY + 32;
        int listW = PANEL_W - 16;

        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY + 2 && mouseY < listY + 2 + VISIBLE_ROWS * ROW_H) {
            int row = (int) (mouseY - listY - 2) / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < entries.size()) {
                selectedIndex = idx;
                encodeButton.active = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (scrollY < 0 && scrollOffset + VISIBLE_ROWS < entries.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
