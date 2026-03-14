package com.researchcube.client.screen;

import com.researchcube.research.*;
import com.researchcube.research.prerequisite.NonePrerequisite;
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 232;
    private int panelX, panelY;

    // Scrolling
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 14;
    private int visibleRows;

    // Data
    private List<DisplayEntry> entries = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";

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
        visibleRows = (PANEL_HEIGHT - 90) / ROW_HEIGHT; // reserve header, search box, and footer areas

        buildEntryList();

        // Search box above the list
        searchBox = new EditBox(font, panelX + 8, panelY + 28, PANEL_WIDTH - 16, 12,
                Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setHint(Component.literal("Search...").withStyle(s -> s.withColor(0xFF666666)));
        searchBox.setResponder(query -> {
            searchFilter = query.toLowerCase();
            scrollOffset = 0;
            buildEntryList();
        });
        addRenderableWidget(searchBox);

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
            .bounds(panelX + PANEL_WIDTH - 54, panelY + PANEL_HEIGHT - 22, 48, 16)
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
                // Apply search filter
                if (!searchFilter.isEmpty()) {
                    String name = def.getDisplayName().toLowerCase();
                    String cat = def.getCategory() != null ? def.getCategory().toLowerCase() : "";
                    String tierName = tier.getDisplayName().toLowerCase();
                    if (!name.contains(searchFilter) && !cat.contains(searchFilter) && !tierName.contains(searchFilter)) {
                        continue;
                    }
                }
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
        // Semi-transparent dark overlay (50% opacity) — lets world show through without blur shader
        graphics.fill(0, 0, this.width, this.height, 0x80101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF111111);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF293047);
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, 0xFF1E2435);
        graphics.fill(panelX + 6, panelY + 28, panelX + PANEL_WIDTH - 6, panelY + PANEL_HEIGHT - 30, 0xFF242D46);

        // Title
        graphics.drawCenteredString(font, "Research Encyclopedia", panelX + PANEL_WIDTH / 2, panelY + 6, 0xFFFFAA);

        // Stats line
        long totalResearch = ResearchRegistry.getAll().stream().filter(d -> d.getTier().isFunctional()).count();
        long completedCount = completedResearch.size();
        String statsText = "Progress: " + completedCount + "/" + totalResearch;
        graphics.drawCenteredString(font, statsText, panelX + PANEL_WIDTH / 2, panelY + 18, 0x88AAFF);

        int progressBarX = panelX + 10;
        int progressBarY = panelY + PANEL_HEIGHT - 36;
        int progressBarW = PANEL_WIDTH - 70;
        int progressBarH = 8;
        float progressRatio = totalResearch <= 0 ? 0f : (float) completedCount / totalResearch;
        int progressFill = Math.max(0, Math.min(progressBarW, Math.round(progressBarW * progressRatio)));
        graphics.fill(progressBarX, progressBarY, progressBarX + progressBarW, progressBarY + progressBarH, 0xFF151A26);
        graphics.fill(progressBarX, progressBarY, progressBarX + progressFill, progressBarY + progressBarH, 0xFF3BA55D);
        graphics.fill(progressBarX + progressBarW, progressBarY, progressBarX + progressBarW + 1, progressBarY + progressBarH + 1, 0xFF68708C);
        graphics.fill(progressBarX, progressBarY + progressBarH, progressBarX + progressBarW + 1, progressBarY + progressBarH + 1, 0xFF68708C);

        // Research list
        int listY = panelY + 48;
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        graphics.drawString(font, "Status", panelX + 10, listY - 10, 0xFF8EA3D1, false);
        graphics.drawString(font, "Research", panelX + 48, listY - 10, 0xFF8EA3D1, false);
        graphics.drawString(font, "Time", panelX + PANEL_WIDTH - 100, listY - 10, 0xFF8EA3D1, false);
        graphics.drawString(font, "Category", panelX + PANEL_WIDTH - 54, listY - 10, 0xFF8EA3D1, false);

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) break;

            DisplayEntry entry = entries.get(idx);
            int rowY = listY + i * ROW_HEIGHT;

            if (entry.isHeader()) {
                // Tier header row
                graphics.fill(panelX + 8, rowY, panelX + PANEL_WIDTH - 8, rowY + ROW_HEIGHT - 1, 0xFF242E4F);
                graphics.drawCenteredString(font, entry.headerText(), panelX + PANEL_WIDTH / 2, rowY + 2,
                        entry.headerColor() | 0xFF000000);
            } else {
                // Research entry row
                ResearchDefinition def = entry.definition();
                boolean done = entry.completed();

                // Row background (subtle alternation)
                int bg = (idx % 2 == 0) ? 0xFF283252 : 0xFF2D375A;
                graphics.fill(panelX + 8, rowY, panelX + PANEL_WIDTH - 8, rowY + ROW_HEIGHT - 1, bg);

                // Completion icon
                String icon = done ? "\u2714 " : "\u2718 ";
                int iconColor = done ? 0xFF55FF55 : 0xFF555555;
                graphics.drawString(font, icon, panelX + 10, rowY + 2, iconColor, false);

                // Research name (tier-colored if completed, grey if not)
                int nameColor = done ? (def.getTier().getColor() | 0xFF000000) : 0xFF888888;
                String name = trimToWidth(def.getDisplayName(), 168);
                graphics.drawString(font, name, panelX + 30, rowY + 2, nameColor, false);

                // Category tag (right-aligned)
                if (def.getCategory() != null) {
                    String cat = "[" + def.getCategory() + "]";
                    int catWidth = font.width(cat);
                    graphics.drawString(font, cat, panelX + PANEL_WIDTH - 12 - catWidth, rowY + 2, 0xFF8E9457, false);
                }

                // Duration (right of name, before category)
                String duration = String.format("%.0fs", def.getDurationSeconds());
                int durWidth = font.width(duration);
                int durX = def.getCategory() != null
                        ? panelX + PANEL_WIDTH - 14 - font.width("[" + def.getCategory() + "]") - durWidth - 6
                        : panelX + PANEL_WIDTH - 12 - durWidth;
                graphics.drawString(font, duration, durX, rowY + 2, 0xFF93A0C2, false);
            }
        }

        // Scroll indicators (compact arrows at right edge of list)
        if (scrollOffset > 0) {
            graphics.drawString(font, "\u25B2", panelX + PANEL_WIDTH - 18, listY + 2, 0xFF888888, false);
        }
        if (scrollOffset + visibleRows < entries.size()) {
            graphics.drawString(font, "\u25BC", panelX + PANEL_WIDTH - 18,
                    listY + (visibleRows - 1) * ROW_HEIGHT + 2, 0xFF888888, false);
        }

        // Tooltip on hover
        renderEntryTooltip(graphics, mouseX, mouseY, listY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String value = text;
        while (value.length() > 2 && font.width(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
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

        // Recipe pool — resolve to output item names
        if (def.hasRecipePool()) {
            tooltip.add(Component.literal("Possible Rewards:").withStyle(s -> s.withColor(0x55FFFF)));
            for (ResourceLocation recipeRl : def.getRecipePool()) {
                String resolved = RecipeOutputResolver.formatOutput(recipeRl.toString());
                ItemStack output = RecipeOutputResolver.resolveOutput(recipeRl.toString());
                int rewardColor = !output.isEmpty() ? 0x88FF88 : 0xBBBBBB;
                tooltip.add(Component.literal("  \u2192 " + resolved)
                        .withStyle(s -> s.withColor(rewardColor)));
            }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && searchBox != null && searchBox.isFocused() && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
