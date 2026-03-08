package com.researchcube.client.screen;

import com.researchcube.research.*;
import com.researchcube.research.prerequisite.NonePrerequisite;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Read-only screen showing all research definitions, grouped by tier,
 * with completion status based on the player's progress.
 *
 * Opened by the Research Book item via server→client packet.
 */
public class ResearchBookScreen extends Screen {

    private final Set<String> completedResearch;

    // Layout
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 200;
    private int panelX, panelY;

    // Scrolling
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 14;
    private int visibleRows;

    // Data
    private List<DisplayEntry> entries = new ArrayList<>();

    /** A row in the display list — either a tier header or a research entry. */
    private record DisplayEntry(boolean isHeader, String headerText, int headerColor,
                                ResearchDefinition definition, boolean completed) {
        static DisplayEntry header(String text, int color) {
            return new DisplayEntry(true, text, color, null, false);
        }
        static DisplayEntry research(ResearchDefinition def, boolean completed) {
            return new DisplayEntry(false, null, 0, def, completed);
        }
    }

    public ResearchBookScreen(Set<String> completedResearch) {
        super(Component.literal("Research Encyclopedia"));
        this.completedResearch = completedResearch;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        visibleRows = (PANEL_HEIGHT - 30) / ROW_HEIGHT; // reserve top for title

        buildEntryList();

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(panelX + PANEL_WIDTH - 45, panelY + PANEL_HEIGHT - 18, 40, 14)
                .build());
    }

    private void buildEntryList() {
        entries.clear();

        // Group all research by tier, ordered by tier ordinal
        Map<ResearchTier, List<ResearchDefinition>> byTier = new LinkedHashMap<>();
        for (ResearchTier tier : ResearchTier.values()) {
            if (!tier.isFunctional()) continue; // skip IRRECOVERABLE
            byTier.put(tier, new ArrayList<>());
        }

        for (ResearchDefinition def : ResearchRegistry.getAll()) {
            ResearchTier tier = def.getTier();
            if (byTier.containsKey(tier)) {
                byTier.get(tier).add(def);
            }
        }

        for (Map.Entry<ResearchTier, List<ResearchDefinition>> tierEntry : byTier.entrySet()) {
            ResearchTier tier = tierEntry.getKey();
            List<ResearchDefinition> defs = tierEntry.getValue();
            if (defs.isEmpty()) continue;

            // Tier header
            entries.add(DisplayEntry.header(
                    "\u2550\u2550 " + tier.getDisplayName() + " Tier \u2550\u2550",
                    tier.getColor()));

            // Sort by display name within tier
            defs.sort(Comparator.comparing(ResearchDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));

            for (ResearchDefinition def : defs) {
                boolean done = completedResearch.contains(def.getIdString());
                entries.add(DisplayEntry.research(def, done));
            }
        }
    }

    /**
     * Override renderBackground to prevent Minecraft 1.21's automatic blur-shader overlay.
     * The game calls this before render(), so we must intercept it here.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Flat opaque overlay — no super call, so no blur shader is applied
        graphics.fill(0, 0, this.width, this.height, 0xFF101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF111111);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF1A1A2E);

        // Title
        graphics.drawCenteredString(font, "Research Encyclopedia", panelX + PANEL_WIDTH / 2, panelY + 4, 0xFFFFAA);

        // Stats line
        long totalResearch = ResearchRegistry.getAll().stream().filter(d -> d.getTier().isFunctional()).count();
        long completedCount = completedResearch.size();
        String statsText = "Progress: " + completedCount + "/" + totalResearch;
        graphics.drawCenteredString(font, statsText, panelX + PANEL_WIDTH / 2, panelY + 16, 0x88AAFF);

        // Research list
        int listY = panelY + 30;
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) break;

            DisplayEntry entry = entries.get(idx);
            int rowY = listY + i * ROW_HEIGHT;

            if (entry.isHeader()) {
                // Tier header row
                graphics.fill(panelX + 4, rowY, panelX + PANEL_WIDTH - 4, rowY + ROW_HEIGHT - 1, 0xFF222244);
                graphics.drawCenteredString(font, entry.headerText(), panelX + PANEL_WIDTH / 2, rowY + 2,
                        entry.headerColor() | 0xFF000000);
            } else {
                // Research entry row
                ResearchDefinition def = entry.definition();
                boolean done = entry.completed();

                // Row background (subtle alternation)
                int bg = (idx % 2 == 0) ? 0xFF1E1E30 : 0xFF222240;
                graphics.fill(panelX + 4, rowY, panelX + PANEL_WIDTH - 4, rowY + ROW_HEIGHT - 1, bg);

                // Completion icon
                String icon = done ? "\u2714 " : "\u2718 ";
                int iconColor = done ? 0xFF55FF55 : 0xFF555555;
                graphics.drawString(font, icon, panelX + 8, rowY + 2, iconColor, false);

                // Research name (tier-colored if completed, grey if not)
                int nameColor = done ? (def.getTier().getColor() | 0xFF000000) : 0xFF888888;
                String name = def.getDisplayName();
                graphics.drawString(font, name, panelX + 22, rowY + 2, nameColor, false);

                // Category tag (right-aligned)
                if (def.getCategory() != null) {
                    String cat = "[" + def.getCategory() + "]";
                    int catWidth = font.width(cat);
                    graphics.drawString(font, cat, panelX + PANEL_WIDTH - 8 - catWidth, rowY + 2, 0xFF666600, false);
                }

                // Duration (right of name, before category)
                String duration = String.format("%.0fs", def.getDurationSeconds());
                int durWidth = font.width(duration);
                int durX = def.getCategory() != null
                        ? panelX + PANEL_WIDTH - 10 - font.width("[" + def.getCategory() + "]") - durWidth - 6
                        : panelX + PANEL_WIDTH - 8 - durWidth;
                graphics.drawString(font, duration, durX, rowY + 2, 0xFF555555, false);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "\u25B2 Scroll Up", panelX + PANEL_WIDTH / 2, listY - 10, 0xFF888888);
        }
        if (scrollOffset + visibleRows < entries.size()) {
            graphics.drawCenteredString(font, "\u25BC Scroll Down",
                    panelX + PANEL_WIDTH / 2, listY + visibleRows * ROW_HEIGHT + 2, 0xFF888888);
        }

        // Tooltip on hover
        renderEntryTooltip(graphics, mouseX, mouseY, listY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntryTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listY) {
        if (mouseX < panelX + 4 || mouseX >= panelX + PANEL_WIDTH - 4) return;
        if (mouseY < listY || mouseY >= listY + visibleRows * ROW_HEIGHT) return;

        int row = (mouseY - listY) / ROW_HEIGHT;
        int idx = scrollOffset + row;
        if (idx < 0 || idx >= entries.size()) return;

        DisplayEntry entry = entries.get(idx);
        if (entry.isHeader()) return;

        ResearchDefinition def = entry.definition();
        List<Component> tooltip = new ArrayList<>();

        // Name
        tooltip.add(Component.literal(def.getDisplayName())
                .withStyle(s -> s.withColor(def.getTier().getColor())));

        // Description
        if (def.getDescription() != null) {
            tooltip.add(Component.literal(def.getDescription())
                    .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
        }

        // Tier + Duration
        tooltip.add(Component.literal("Tier: " + def.getTier().getDisplayName()
                + "  |  Duration: " + String.format("%.0fs", def.getDurationSeconds()))
                .withStyle(s -> s.withColor(0x888888)));

        // Category
        if (def.getCategory() != null) {
            tooltip.add(Component.literal("Category: " + def.getCategory())
                    .withStyle(s -> s.withColor(0xCCAA00)));
        }

        // Item costs
        if (!def.getItemCosts().isEmpty()) {
            tooltip.add(Component.literal("Costs:").withStyle(s -> s.withColor(0xCCCC00)));
            for (ItemCost cost : def.getItemCosts()) {
                tooltip.add(Component.literal("  \u2022 " + cost.getItem().getDescription().getString() + " x" + cost.count())
                        .withStyle(s -> s.withColor(0xBBBBBB)));
            }
        }

        // Prerequisites
        if (!(def.getPrerequisites() instanceof NonePrerequisite)) {
            boolean met = def.getPrerequisites().isSatisfied(completedResearch);
            String prereqIcon = met ? "\u2714" : "\u2718";
            int prereqColor = met ? 0x55FF55 : 0xFF5555;
            tooltip.add(Component.literal("Prerequisites: " + prereqIcon + " " + def.getPrerequisites().describe())
                    .withStyle(s -> s.withColor(prereqColor)));
        }

        // Recipe pool
        if (def.hasRecipePool()) {
            tooltip.add(Component.literal("Recipes: " + def.getRecipePool().size() + " possible")
                    .withStyle(s -> s.withColor(0x55FFFF)));
        }

        // Completion status
        boolean done = entry.completed();
        tooltip.add(Component.literal(done ? "\u2714 Completed" : "\u2718 Not Completed")
                .withStyle(s -> s.withColor(done ? 0x55FF55 : 0xFF5555)));

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (scrollY < 0 && scrollOffset < maxScroll) {
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
