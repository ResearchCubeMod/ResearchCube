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
import com.researchcube.research.ResearchTier;
import com.researchcube.research.ItemCost;
import com.researchcube.research.FluidCost;
import com.researchcube.research.prerequisite.NonePrerequisite;
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Client-side screen for the Research Table.
 *
 * Expanded layout (520x286):
 *   Left panel: Drive slot, Cube slot, 3×2 cost grid, progress bar, Start/Stop buttons
 *   Right panel: Scrollable research list grouped by category (8 visible rows, 130px wide)
 *   Bottom: Player inventory + hotbar (centered)
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {

    /** A display row in the research list: either a category header or a research entry. */
    private record ListRow(boolean isHeader, String headerText, ResearchDefinition definition) {
        static ListRow header(String text) { return new ListRow(true, text, null); }
        static ListRow entry(ResearchDefinition def) { return new ListRow(false, null, def); }
    }

    // ── Layout constants ──

    // Research list (right panel)
    private static final int LIST_X = 194;
    private static final int LIST_Y = 38;
    private static final int LIST_W = 306;
    private static final int ROW_H = 14;
    private static final int VISIBLE_ROWS = 7;

    // Progress bar
    private static final int PROGRESS_X = 26;
    private static final int PROGRESS_Y = 118;
    private static final int PROGRESS_W = 146;
    private static final int PROGRESS_H = 8;

    // Fluid gauge (vertical bar on right side of left panel)
    private static final int GAUGE_X = 154;
    private static final int GAUGE_Y = 34;
    private static final int GAUGE_W = 16;
    private static final int GAUGE_H = 58;

    // Panel regions
    private static final int LEFT_PANEL_X = 20;
    private static final int LEFT_PANEL_Y = 20;
    private static final int LEFT_PANEL_W = 160;
    private static final int LEFT_PANEL_H = 132;

    private static final int RIGHT_PANEL_X = 186;
    private static final int RIGHT_PANEL_Y = 20;
    private static final int RIGHT_PANEL_W = 314;
    private static final int RIGHT_PANEL_H = 132;

    // Colors
    private static final int BG_OUTER = 0xFFC6C6C6;
    private static final int PANEL_BG = 0xFF4A4F60;
    private static final int PANEL_BORDER_LIGHT = 0xFF7E87A6;
    private static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    private static final int PANEL_INNER = 0xFF2E3342;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF2B2E38;
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

    public ResearchTableScreen(ResearchTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 520;
        this.imageHeight = 286;
        this.inventoryLabelX = 179;
        this.inventoryLabelY = 154;
    }

    @Override
    protected void init() {
        super.init();

        // Start / Cancel buttons below the progress bar
        startButton = Button.builder(Component.literal("Start"), btn -> onStartResearch())
                .bounds(leftPos + 26, topPos + 132, 70, 18)
                .build();
        addRenderableWidget(startButton);

        cancelButton = Button.builder(Component.literal("Stop"), btn -> onCancelResearch())
                .bounds(leftPos + 102, topPos + 132, 70, 18)
                .build();
        addRenderableWidget(cancelButton);

        wipeButton = Button.builder(Component.literal("Wipe"), btn -> onWipeTank())
                .bounds(leftPos + 114, topPos + 86, 34, 16)
                .build();
        addRenderableWidget(wipeButton);

        treeViewButton = Button.builder(Component.literal("Tree"), btn -> onOpenTreeView())
            .bounds(leftPos + 432, topPos + 22, 66, 18)
                .build();
        addRenderableWidget(treeViewButton);

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
                && isPrerequisiteMet(availableResearch.get(selectedIndex));
        startButton.active = canStart;
        cancelButton.active = menu.isResearching();
        wipeButton.active = menu.getFluidAmount() > 0;
        treeViewButton.active = true;
    }

    private boolean isPrerequisiteMet(ResearchDefinition def) {
        Set<String> completed = menu.getCompletedResearch();
        return def.getPrerequisites().isSatisfied(completed);
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // ── Outer container background (vanilla grey) ──
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_OUTER);
        // Top/left highlight, bottom/right shadow (bevel)
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);

        // ── Left panel (dark inset) ──
        drawInsetPanel(g, x + LEFT_PANEL_X, y + LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);

        // ── Right panel (dark inset) ──
        drawInsetPanel(g, x + RIGHT_PANEL_X, y + RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);

        // ── Player inventory area ──
        drawInsetPanel(g, x + 20, y + 156, 480, 122);

        // ── Slot backgrounds ──
        // Drive slot
        drawSlotBg(g, x + ResearchTableMenu.DRIVE_X, y + ResearchTableMenu.DRIVE_Y);
        // Cube slot
        drawSlotBg(g, x + ResearchTableMenu.CUBE_X, y + ResearchTableMenu.CUBE_Y);
        // Cost slots 3×2
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlotBg(g, x + ResearchTableMenu.COST_X + col * 18, y + ResearchTableMenu.COST_Y + row * 18);
            }
        }
        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + ResearchTableMenu.PLAYER_INV_X + col * 18, y + ResearchTableMenu.PLAYER_INV_Y + row * 18);
            }
        }
        // Hotbar slots
        for (int col = 0; col < 9; col++) {
            drawSlotBg(g, x + ResearchTableMenu.HOTBAR_X + col * 18, y + ResearchTableMenu.HOTBAR_Y);
        }

        // ── Slot labels ──
        g.drawString(font, "Drive", x + 24, y + 28, 0xFFD3D7E5, false);
        g.drawString(font, "Cube", x + 24, y + 64, 0xFFD3D7E5, false);
        g.drawString(font, "Costs", x + 70, y + 28, 0xFFD3D7E5, false);

        // ── Bucket slots ──
        drawSlotBg(g, x + ResearchTableMenu.BUCKET_IN_X, y + ResearchTableMenu.BUCKET_IN_Y);
        drawSlotBg(g, x + ResearchTableMenu.BUCKET_OUT_X, y + ResearchTableMenu.BUCKET_OUT_Y);
        g.drawString(font, "\u25BC", x + ResearchTableMenu.BUCKET_IN_X + 3, y + ResearchTableMenu.BUCKET_IN_Y - 4, 0xFF55CCFF, false);
        g.drawString(font, "\u25B2", x + ResearchTableMenu.BUCKET_OUT_X + 3, y + ResearchTableMenu.BUCKET_OUT_Y - 4, 0xFF999999, false);

        // ── Fluid gauge ──
        drawFluidGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H);

        // ── Progress bar ──
        if (menu.isResearching()) {
            float progress = Math.min(1.0f, menu.getScaledProgress());
            int filledWidth = Math.round(PROGRESS_W * progress);

            // Bar background
            g.fill(x + PROGRESS_X, y + PROGRESS_Y,
                    x + PROGRESS_X + PROGRESS_W, y + PROGRESS_Y + PROGRESS_H,
                    0xFF222222);
            // Bar border
            g.fill(x + PROGRESS_X - 1, y + PROGRESS_Y - 1,
                    x + PROGRESS_X + PROGRESS_W + 1, y + PROGRESS_Y,
                    PANEL_BORDER_DARK);
            g.fill(x + PROGRESS_X - 1, y + PROGRESS_Y + PROGRESS_H,
                    x + PROGRESS_X + PROGRESS_W + 1, y + PROGRESS_Y + PROGRESS_H + 1,
                    PANEL_BORDER_LIGHT);

            // Filled portion (green gradient)
            if (filledWidth > 0) {
                int green = 0xCC + (int) (0x33 * progress);
                int barColor = 0xFF000000 | (green << 8);
                g.fill(x + PROGRESS_X, y + PROGRESS_Y,
                        x + PROGRESS_X + filledWidth, y + PROGRESS_Y + PROGRESS_H,
                        barColor);
            }
        }
    }

    /** Draw an inset dark panel with a 1px bevelled border. */
    private void drawInsetPanel(GuiGraphics g, int px, int py, int pw, int ph) {
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

    /** Draw the 18×18 slot background (Minecraft-style inset square). */
    private void drawSlotBg(GuiGraphics g, int sx, int sy) {
        // The slot position in Minecraft is the top-left of the 16×16 inner area.
        // The border starts 1px above/left.
        int x0 = sx - 1;
        int y0 = sy - 1;
        g.fill(x0, y0, x0 + 18, y0 + 18, SLOT_BG);
        g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, SLOT_INNER);
        // Top/left border shadow
        g.fill(x0, y0, x0 + 18, y0 + 1, PANEL_BORDER_DARK);
        g.fill(x0, y0, x0 + 1, y0 + 18, PANEL_BORDER_DARK);
    }

    /**
     * Draw the vertical fluid gauge bar showing current tank contents.
     * Fills from bottom to top, colored by fluid type.
     */
    private void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh) {
        // Frame (inset border)
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

            // Subtle shine line at the top of the fill
            if (fillHeight > 2) {
                int shine = (fluidColor & 0x00FFFFFF) | 0x44000000;
                g.fill(gx, fillY, gx + gw, fillY + 1, shine);
            }
        }

        // Bottom/right highlight
        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Research list
        renderResearchList(graphics, mouseX, mouseY);

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

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderResearchList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        // Header label
        graphics.drawString(font, "Research", x, y - 12, 0xFFC8D2EE, false);

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
                // Category header row
                String headerLabel = "\u25B8 " + row.headerText();
                if (font.width(headerLabel) > LIST_W - 4) {
                    // Truncate to fit
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

                // Selection highlight
                if (def.getId().equals(selectedId)) {
                    graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, locked ? 0xFF442222 : 0xFF334488);
                } else if (mouseX >= x && mouseX < x + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H) {
                    // Hover highlight
                    graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFF2A2A3A);
                }

                // Build label
                String prefix = locked ? "\uD83D\uDD12 " : "";
                String name = def.getDisplayName();
                String label = prefix + name;

                // Truncate to fit list width
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

    /**
     * Renders a tooltip when hovering over a research list entry.
     */
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

        // Name
        tooltip.add(Component.literal(def.getDisplayName())
                .withStyle(s -> s.withColor(def.getTier().getColor())));

        // Description
        if (def.getDescription() != null) {
            tooltip.add(Component.literal(def.getDescription())
                    .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
        }

        // Category
        if (def.getCategory() != null) {
            tooltip.add(Component.literal("Category: " + def.getCategory())
                    .withStyle(s -> s.withColor(0xCCAA00)));
        }

        // Tier + Duration
        tooltip.add(Component.literal("Tier: " + def.getTier().getDisplayName()
                + "  |  " + String.format("%.0fs", def.getDurationSeconds()))
                .withStyle(s -> s.withColor(0x888888)));

        // Item costs
        if (!def.getItemCosts().isEmpty()) {
            tooltip.add(Component.literal("Costs:").withStyle(s -> s.withColor(0xCCCC00)));
            for (ItemCost cost : def.getItemCosts()) {
                tooltip.add(Component.literal("  \u2022 " + cost.getItem().getDescription().getString() + " x" + cost.count())
                        .withStyle(s -> s.withColor(0xBBBBBB)));
            }
        }

        // Fluid cost
        FluidCost fluidCost = def.getFluidCost();
        if (fluidCost != null) {
            String fluidName = fluidCost.getFluidName();
            fluidName = fluidName.substring(0, 1).toUpperCase() + fluidName.substring(1);
            tooltip.add(Component.literal("Fluid: " + fluidCost.amount() + " mB " + fluidName)
                    .withStyle(s -> s.withColor(0x55CCFF)));
        }

        // Prerequisites status
        if (!(def.getPrerequisites() instanceof NonePrerequisite)) {
            boolean met = isPrerequisiteMet(def);
            String prereqIcon = met ? "\u2714" : "\u2718";
            int prereqColor = met ? 0x55FF55 : 0xFF5555;
            tooltip.add(Component.literal("Prerequisites: " + prereqIcon + " " + def.getPrerequisites().describe())
                    .withStyle(s -> s.withColor(prereqColor)));
        }

        // Recipe pool — resolve to output item names
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

    /**
     * Renders a tooltip when hovering over the fluid gauge bar.
     */
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
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Dark title for the light outer strip.
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF343841, false);
        // Inventory label on dark panel should be bright.
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFE6EAF5, false);
    }
}
