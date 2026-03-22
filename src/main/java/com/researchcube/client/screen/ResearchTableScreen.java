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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client-side screen for the Research Table.
 *
 * Layout (470x260) - compact square design:
 *   Top section: Upper panel with 3 view modes:
 *     - LIST: Search bar, Research list (4 rows), Detail pane
 *     - TREE: Embedded research tree visualization
 *     - PROGRESS: Active research info, description, progress bar
 *   Bottom section: Machine panel (left) + Player inventory (right)
 *     - Machine: Drive, Cube, Idea, Cost grid, Fluid gauge, Buckets, Buttons
 *     - Player inventory: 9x3 main + 9x1 hotbar
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {

    /** View mode for the upper panel. */
    private enum ViewMode { LIST, TREE, PROGRESS }

    /** A display row in the research list: either a category header or a research entry. */
    private record ListRow(boolean isHeader, String headerText, ResearchDefinition definition) {
        static ListRow header(String text) { return new ListRow(true, text, null); }
        static ListRow entry(ResearchDefinition def) { return new ListRow(false, null, def); }
    }

    // ── Texture ──
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchCubeMod.MOD_ID, "textures/gui/research_table.png");
    private static final int TEX_W = ResearchTableMenu.GUI_WIDTH;
    private static final int TEX_H = ResearchTableMenu.GUI_HEIGHT;

    // Colors (for dynamic elements)
    private static final int PANEL_BORDER_LIGHT = 0xFF7E87A6;
    private static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    private static final int LIST_BG = 0xFF252A3E;

    // View mode state
    private ViewMode currentView = ViewMode.LIST;
    private ViewMode preferredView = ViewMode.LIST;  // user preference when not researching

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
    private Button listViewButton;
    private EditBox searchBox;
    private String searchFilter = "";

    public ResearchTableScreen(ResearchTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = ResearchTableMenu.GUI_WIDTH;
        this.imageHeight = ResearchTableMenu.GUI_HEIGHT;
        this.inventoryLabelX = ResearchTableMenu.PLAYER_INV_X;
        this.inventoryLabelY = ResearchTableMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();

        // Start button
        startButton = Button.builder(Component.literal("Start"), btn -> onStartResearch())
                .bounds(leftPos + ResearchTableMenu.START_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build();
        addRenderableWidget(startButton);

        // Cancel/Stop button
        cancelButton = Button.builder(Component.literal("Stop"), btn -> onCancelResearch())
                .bounds(leftPos + ResearchTableMenu.STOP_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build();
        addRenderableWidget(cancelButton);

        // Wipe tank button
        wipeButton = Button.builder(Component.literal("Wipe"), btn -> onWipeTank())
                .bounds(leftPos + ResearchTableMenu.WIPE_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build();
        addRenderableWidget(wipeButton);

        // Tree view button (switches to tree view)
        treeViewButton = Button.builder(Component.literal("Tree"), btn -> onSwitchToTreeView())
                .bounds(leftPos + ResearchTableMenu.TREE_BTN_X, topPos + ResearchTableMenu.TREE_BTN_Y,
                        ResearchTableMenu.TREE_BTN_W, ResearchTableMenu.TREE_BTN_H)
                .build();
        addRenderableWidget(treeViewButton);

        // List view button (switches back to list view)
        listViewButton = Button.builder(Component.literal("List"), btn -> onSwitchToListView())
                .bounds(leftPos + ResearchTableMenu.LIST_BTN_X, topPos + ResearchTableMenu.LIST_BTN_Y,
                        ResearchTableMenu.LIST_BTN_W, ResearchTableMenu.LIST_BTN_H)
                .build();
        addRenderableWidget(listViewButton);

        // Search box above the research list
        searchBox = new EditBox(font,
                leftPos + ResearchTableMenu.SEARCH_X, topPos + ResearchTableMenu.SEARCH_Y,
                ResearchTableMenu.SEARCH_W, ResearchTableMenu.SEARCH_H,
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
        updateViewMode();
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
        if (!canStartResearch(def)) return;

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

    private void onSwitchToTreeView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new ResearchTreeScreen(menu, mc.player.getInventory(), this.title));
    }

    private void onSwitchToListView() {
        preferredView = ViewMode.LIST;
        updateViewMode();
    }

    /**
     * Updates the view mode based on research state and user preference.
     * When researching AND definition is available: show PROGRESS view
     * When not researching: show user's preferred view (LIST or TREE)
     */
    private void updateViewMode() {
        if (menu.isResearching()) {
            // Only show PROGRESS view if the active definition is synced to client
            ResearchDefinition activeDef = menu.getBlockEntity().getActiveDefinition();
            if (activeDef != null) {
                currentView = ViewMode.PROGRESS;
            } else {
                // Stay in current view until definition is synced
                currentView = preferredView;
            }
        } else {
            currentView = preferredView;
        }

        // Update visibility of view-specific components
        boolean showListComponents = (currentView == ViewMode.LIST);
        searchBox.visible = showListComponents;
        treeViewButton.visible = showListComponents;
        listViewButton.visible = (currentView == ViewMode.TREE);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshResearchList();
        updateViewMode();

        // Start button: only active if research is selected AND all requirements are met
        ResearchDefinition selectedDef = getSelectedDefinition();
        boolean canStart = !menu.isResearching() && selectedDef != null && canStartResearch(selectedDef);
        startButton.active = canStart;
        cancelButton.active = menu.isResearching();
        wipeButton.active = menu.getFluidAmount() > 0;
    }

    /**
     * Checks if research can be started (prerequisites, idea chip, and recipe items/fluids available).
     */
    private boolean canStartResearch(ResearchDefinition def) {
        if (!isPrerequisiteMet(def)) return false;
        if (!isIdeaChipSatisfied(def)) return false;
        if (!hasRequiredItems(def)) return false;
        if (!hasRequiredFluid(def)) return false;
        return true;
    }

    /**
     * Checks if the required item costs are present in the cost slots.
     * Uses menu slots directly for proper client-side sync.
     */
    private boolean hasRequiredItems(ResearchDefinition def) {
        if (def.getItemCosts().isEmpty()) return true;

        // Collect all items in cost slots using menu's synced slots
        Map<Item, Integer> available = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            int slotIndex = ResearchTableBlockEntity.COST_SLOT_START + i;
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        // Check each required cost
        for (ItemCost cost : def.getItemCosts()) {
            Item requiredItem = cost.getItem();
            int requiredCount = cost.count();
            int availableCount = available.getOrDefault(requiredItem, 0);
            if (availableCount < requiredCount) return false;
        }
        return true;
    }

    /**
     * Checks if the required fluid is present in the tank.
     */
    private boolean hasRequiredFluid(ResearchDefinition def) {
        FluidCost fluidCost = def.getFluidCost();
        if (fluidCost == null) return true;

        int requiredAmount = fluidCost.amount();
        int tankFluidType = menu.getFluidType();
        int tankAmount = menu.getFluidAmount();

        // Check if fluid type matches
        int requiredType = ModFluids.getFluidIndex(fluidCost.getFluid());
        if (tankFluidType != requiredType) return false;

        return tankAmount >= requiredAmount;
    }

    private boolean isPrerequisiteMet(ResearchDefinition def) {
        Set<String> completed = menu.getCompletedResearch();
        return def.getPrerequisites().isSatisfied(completed);
    }

    private boolean isIdeaChipSatisfied(ResearchDefinition def) {
        if (def.getIdeaChip().isEmpty()) return true;
        ItemStack required = def.getIdeaChip().get();
        // Use menu's synced slot instead of direct BlockEntity inventory access
        ItemStack candidate = menu.getSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP).getItem();
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

        // ── Upper panel content (view mode dependent) ──
        renderUpperPanel(g, x, y, mouseX, mouseY);

        // ── Slot labels (above slot row) ──
        int labelY = y + ResearchTableMenu.LABEL_Y;
        g.drawString(font, "Dr", x + ResearchTableMenu.DRIVE_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Cb", x + ResearchTableMenu.CUBE_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Id", x + ResearchTableMenu.IDEA_CHIP_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Costs", x + ResearchTableMenu.COST_X, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Fl", x + ResearchTableMenu.FLUID_GAUGE_X + 3, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "I/O", x + ResearchTableMenu.BUCKET_IN_X, labelY, 0xFFD3D7E5, false);

        // ── Idea chip dynamic overlay ──
        renderIdeaChipOverlay(g, x + ResearchTableMenu.IDEA_CHIP_X, y + ResearchTableMenu.IDEA_CHIP_Y);

        // ── Fluid gauge (dynamic) ──
        drawFluidGauge(g, x + ResearchTableMenu.FLUID_GAUGE_X, y + ResearchTableMenu.FLUID_GAUGE_Y,
                ResearchTableMenu.FLUID_GAUGE_W, ResearchTableMenu.FLUID_GAUGE_H);
    }

    /**
     * Renders the upper panel content based on current view mode.
     */
    private void renderUpperPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        switch (currentView) {
            case LIST -> {
                // List view: search + list + detail pane handled in render()
            }
            case TREE -> {
                // Tree view: render embedded tree
                renderTreeView(g, x, y, mouseX, mouseY);
            }
            case PROGRESS -> {
                // Progress view: detailed progress info
                renderProgressView(g, x, y);
            }
        }
    }

    /**
     * Renders the embedded tree view in the upper panel.
     */
    private void renderTreeView(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int vx = x + ResearchTableMenu.PROGRESS_VIEW_X;
        int vy = y + ResearchTableMenu.PROGRESS_VIEW_Y;
        int vw = ResearchTableMenu.PROGRESS_VIEW_W;
        int vh = ResearchTableMenu.PROGRESS_VIEW_H;

        // Background
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF1A1E2A);

        // TODO: Integrate tree rendering from ResearchTreeScreen
        // For now, show a placeholder message
        g.drawString(font, "Research Tree View", vx + 4, vy + 4, 0xFFD3D7E5, false);
        g.drawString(font, "(Click 'List' to return to list view)", vx + 4, vy + 16, 0xFF888888, false);

        // Draw a mini-map style representation
        Set<String> completed = menu.getCompletedResearch();
        int nodeX = vx + 10;
        int nodeY = vy + 36;
        int nodeSize = 12;
        int spacing = 20;
        int col = 0;
        int row = 0;
        int maxCols = vw / spacing - 1;

        for (ResearchDefinition def : ResearchRegistry.getAll()) {
            boolean isComplete = completed.contains(def.getId().toString());
            boolean isAvailable = def.getPrerequisites().isSatisfied(completed);

            int color;
            if (isComplete) {
                color = 0xFF55FF55;
            } else if (isAvailable) {
                color = def.getTier().getColor() | 0xFF000000;
            } else {
                color = 0xFF444444;
            }

            int nx = nodeX + col * spacing;
            int ny = nodeY + row * spacing;

            g.fill(nx, ny, nx + nodeSize, ny + nodeSize, color);
            g.fill(nx, ny, nx + nodeSize, ny + 1, PANEL_BORDER_LIGHT);
            g.fill(nx, ny, nx + 1, ny + nodeSize, PANEL_BORDER_LIGHT);
            g.fill(nx + nodeSize - 1, ny, nx + nodeSize, ny + nodeSize, PANEL_BORDER_DARK);
            g.fill(nx, ny + nodeSize - 1, nx + nodeSize, ny + nodeSize, PANEL_BORDER_DARK);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
                if (nodeY + row * spacing + nodeSize > vy + vh - 8) break;
            }
        }
    }

    /**
     * Renders the progress view showing active research details.
     */
    private void renderProgressView(GuiGraphics g, int x, int y) {
        int vx = x + ResearchTableMenu.PROGRESS_VIEW_X;
        int vy = y + ResearchTableMenu.PROGRESS_VIEW_Y;
        int vw = ResearchTableMenu.PROGRESS_VIEW_W;
        int vh = ResearchTableMenu.PROGRESS_VIEW_H;

        // Background
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF1A1E2A);
        g.fill(vx, vy, vx + vw, vy + 1, PANEL_BORDER_DARK);
        g.fill(vx, vy, vx + 1, vy + vh, PANEL_BORDER_DARK);
        g.fill(vx + vw - 1, vy, vx + vw, vy + vh, PANEL_BORDER_LIGHT);
        g.fill(vx, vy + vh - 1, vx + vw, vy + vh, PANEL_BORDER_LIGHT);

        ResearchTableBlockEntity be = menu.getBlockEntity();
        ResearchDefinition activeDef = be.getActiveDefinition();

        if (activeDef == null) {
            g.drawString(font, "No active research", vx + 8, vy + 8, 0xFF666666, false);
            return;
        }

        int textY = vy + 8;
        int lineHeight = 12;

        // Line 1: Research name with tier color
        int nameColor = activeDef.getTier().getColor() | 0xFF000000;
        g.drawString(font, activeDef.getDisplayName(), vx + 8, textY, nameColor, false);
        String tierBadge = " [" + activeDef.getTier().getDisplayName() + "]";
        g.drawString(font, tierBadge, vx + 8 + font.width(activeDef.getDisplayName()), textY, 0xFF888888, false);
        textY += lineHeight + 4;

        // Line 2: Category
        if (activeDef.getCategory() != null && !activeDef.getCategory().isEmpty()) {
            g.drawString(font, "Category: " + activeDef.getCategory(), vx + 8, textY, 0xFFCCAA00, false);
            textY += lineHeight;
        }

        // Line 3: Description
        if (activeDef.getDescription() != null && !activeDef.getDescription().isEmpty()) {
            String desc = activeDef.getDescription();
            if (font.width(desc) > vw - 20) {
                while (font.width(desc + "\u2026") > vw - 20 && desc.length() > 3) {
                    desc = desc.substring(0, desc.length() - 1);
                }
                desc += "\u2026";
            }
            g.drawString(font, desc, vx + 8, textY, 0xFFAAB0C0, false);
            textY += lineHeight;
        }

        // Progress info
        textY += 8;
        float progress = Math.min(1.0f, menu.getScaledProgress());
        int percent = (int) (progress * 100);
        float totalSeconds = activeDef.getDurationSeconds();
        float remainingSeconds = totalSeconds * (1.0f - progress);
        int mins = (int) (remainingSeconds / 60);
        int secs = (int) (remainingSeconds % 60);

        String progressText = String.format("Progress: %d%% - %d:%02d remaining", percent, mins, secs);
        g.drawString(font, progressText, vx + 8, textY, 0xFFE6EAF5, false);

        // Progress bar
        int barX = x + ResearchTableMenu.PROGRESS_BAR_X;
        int barY = y + ResearchTableMenu.PROGRESS_BAR_Y;
        int barW = ResearchTableMenu.PROGRESS_BAR_W;
        int barH = ResearchTableMenu.PROGRESS_BAR_H;

        // Bar background
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF222222);
        g.fill(barX, barY, barX + barW, barY + 1, PANEL_BORDER_DARK);
        g.fill(barX, barY, barX + 1, barY + barH, PANEL_BORDER_DARK);
        g.fill(barX + barW - 1, barY, barX + barW, barY + barH, PANEL_BORDER_LIGHT);
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, PANEL_BORDER_LIGHT);

        // Filled portion
        int filledWidth = Math.round((barW - 2) * progress);
        if (filledWidth > 0) {
            int barColor = nameColor;
            g.fill(barX + 1, barY + 1, barX + 1 + filledWidth, barY + barH - 1, barColor);
        }

        // Percentage text on bar
        String pctStr = percent + "%";
        int textWidth = font.width(pctStr);
        g.drawString(font, pctStr, barX + (barW - textWidth) / 2, barY + 2, 0xFFFFFFFF, true);
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

        // View-specific content
        if (currentView == ViewMode.LIST) {
            // Research list
            renderResearchList(graphics, mouseX, mouseY);

            // Detail pane for selected research
            renderDetailPane(graphics);

            // Tooltip on research row hover
            renderResearchTooltip(graphics, mouseX, mouseY);
        }

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
        int x = leftPos + ResearchTableMenu.DETAIL_X;
        int y = topPos + ResearchTableMenu.DETAIL_Y;
        int w = ResearchTableMenu.DETAIL_W;
        int h = ResearchTableMenu.DETAIL_H;

        // Detail pane background
        g.fill(x, y, x + w, y + h, 0xFF1E2233);
        g.fill(x, y, x + w, y + 1, PANEL_BORDER_DARK);
        g.fill(x, y, x + 1, y + h, PANEL_BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER_LIGHT);

        ResearchDefinition def = getSelectedDefinition();
        if (def == null) {
            g.drawString(font, "Select a research entry", x + 4, y + 4, 0xFF666666, false);
            return;
        }

        int textY = y + 4;
        int lineHeight = 10;

        // Line 1: Research name + tier badge
        int nameColor = def.getTier().getColor() | 0xFF000000;
        String nameStr = def.getDisplayName();
        String tierBadge = " [" + def.getTier().getDisplayName() + "]";
        String durationStr = String.format(" - %.0fs", def.getDurationSeconds());

        g.drawString(font, nameStr, x + 4, textY, nameColor, false);
        int textX = x + 4 + font.width(nameStr);
        g.drawString(font, tierBadge, textX, textY, 0xFF888888, false);
        textX += font.width(tierBadge);
        g.drawString(font, durationStr, textX, textY, 0xFFAAB0C0, false);
        textY += lineHeight;

        // Line 2: Description (if available)
        String description = def.getDescription();
        if (description != null && !description.isEmpty()) {
            if (font.width(description) > w - 12) {
                while (font.width(description + "\u2026") > w - 12 && description.length() > 3) {
                    description = description.substring(0, description.length() - 1);
                }
                description += "\u2026";
            }
            g.drawString(font, description, x + 4, textY, 0xFFAAB0C0, false);
            textY += lineHeight;
        }

        // Line 3: Costs summary
        StringBuilder costs = new StringBuilder();
        if (!def.getItemCosts().isEmpty()) {
            for (var cost : def.getItemCosts()) {
                if (costs.length() > 0) costs.append(", ");
                costs.append(cost.getItem().getDescription().getString()).append(" x").append(cost.count());
            }
        }
        if (def.getFluidCost() != null) {
            if (costs.length() > 0) costs.append(" | ");
            costs.append(def.getFluidCost().amount()).append("mB ").append(def.getFluidCost().getFluidName());
        }
        if (costs.length() > 0) {
            String costStr = costs.toString();
            if (font.width(costStr) > w - 12) {
                while (font.width(costStr + "\u2026") > w - 12 && costStr.length() > 3) {
                    costStr = costStr.substring(0, costStr.length() - 1);
                }
                costStr += "\u2026";
            }
            g.drawString(font, costStr, x + 4, textY, 0xFFAAB0C0, false);
        }
    }

    private void renderResearchList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + ResearchTableMenu.LIST_X;
        int y = topPos + ResearchTableMenu.LIST_Y;
        int listW = ResearchTableMenu.LIST_W;
        int rowH = ResearchTableMenu.LIST_ROW_H;
        int visibleRows = ResearchTableMenu.LIST_VISIBLE_ROWS;

        // List background
        graphics.fill(x - 2, y - 2, x + listW + 2, y + visibleRows * rowH + 2, LIST_BG);
        // Border
        graphics.fill(x - 2, y - 2, x + listW + 2, y - 1, PANEL_BORDER_DARK);
        graphics.fill(x - 2, y - 2, x - 1, y + visibleRows * rowH + 2, PANEL_BORDER_DARK);
        graphics.fill(x + listW + 1, y - 2, x + listW + 2, y + visibleRows * rowH + 2, PANEL_BORDER_LIGHT);
        graphics.fill(x - 2, y + visibleRows * rowH + 1, x + listW + 2, y + visibleRows * rowH + 2, PANEL_BORDER_LIGHT);

        for (int i = 0; i < visibleRows; i++) {
            int displayIdx = scrollOffset + i;
            if (displayIdx >= displayRows.size()) break;

            ListRow row = displayRows.get(displayIdx);
            int rowY = y + i * rowH;

            if (row.isHeader()) {
                String headerLabel = "\u25B8 " + row.headerText();
                if (font.width(headerLabel) > listW - 4) {
                    while (font.width(headerLabel + "\u2026") > listW - 4 && headerLabel.length() > 3) {
                        headerLabel = headerLabel.substring(0, headerLabel.length() - 1);
                    }
                    headerLabel += "\u2026";
                }
                graphics.fill(x, rowY, x + listW, rowY + rowH, 0xFF2A2A1A);
                graphics.drawString(font, headerLabel, x + 3, rowY + 3, 0xFFCCAA00, false);
            } else {
                ResearchDefinition def = row.definition();
                boolean locked = !isPrerequisiteMet(def);

                if (def.getId().equals(selectedId)) {
                    graphics.fill(x, rowY, x + listW, rowY + rowH, locked ? 0xFF442222 : 0xFF334488);
                } else if (mouseX >= x && mouseX < x + listW && mouseY >= rowY && mouseY < rowY + rowH) {
                    graphics.fill(x, rowY, x + listW, rowY + rowH, 0xFF2A2A3A);
                }

                String prefix = locked ? "\uD83D\uDD12 " : "";
                String name = def.getDisplayName();
                String label = prefix + name;

                if (font.width(label) > listW - 6) {
                    while (font.width(label + "\u2026") > listW - 6 && label.length() > 3) {
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
            graphics.drawString(font, "\u25B2", x + listW - 8, y - 12, 0xAAAAAAA, false);
        }
        if (scrollOffset + visibleRows < displayRows.size()) {
            graphics.drawString(font, "\u25BC", x + listW - 8, y + visibleRows * rowH + 3, 0xAAAAAA, false);
        }
    }

    private void renderResearchTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + ResearchTableMenu.LIST_X;
        int y = topPos + ResearchTableMenu.LIST_Y;
        int listW = ResearchTableMenu.LIST_W;
        int rowH = ResearchTableMenu.LIST_ROW_H;
        int visibleRows = ResearchTableMenu.LIST_VISIBLE_ROWS;

        if (mouseX < x || mouseX >= x + listW || mouseY < y || mouseY >= y + visibleRows * rowH) {
            return;
        }

        int row = (mouseY - y) / rowH;
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
        int gx = leftPos + ResearchTableMenu.FLUID_GAUGE_X;
        int gy = topPos + ResearchTableMenu.FLUID_GAUGE_Y;
        int gw = ResearchTableMenu.FLUID_GAUGE_W;
        int gh = ResearchTableMenu.FLUID_GAUGE_H;

        if (mouseX < gx - 1 || mouseX >= gx + gw + 1 || mouseY < gy - 1 || mouseY >= gy + gh + 1) {
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
        // List clicks only work in LIST view
        if (currentView == ViewMode.LIST) {
            int x = leftPos + ResearchTableMenu.LIST_X;
            int y = topPos + ResearchTableMenu.LIST_Y;
            int listW = ResearchTableMenu.LIST_W;
            int rowH = ResearchTableMenu.LIST_ROW_H;
            int visibleRows = ResearchTableMenu.LIST_VISIBLE_ROWS;

            if (mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + visibleRows * rowH) {
                int row = (int) ((mouseY - y) / rowH);
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
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // List scrolling only works in LIST view
        if (currentView == ViewMode.LIST) {
            int x = leftPos + ResearchTableMenu.LIST_X;
            int y = topPos + ResearchTableMenu.LIST_Y;
            int listW = ResearchTableMenu.LIST_W;
            int rowH = ResearchTableMenu.LIST_ROW_H;
            int visibleRows = ResearchTableMenu.LIST_VISIBLE_ROWS;

            if (mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + visibleRows * rowH + 14) {
                if (scrollY > 0 && scrollOffset > 0) {
                    scrollOffset--;
                } else if (scrollY < 0 && scrollOffset + visibleRows < displayRows.size()) {
                    scrollOffset++;
                }
                return true;
            }
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
