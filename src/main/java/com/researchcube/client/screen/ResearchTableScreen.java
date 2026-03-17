package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.item.CubeItem;
import com.researchcube.menu.ResearchTableMenu;
import com.researchcube.network.CancelResearchPacket;
import com.researchcube.network.StartResearchPacket;
import com.researchcube.network.WipeTankPacket;
import com.researchcube.registry.ModFluids;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ItemCost;
import com.researchcube.research.FluidCost;
import com.researchcube.research.prerequisite.NonePrerequisite;
import com.researchcube.util.IdeaChipMatcher;
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client-side screen for the Research Table.
 *
 * Compact layout (340x250):
 *   Left column (~100px): Drive, Cube, 3x2 cost grid, buckets, idea chip, fluid gauge,
 *                          progress bar, Start/Stop/Wipe buttons
 *   Right panel (~228px): Search bar + Tree button, scrollable research list (6 rows),
 *                          detail pane (name, flavor text, tier/duration/costs)
 *   Bottom: Player inventory + hotbar (centered)
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {

    /** A display row in the research list: either a category header or a research entry. */
    private record ListRow(boolean isHeader, String headerText, ResearchDefinition definition) {
        static ListRow header(String text) { return new ListRow(true, text, null); }
        static ListRow entry(ResearchDefinition def) { return new ListRow(false, null, def); }
    }

    // ── Layout constants ──

    // Left machine column
    private static final int LEFT_COL_X = 56;
    private static final int LEFT_COL_Y = 66;
    private static final int LEFT_COL_W = 182;
    private static final int LEFT_COL_H = 104;

    // Research list (right panel)
    private static final int LIST_X = 248;
    private static final int LIST_Y = 71;
    private static final int LIST_W = 400;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 5;

    // Detail pane (below research list)
    private static final int DETAIL_X = 248;
    private static final int DETAIL_Y = 162;
    private static final int DETAIL_W = 400;
    private static final int DETAIL_H = 13;

    // Progress bar (in left column)
    private static final int PROGRESS_X = 113;
    private static final int PROGRESS_Y = 113;
    private static final int PROGRESS_W = 88;
    private static final int PROGRESS_H = 13;

    // Fluid gauge (vertical bar on right side)
    private static final int GAUGE_X = 203;
    private static final int GAUGE_Y = 79;
    private static final int GAUGE_W = 18;
    private static final int GAUGE_H = 83;

    // Right panel region
    private static final int RIGHT_PANEL_X = 243;
    private static final int RIGHT_PANEL_Y = 66;
    private static final int RIGHT_PANEL_W = 411;
    private static final int RIGHT_PANEL_H = 114;

    // Texture
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchCubeMod.MOD_ID, "textures/gui/research_table.png");
    private static final int TEX_W = 699;
    private static final int TEX_H = 337;

    // Colors (retained for dynamic elements)
    private static final int PANEL_BORDER_LIGHT = 0xFF7E87A6;
    private static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    private static final int LIST_BG = 0xFF252A3E;

    // Research list state
    private List<ResearchDefinition> availableResearch = new ArrayList<>();
    private List<ListRow> displayRows = new ArrayList<>();
    private int selectedIndex = -1;
    private ResourceLocation selectedId = null;
    private int scrollOffset = 0;

    private Button startButton;
    private Button cancelButton;
    private Button wipeButton;
    private Button treeViewButton;
    private EditBox searchBox;
    private String searchFilter = "";

    public ResearchTableScreen(ResearchTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 699;
        this.imageHeight = 337;
        this.inventoryLabelX = 245;
        this.inventoryLabelY = 180;
    }

    @Override
    protected void init() {
        super.init();

        // Start button - SMALLER
        startButton = Button.builder(Component.literal("Start"), btn -> onStartResearch())
                .bounds(leftPos + 66, topPos + 151, 38, 16)
                .build();
        addRenderableWidget(startButton);

        // Cancel/Stop button - SMALLER
        cancelButton = Button.builder(Component.literal("Stop"), btn -> onCancelResearch())
                .bounds(leftPos + 108, topPos + 151, 38, 16)
                .build();
        addRenderableWidget(cancelButton);

        // Wipe tank button - SMALLER
        wipeButton = Button.builder(Component.literal("Wipe"), btn -> onWipeTank())
                .bounds(leftPos + 150, topPos + 151, 38, 16)
                .build();
        addRenderableWidget(wipeButton);

        // Tree view button (right of search box)
        treeViewButton = Button.builder(Component.literal("Tree"), btn -> onOpenTreeView())
                .bounds(leftPos + 599, topPos + 71, 49, 18)
                .build();
        addRenderableWidget(treeViewButton);

        // Search box above the research list
        searchBox = new EditBox(font, leftPos + LIST_X, topPos + 71, 346, 16,
                Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setHint(Component.literal("Search...").withStyle(s -> s.withColor(0xFF666666)));
        searchBox.setResponder(query -> {
            searchFilter = query.toLowerCase();
            scrollOffset = 0;
            refreshResearchList();
        });
        addRenderableWidget(searchBox);

        refreshResearchList();
    }

    // ══════════════════════════════════════════════════════════════
    //  Research list management
    // ══════════════════════════════════════════════════════════════

    private void refreshResearchList() {
        availableResearch.clear();
        displayRows.clear();

        ResearchTableBlockEntity be = menu.getBlockEntity();
        ItemStack cubeStack = be.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_CUBE);

        List<ResearchDefinition> raw;
        if (cubeStack.getItem() instanceof CubeItem cube) {
            raw = new ArrayList<>(ResearchRegistry.getUpToTier(cube.getTier()));
        } else {
            raw = new ArrayList<>(ResearchRegistry.getAll());
        }

        // Apply search filter
        if (!searchFilter.isEmpty()) {
            raw.removeIf(def -> {
                String name = def.getDisplayName().toLowerCase();
                String cat = def.getCategory() != null ? def.getCategory().toLowerCase() : "";
                String tier = def.getTier().getDisplayName().toLowerCase();
                return !name.contains(searchFilter) && !cat.contains(searchFilter) && !tier.contains(searchFilter);
            });
        }

        // Group by category
        Map<String, List<ResearchDefinition>> grouped = new LinkedHashMap<>();
        List<ResearchDefinition> uncategorized = new ArrayList<>();

        for (ResearchDefinition def : raw) {
            String cat = def.getCategory();
            if (cat != null && !cat.isEmpty()) {
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(def);
            } else {
                uncategorized.add(def);
            }
        }

        List<String> sortedKeys = new ArrayList<>(grouped.keySet());
        sortedKeys.sort(String.CASE_INSENSITIVE_ORDER);

        for (String cat : sortedKeys) {
            displayRows.add(ListRow.header(cat));
            for (ResearchDefinition def : grouped.get(cat)) {
                availableResearch.add(def);
                displayRows.add(ListRow.entry(def));
            }
        }

        if (!uncategorized.isEmpty()) {
            if (!grouped.isEmpty()) {
                displayRows.add(ListRow.header("other"));
            }
            for (ResearchDefinition def : uncategorized) {
                availableResearch.add(def);
                displayRows.add(ListRow.entry(def));
            }
        }

        // Restore selection by ID
        selectedIndex = -1;
        if (selectedId != null) {
            for (int i = 0; i < availableResearch.size(); i++) {
                if (availableResearch.get(i).getId().equals(selectedId)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════

    private void onStartResearch() {
        if (selectedIndex < 0 || selectedIndex >= availableResearch.size()) return;
        if (menu.isResearching()) return;

        ResearchDefinition def = availableResearch.get(selectedIndex);
        if (!isPrerequisiteMet(def)) return;
        if (!isIdeaChipSatisfied(def)) return;

        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new StartResearchPacket(be.getBlockPos(), def.getId().toString()));
    }

    private void onCancelResearch() {
        if (!menu.isResearching()) return;
        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new CancelResearchPacket(be.getBlockPos()));
    }

    private void onWipeTank() {
        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new WipeTankPacket(be.getBlockPos()));
    }

    private void onOpenTreeView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new ResearchTreeScreen(menu, mc.player.getInventory(), this.title));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshResearchList();

        boolean canStart = !menu.isResearching() && selectedIndex >= 0 && selectedIndex < availableResearch.size()
                && isPrerequisiteMet(availableResearch.get(selectedIndex))
                && isIdeaChipSatisfied(availableResearch.get(selectedIndex));
        startButton.active = canStart;
        cancelButton.active = menu.isResearching();
        wipeButton.active = menu.getFluidAmount() > 0;
        treeViewButton.active = true;
    }

    private boolean isPrerequisiteMet(ResearchDefinition def) {
        Set<String> completed = menu.getCompletedResearch();
        return def.getPrerequisites().isSatisfied(completed);
    }

    private boolean isIdeaChipSatisfied(ResearchDefinition def) {
        if (def.getIdeaChip().isEmpty()) return true;
        ItemStack required = def.getIdeaChip().get();
        ItemStack candidate = menu.getBlockEntity().getInventory()
                .getStackInSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP);
        return IdeaChipMatcher.matches(required, candidate);
    }

    private void renderIdeaChipOverlay(GuiGraphics g, int sx, int sy) {
        ResearchDefinition selected = getSelectedDefinition();
        if (selected == null || selected.getIdeaChip().isEmpty()) {
            g.fill(sx, sy, sx + 16, sy + 16, 0x88000000);
        } else if (!isIdeaChipSatisfied(selected)) {
            int x0 = sx - 1;
            int y0 = sy - 1;
            g.fill(x0, y0, x0 + 18, y0 + 1, 0xFFFF3333);
            g.fill(x0, y0 + 17, x0 + 18, y0 + 18, 0xFFFF3333);
            g.fill(x0, y0, x0 + 1, y0 + 18, 0xFFFF3333);
            g.fill(x0 + 17, y0, x0 + 18, y0 + 18, 0xFFFF3333);
        }
    }

    @Nullable
    private ResearchDefinition getSelectedDefinition() {
        if (selectedIndex < 0 || selectedIndex >= availableResearch.size()) return null;
        return availableResearch.get(selectedIndex);
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // ── Static background from texture ──
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        // ── Slot labels ──
        g.drawString(font, "Drive", x + 63, y + 66, 0xFFD3D7E5, false);
        g.drawString(font, "Cube", x + 63, y + 100, 0xFFD3D7E5, false);
        g.drawString(font, "Costs", x + 110, y + 66, 0xFFD3D7E5, false);

        // ── Bucket slot direction indicators ──
        g.drawString(font, "\u25BC", x + ResearchTableMenu.BUCKET_IN_X + 3, y + ResearchTableMenu.BUCKET_IN_Y - 4, 0xFF55CCFF, false);
        g.drawString(font, "\u25B2", x + ResearchTableMenu.BUCKET_OUT_X + 3, y + ResearchTableMenu.BUCKET_OUT_Y - 4, 0xFF999999, false);

        // ── Idea chip slot label + dynamic overlay ──
        g.drawString(font, "Idea", x + 164, y + 100, 0xFFD3D7E5, false);
        renderIdeaChipOverlay(g, x + ResearchTableMenu.IDEA_CHIP_X, y + ResearchTableMenu.IDEA_CHIP_Y);

        // ── Fluid gauge label ──
        g.drawString(font, "Fl.", x + 203, y + 66, 0xFFD3D7E5, false);

        // ── Fluid gauge (dynamic) ──
        drawFluidGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H);

        // ── Progress bar (dynamic) ──
        if (menu.isResearching()) {
            float progress = Math.min(1.0f, menu.getScaledProgress());
            int filledWidth = Math.round(PROGRESS_W * progress);

            g.fill(x + PROGRESS_X, y + PROGRESS_Y,
                    x + PROGRESS_X + PROGRESS_W, y + PROGRESS_Y + PROGRESS_H,
                    0xFF222222);
            g.fill(x + PROGRESS_X - 1, y + PROGRESS_Y - 1,
                    x + PROGRESS_X + PROGRESS_W + 1, y + PROGRESS_Y,
                    PANEL_BORDER_DARK);
            g.fill(x + PROGRESS_X - 1, y + PROGRESS_Y + PROGRESS_H,
                    x + PROGRESS_X + PROGRESS_W + 1, y + PROGRESS_Y + PROGRESS_H + 1,
                    PANEL_BORDER_LIGHT);

            if (filledWidth > 0) {
                int green = 0xCC + (int) (0x33 * progress);
                int barColor = 0xFF000000 | (green << 8);
                g.fill(x + PROGRESS_X, y + PROGRESS_Y,
                        x + PROGRESS_X + filledWidth, y + PROGRESS_Y + PROGRESS_H,
                        barColor);
            }
        }
    }

    private void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh) {
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF222222);

        int fluidAmount = menu.getFluidAmount();
        int fluidType = menu.getFluidType();

        if (fluidAmount > 0 && fluidType > 0) {
            int tankCapacity = ResearchTableBlockEntity.TANK_CAPACITY;
            int fillHeight = (int) ((float) gh * fluidAmount / tankCapacity);
            fillHeight = Math.min(fillHeight, gh);
            int fillY = gy + gh - fillHeight;
            int fluidColor = ModFluids.getFluidColor(fluidType);
            g.fill(gx, fillY, gx + gw, gy + gh, fluidColor);

            if (fillHeight > 2) {
                int shine = (fluidColor & 0x00FFFFFF) | 0x44000000;
                g.fill(gx, fillY, gx + gw, fillY + 1, shine);
            }
        }

        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Research list
        renderResearchList(graphics, mouseX, mouseY);

        // Detail pane for selected research
        renderDetailPane(graphics);

        // Active research name + percentage
        if (menu.isResearching()) {
            ResearchTableBlockEntity be = menu.getBlockEntity();
            ResearchDefinition activeDef = be.getActiveDefinition();
            if (activeDef != null) {
                String activeName = activeDef.getDisplayName();
                int nameColor = activeDef.getTier().getColor() | 0xFF000000;
                int percent = (int) (menu.getScaledProgress() * 100);
                graphics.drawCenteredString(font, activeName + "  " + percent + "%",
                    leftPos + PROGRESS_X + PROGRESS_W / 2, topPos + PROGRESS_Y - 10, nameColor);
            }
        }

        // Tooltip on research row hover
        renderResearchTooltip(graphics, mouseX, mouseY);

        // Tooltip on fluid gauge hover
        renderFluidGaugeTooltip(graphics, mouseX, mouseY);

        // Tooltip on idea chip slot hover
        renderIdeaChipTooltip(graphics, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Renders the detail pane below the research list showing info about the selected research.
     */
    private void renderDetailPane(GuiGraphics g) {
        int x = leftPos + DETAIL_X;
        int y = topPos + DETAIL_Y;

        // Detail pane background
        g.fill(x, y, x + DETAIL_W, y + DETAIL_H, 0xFF1E2233);
        g.fill(x, y, x + DETAIL_W, y + 1, PANEL_BORDER_DARK);
        g.fill(x, y, x + 1, y + DETAIL_H, PANEL_BORDER_DARK);
        g.fill(x + DETAIL_W - 1, y, x + DETAIL_W, y + DETAIL_H, PANEL_BORDER_LIGHT);
        g.fill(x, y + DETAIL_H - 1, x + DETAIL_W, y + DETAIL_H, PANEL_BORDER_LIGHT);

        ResearchDefinition def = getSelectedDefinition();
        if (def == null) {
            g.drawString(font, "Select a research entry", x + 4, y + 4, 0xFF666666, false);
            return;
        }

        int textY = y + 3;

        // Research name + tier badge
        int nameColor = def.getTier().getColor() | 0xFF000000;
        String nameStr = def.getDisplayName();
        String tierBadge = "  [" + def.getTier().getDisplayName() + "]";
        g.drawString(font, nameStr, x + 4, textY, nameColor, false);
        g.drawString(font, tierBadge, x + 4 + font.width(nameStr), textY, 0xFF888888, false);
        textY += 11;

        // Flavor text (italic grey) — if available
        String flavorText = def.getFlavorText();
        if (flavorText != null && !flavorText.isEmpty()) {
            // Truncate if too wide
            if (font.width(flavorText) > DETAIL_W - 12) {
                while (font.width(flavorText + "\u2026") > DETAIL_W - 12 && flavorText.length() > 3) {
                    flavorText = flavorText.substring(0, flavorText.length() - 1);
                }
                flavorText += "\u2026";
            }
            g.drawString(font, "\u201C" + flavorText + "\u201D", x + 4, textY, 0xFF777799, false);
            textY += 11;
        }

        // Duration + costs summary
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("%.0fs", def.getDurationSeconds()));
        if (!def.getItemCosts().isEmpty()) {
            summary.append(" | ");
            boolean first = true;
            for (ItemCost cost : def.getItemCosts()) {
                if (!first) summary.append(", ");
                summary.append(cost.getItem().getDescription().getString()).append(" x").append(cost.count());
                first = false;
            }
        }
        FluidCost fc = def.getFluidCost();
        if (fc != null) {
            summary.append(" | ").append(fc.amount()).append("mB ");
            String fn = fc.getFluidName();
            summary.append(fn.substring(0, 1).toUpperCase()).append(fn.substring(1));
        }

        // Truncate summary if too long
        String sumStr = summary.toString();
        if (font.width(sumStr) > DETAIL_W - 8) {
            while (font.width(sumStr + "\u2026") > DETAIL_W - 8 && sumStr.length() > 3) {
                sumStr = sumStr.substring(0, sumStr.length() - 1);
            }
            sumStr += "\u2026";
        }
        g.drawString(font, sumStr, x + 4, textY, 0xFFAAB0C0, false);
    }

    private void renderResearchList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        // List background
        graphics.fill(x - 2, y - 2, x + LIST_W + 2, y + VISIBLE_ROWS * ROW_H + 2, LIST_BG);
        // Border
        graphics.fill(x - 2, y - 2, x + LIST_W + 2, y - 1, PANEL_BORDER_DARK);
        graphics.fill(x - 2, y - 2, x - 1, y + VISIBLE_ROWS * ROW_H + 2, PANEL_BORDER_DARK);
        graphics.fill(x + LIST_W + 1, y - 2, x + LIST_W + 2, y + VISIBLE_ROWS * ROW_H + 2, PANEL_BORDER_LIGHT);
        graphics.fill(x - 2, y + VISIBLE_ROWS * ROW_H + 1, x + LIST_W + 2, y + VISIBLE_ROWS * ROW_H + 2, PANEL_BORDER_LIGHT);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int displayIdx = scrollOffset + i;
            if (displayIdx >= displayRows.size()) break;

            ListRow row = displayRows.get(displayIdx);
            int rowY = y + i * ROW_H;

            if (row.isHeader()) {
                String headerLabel = "\u25B8 " + row.headerText();
                if (font.width(headerLabel) > LIST_W - 4) {
                    while (font.width(headerLabel + "\u2026") > LIST_W - 4 && headerLabel.length() > 3) {
                        headerLabel = headerLabel.substring(0, headerLabel.length() - 1);
                    }
                    headerLabel += "\u2026";
                }
                graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFF2A2A1A);
                graphics.drawString(font, headerLabel, x + 3, rowY + 3, 0xFFCCAA00, false);
            } else {
                ResearchDefinition def = row.definition();
                boolean locked = !isPrerequisiteMet(def);

                if (def.getId().equals(selectedId)) {
                    graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, locked ? 0xFF442222 : 0xFF334488);
                } else if (mouseX >= x && mouseX < x + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H) {
                    graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFF2A2A3A);
                }

                String prefix = locked ? "\uD83D\uDD12 " : "";
                String name = def.getDisplayName();
                String label = prefix + name;

                if (font.width(label) > LIST_W - 6) {
                    while (font.width(label + "\u2026") > LIST_W - 6 && label.length() > 3) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "\u2026";
                }

                int textColor = locked ? 0xFF666666 : (def.getTier().getColor() | 0xFF000000);
                graphics.drawString(font, label, x + 3, rowY + 3, textColor, false);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawString(font, "\u25B2", x + LIST_W - 8, y - 12, 0xAAAAAAA, false);
        }
        if (scrollOffset + VISIBLE_ROWS < displayRows.size()) {
            graphics.drawString(font, "\u25BC", x + LIST_W - 8, y + VISIBLE_ROWS * ROW_H + 3, 0xAAAAAA, false);
        }
    }

    private void renderResearchTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX < x || mouseX >= x + LIST_W || mouseY < y || mouseY >= y + VISIBLE_ROWS * ROW_H) {
            return;
        }

        int row = (mouseY - y) / ROW_H;
        int displayIdx = scrollOffset + row;
        if (displayIdx < 0 || displayIdx >= displayRows.size()) return;

        ListRow listRow = displayRows.get(displayIdx);
        if (listRow.isHeader()) return;

        ResearchDefinition def = listRow.definition();
        List<Component> tooltip = new ArrayList<>();

        tooltip.add(Component.literal(def.getDisplayName())
                .withStyle(s -> s.withColor(def.getTier().getColor())));

        if (def.getDescription() != null) {
            tooltip.add(Component.literal(def.getDescription())
                    .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
        }

        if (def.getCategory() != null) {
            tooltip.add(Component.literal("Category: " + def.getCategory())
                    .withStyle(s -> s.withColor(0xCCAA00)));
        }

        tooltip.add(Component.literal("Tier: " + def.getTier().getDisplayName()
                + "  |  " + String.format("%.0fs", def.getDurationSeconds()))
                .withStyle(s -> s.withColor(0x888888)));

        if (!def.getItemCosts().isEmpty()) {
            tooltip.add(Component.literal("Costs:").withStyle(s -> s.withColor(0xCCCC00)));
            for (ItemCost cost : def.getItemCosts()) {
                tooltip.add(Component.literal("  \u2022 " + cost.getItem().getDescription().getString() + " x" + cost.count())
                        .withStyle(s -> s.withColor(0xBBBBBB)));
            }
        }

        FluidCost fluidCost = def.getFluidCost();
        if (fluidCost != null) {
            String fluidName = fluidCost.getFluidName();
            fluidName = fluidName.substring(0, 1).toUpperCase() + fluidName.substring(1);
            tooltip.add(Component.literal("Fluid: " + fluidCost.amount() + " mB " + fluidName)
                    .withStyle(s -> s.withColor(0x55CCFF)));
        }

        if (def.getIdeaChip().isPresent()) {
            ItemStack chip = def.getIdeaChip().get();
            boolean satisfied = isIdeaChipSatisfied(def);
            String icon = satisfied ? "\u2714" : "\u2718";
            int color = satisfied ? 0x55FF55 : 0xFF5555;
            tooltip.add(Component.literal("Idea Chip: " + icon + " " + chip.getHoverName().getString())
                    .withStyle(s -> s.withColor(color)));
        }

        if (!(def.getPrerequisites() instanceof NonePrerequisite)) {
            boolean met = isPrerequisiteMet(def);
            String prereqIcon = met ? "\u2714" : "\u2718";
            int prereqColor = met ? 0x55FF55 : 0xFF5555;
            tooltip.add(Component.literal("Prerequisites: " + prereqIcon + " " + def.getPrerequisites().describe())
                    .withStyle(s -> s.withColor(prereqColor)));
        }

        if (def.hasRecipePool()) {
            StringBuilder rewardLine = new StringBuilder("Rewards: ");
            boolean first = true;
            for (ResourceLocation recipeRl : def.getRecipePool()) {
                if (!first) rewardLine.append(" or ");
                rewardLine.append(RecipeOutputResolver.formatOutput(recipeRl.toString()));
                first = false;
            }
            tooltip.add(Component.literal(rewardLine.toString())
                    .withStyle(s -> s.withColor(0x55FFFF)));
        }

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderFluidGaugeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int gx = leftPos + GAUGE_X;
        int gy = topPos + GAUGE_Y;

        if (mouseX < gx - 1 || mouseX >= gx + GAUGE_W + 1 || mouseY < gy - 1 || mouseY >= gy + GAUGE_H + 1) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        int fluidAmount = menu.getFluidAmount();
        int fluidType = menu.getFluidType();

        if (fluidAmount > 0 && fluidType > 0) {
            int color = ModFluids.getFluidColor(fluidType);
            tooltip.add(Component.literal(ModFluids.getFluidName(fluidType))
                    .withStyle(s -> s.withColor(color & 0x00FFFFFF)));
            tooltip.add(Component.literal(fluidAmount + " / " + ResearchTableBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        } else {
            tooltip.add(Component.literal("Empty")
                    .withStyle(s -> s.withColor(0x888888)));
            tooltip.add(Component.literal("0 / " + ResearchTableBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        }
        tooltip.add(Component.literal("Insert fluid buckets below")
                .withStyle(s -> s.withColor(0x666666).withItalic(true)));

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderIdeaChipTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int sx = leftPos + ResearchTableMenu.IDEA_CHIP_X;
        int sy = topPos + ResearchTableMenu.IDEA_CHIP_Y;

        if (mouseX < sx - 1 || mouseX >= sx + 17 || mouseY < sy - 1 || mouseY >= sy + 17) {
            return;
        }

        ItemStack slotStack = menu.getBlockEntity().getInventory()
                .getStackInSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP);
        if (!slotStack.isEmpty()) return;

        List<Component> tooltip = new ArrayList<>();
        ResearchDefinition selected = getSelectedDefinition();

        if (selected == null || selected.getIdeaChip().isEmpty()) {
            tooltip.add(Component.literal("Idea Chip Slot")
                    .withStyle(s -> s.withColor(0x888888)));
            tooltip.add(Component.literal("No idea chip required for this research.")
                    .withStyle(s -> s.withColor(0x666666).withItalic(true)));
        } else {
            ItemStack required = selected.getIdeaChip().get();
            tooltip.add(Component.literal("Idea Chip Slot")
                    .withStyle(s -> s.withColor(0xFF5555)));
            tooltip.add(Component.literal("Requires: " + required.getHoverName().getString())
                    .withStyle(s -> s.withColor(0xFFAAAA)));
        }

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    // ══════════════════════════════════════════════════════════════
    //  Input handling
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + VISIBLE_ROWS * ROW_H) {
            int row = (int) ((mouseY - y) / ROW_H);
            int displayIdx = scrollOffset + row;
            if (displayIdx >= 0 && displayIdx < displayRows.size()) {
                ListRow listRow = displayRows.get(displayIdx);
                if (!listRow.isHeader()) {
                    ResearchDefinition def = listRow.definition();
                    selectedId = def.getId();
                    for (int i = 0; i < availableResearch.size(); i++) {
                        if (availableResearch.get(i).getId().equals(selectedId)) {
                            selectedIndex = i;
                            break;
                        }
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + VISIBLE_ROWS * ROW_H + 14) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
            } else if (scrollY < 0 && scrollOffset + VISIBLE_ROWS < displayRows.size()) {
                scrollOffset++;
            }
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
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF343841, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFE6EAF5, false);
    }
}
