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
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * <p>The upper panel hosts three views, switchable via the List/Tree tabs (top right):
 * <ul>
 *   <li><b>LIST</b> — search bar, categorised research list, detail pane</li>
 *   <li><b>TREE</b> — the dependency graph (delegated to {@link ResearchGraphView})</li>
 *   <li><b>PROGRESS</b> — active research info + progress bar (shown automatically
 *       while researching)</li>
 * </ul>
 * The lower half holds the machine panel (drive/cube/idea/costs/fluid/buckets/buttons)
 * on the left and the player inventory on the right. Selection ({@link #selectedId}) is
 * shared between the list and the tree so it persists across tab switches.
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

    private static final int TAB_ACCENT = 0xFF66B3FF;

    // Tree graph viewport (relative to leftPos/topPos), matching the upper panel content area.
    private static final int GRAPH_X = ResearchTableMenu.UPPER_PANEL_X + 8;
    private static final int GRAPH_Y = ResearchTableMenu.UPPER_PANEL_Y + 24;
    private static final int GRAPH_W = ResearchTableMenu.UPPER_PANEL_W - 16;
    private static final int GRAPH_H = ResearchTableMenu.UPPER_PANEL_H - 28;

    // View mode state
    private ViewMode currentView = ViewMode.LIST;
    private ViewMode preferredView = ViewMode.LIST;  // user preference when not researching

    // Research list state
    private List<ResearchDefinition> availableResearch = new ArrayList<>();
    private List<ListRow> displayRows = new ArrayList<>();
    private ResourceLocation selectedId = null;
    private int scrollOffset = 0;

    private Button startButton;
    private Button cancelButton;
    private Button wipeButton;
    private Button listTabButton;
    private Button treeTabButton;
    private EditBox searchBox;
    private String searchFilter = "";

    private ResearchGraphView graphView;

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

        // ── View tabs (top right): List | Tree ──
        listTabButton = Button.builder(Component.literal("List"), btn -> onSelectView(ViewMode.LIST))
                .bounds(leftPos + ResearchTableMenu.TREE_BTN_X, topPos + ResearchTableMenu.TREE_BTN_Y,
                        ResearchTableMenu.TREE_BTN_W, ResearchTableMenu.TREE_BTN_H)
                .build();
        addRenderableWidget(listTabButton);

        treeTabButton = Button.builder(Component.literal("Tree"), btn -> onSelectView(ViewMode.TREE))
                .bounds(leftPos + ResearchTableMenu.LIST_BTN_X, topPos + ResearchTableMenu.LIST_BTN_Y,
                        ResearchTableMenu.LIST_BTN_W, ResearchTableMenu.LIST_BTN_H)
                .build();
        addRenderableWidget(treeTabButton);

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

        // Tree graph component (persists across re-inits so pan/zoom/selection survive resizes)
        if (graphView == null) {
            graphView = new ResearchGraphView(font, menu);
        }
        graphView.setViewport(leftPos + GRAPH_X, topPos + GRAPH_Y, GRAPH_W, GRAPH_H);
        graphView.buildGraph();
        graphView.fitToViewport();
        graphView.setSelectedId(selectedId);

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
    }

    // ══════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════

    private void onStartResearch() {
        if (menu.isResearching()) return;
        ResearchDefinition def = getSelectedDefinition();
        if (def == null || !ResearchRequirements.canStart(menu, def)) return;

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

    /** Switch the browsing view (List/Tree) and remember it as the user's preference. */
    private void onSelectView(ViewMode view) {
        preferredView = view;
        if (view == ViewMode.TREE) {
            graphView.setSelectedId(selectedId);
        }
        updateViewMode();
    }

    /**
     * Updates the view mode based on research state and user preference.
     * While researching (and the active definition is synced) the PROGRESS view takes
     * over; otherwise the user's preferred browsing view (LIST or TREE) is shown.
     */
    private void updateViewMode() {
        if (menu.isResearching() && menu.getBlockEntity().getActiveDefinition() != null) {
            currentView = ViewMode.PROGRESS;
        } else {
            currentView = preferredView;
        }

        boolean browsing = (currentView != ViewMode.PROGRESS);
        searchBox.visible = (currentView == ViewMode.LIST);
        listTabButton.visible = browsing;
        treeTabButton.visible = browsing;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshResearchList();
        graphView.tick();
        graphView.setSelectedId(selectedId);  // list is the source of truth for shared selection
        updateViewMode();

        // Start button: only active if research is selected AND all requirements are met
        ResearchDefinition selectedDef = getSelectedDefinition();
        boolean canStart = !menu.isResearching() && selectedDef != null && ResearchRequirements.canStart(menu, selectedDef);
        startButton.active = canStart;
        cancelButton.active = menu.isResearching();
        wipeButton.active = menu.getFluidAmount() > 0;
    }

    @Nullable
    private ResearchDefinition getSelectedDefinition() {
        return selectedId == null ? null : ResearchRegistry.get(selectedId);
    }

    private void selectResearch(ResourceLocation id) {
        selectedId = id;
        graphView.setSelectedId(id);
    }

    private void renderIdeaChipOverlay(GuiGraphics g, int sx, int sy) {
        ResearchDefinition selected = getSelectedDefinition();
        if (selected == null || selected.getIdeaChip().isEmpty()) {
            g.fill(sx, sy, sx + 16, sy + 16, 0x88000000);
        } else if (!ResearchRequirements.ideaChipSatisfied(menu, selected)) {
            int x0 = sx - 1;
            int y0 = sy - 1;
            g.fill(x0, y0, x0 + 18, y0 + 1, 0xFFFF3333);
            g.fill(x0, y0 + 17, x0 + 18, y0 + 18, 0xFFFF3333);
            g.fill(x0, y0, x0 + 1, y0 + 18, 0xFFFF3333);
            g.fill(x0 + 17, y0, x0 + 18, y0 + 18, 0xFFFF3333);
        }
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
        switch (currentView) {
            case LIST -> { /* list + detail pane are drawn in render() */ }
            case TREE -> {
                graphView.setViewport(x + GRAPH_X, y + GRAPH_Y, GRAPH_W, GRAPH_H);
                graphView.render(g, mouseX, mouseY);
            }
            case PROGRESS -> renderProgressView(g, x, y);
        }

        // ── Top control bar header (LIST uses the search box instead) ──
        int hx = x + ResearchTableMenu.SEARCH_X;
        int hy = y + ResearchTableMenu.SEARCH_Y + 3;
        if (currentView == ViewMode.TREE) {
            g.drawString(font, "Research Tree", hx, hy, 0xFFE6EAF5, false);
            graphView.renderLegend(g, hx + font.width("Research Tree") + 14, hy);
        } else if (currentView == ViewMode.PROGRESS) {
            g.drawString(font, "Research Station", hx, hy, 0xFFE6EAF5, false);
        }

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
        int fluidType = menu.getFluidType();
        int fluidColor = fluidType > 0 ? ModFluids.getFluidColor(fluidType) : 0;
        ScreenRenderHelper.drawFluidGauge(g,
                x + ResearchTableMenu.FLUID_GAUGE_X, y + ResearchTableMenu.FLUID_GAUGE_Y,
                ResearchTableMenu.FLUID_GAUGE_W, ResearchTableMenu.FLUID_GAUGE_H,
                menu.getFluidAmount(), ResearchTableBlockEntity.TANK_CAPACITY, fluidColor);
    }

    /**
     * Renders the progress view showing active research details.
     * Shows: name, tier, category, description, costs, progress bar.
     */
    private void renderProgressView(GuiGraphics g, int x, int y) {
        int vx = x + ResearchTableMenu.PROGRESS_VIEW_X;
        int vy = y + ResearchTableMenu.PROGRESS_VIEW_Y;
        int vw = ResearchTableMenu.PROGRESS_VIEW_W;
        int vh = ResearchTableMenu.PROGRESS_VIEW_H;

        ScreenRenderHelper.drawBevelBox(g, vx, vy, vw, vh, 0xFF1A1E2A);

        ResearchTableBlockEntity be = menu.getBlockEntity();
        ResearchDefinition activeDef = be.getActiveDefinition();

        if (activeDef == null) {
            g.drawString(font, "No active research", vx + 8, vy + 8, 0xFF666666, false);
            return;
        }

        int textY = vy + 6;
        int lineHeight = 11;
        int tierColor = activeDef.getTier().getColor() | 0xFF000000;

        // Header: Research name with tier badge
        String nameStr = activeDef.getDisplayName();
        String tierBadge = " [" + activeDef.getTier().getDisplayName() + "]";
        g.drawString(font, nameStr, vx + 8, textY, tierColor, false);
        g.drawString(font, tierBadge, vx + 8 + font.width(nameStr), textY, 0xFF888888, false);
        textY += lineHeight + 2;

        // Category line
        if (activeDef.getCategory() != null && !activeDef.getCategory().isEmpty()) {
            g.drawString(font, "Category: " + activeDef.getCategory(), vx + 8, textY, 0xFFCCAA00, false);
            textY += lineHeight;
        }

        // Description/flavor text (supports multi-line wrapping)
        if (activeDef.getDescription() != null && !activeDef.getDescription().isEmpty()) {
            String desc = activeDef.getDescription();
            int maxWidth = vw - 16;

            List<String> lines = new ArrayList<>();
            while (!desc.isEmpty() && lines.size() < 2) {
                if (font.width(desc) <= maxWidth) {
                    lines.add(desc);
                    break;
                }
                int cutIdx = desc.length();
                while (cutIdx > 0 && font.width(desc.substring(0, cutIdx)) > maxWidth) {
                    cutIdx--;
                }
                int spaceIdx = desc.lastIndexOf(' ', cutIdx);
                if (spaceIdx > cutIdx / 2) {
                    cutIdx = spaceIdx;
                }
                lines.add(desc.substring(0, cutIdx).trim());
                desc = desc.substring(cutIdx).trim();
            }
            if (!desc.isEmpty() && lines.size() == 2) {
                String lastLine = lines.get(1);
                while (font.width(lastLine + "...") > maxWidth && lastLine.length() > 3) {
                    lastLine = lastLine.substring(0, lastLine.length() - 1);
                }
                lines.set(1, lastLine + "...");
            }

            for (String line : lines) {
                g.drawString(font, line, vx + 8, textY, 0xFFAAB0C0, false);
                textY += lineHeight - 1;
            }
        }

        // Costs summary (compact, single line)
        StringBuilder costsStr = new StringBuilder();
        if (!activeDef.getItemCosts().isEmpty()) {
            for (var cost : activeDef.getItemCosts()) {
                if (costsStr.length() > 0) costsStr.append(", ");
                costsStr.append(cost.getItem().getDescription().getString()).append(" x").append(cost.count());
            }
        }
        if (activeDef.getFluidCost() != null) {
            if (costsStr.length() > 0) costsStr.append(" | ");
            costsStr.append(activeDef.getFluidCost().amount()).append("mB ").append(activeDef.getFluidCost().getFluidName());
        }
        if (costsStr.length() > 0) {
            String costs = "Costs: " + costsStr;
            if (font.width(costs) > vw - 16) {
                while (font.width(costs + "...") > vw - 16 && costs.length() > 10) {
                    costs = costs.substring(0, costs.length() - 1);
                }
                costs += "...";
            }
            g.drawString(font, costs, vx + 8, textY, 0xFF77AADD, false);
            textY += lineHeight;
        }

        // Progress info
        textY += 4;
        float progress = Math.min(1.0f, menu.getScaledProgress());
        int percent = (int) (progress * 100);
        float totalSeconds = activeDef.getDurationSeconds();
        float remainingSeconds = totalSeconds * (1.0f - progress);
        int mins = (int) (remainingSeconds / 60);
        int secs = (int) (remainingSeconds % 60);

        String progressText = String.format("Progress: %d%%  |  Time remaining: %d:%02d", percent, mins, secs);
        g.drawString(font, progressText, vx + 8, textY, 0xFFE6EAF5, false);

        // Progress bar
        int barX = x + ResearchTableMenu.PROGRESS_BAR_X;
        int barY = y + ResearchTableMenu.PROGRESS_BAR_Y;
        int barW = ResearchTableMenu.PROGRESS_BAR_W;
        int barH = ResearchTableMenu.PROGRESS_BAR_H;

        ScreenRenderHelper.drawBevelBox(g, barX, barY, barW, barH, ScreenRenderHelper.GAUGE_BG);

        // Filled portion with gradient effect
        int filledWidth = Math.round((barW - 2) * progress);
        if (filledWidth > 0) {
            g.fill(barX + 1, barY + 1, barX + 1 + filledWidth, barY + barH - 1, tierColor);
            int highlightColor = (tierColor & 0x00FFFFFF) | 0x44FFFFFF;
            g.fill(barX + 1, barY + 1, barX + 1 + filledWidth, barY + 2, highlightColor);
        }

        // Percentage text on bar (centered)
        String pctStr = percent + "%";
        int textWidth = font.width(pctStr);
        g.drawString(font, pctStr, barX + (barW - textWidth) / 2, barY + 2, 0xFFFFFFFF, true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // View-specific content
        if (currentView == ViewMode.LIST) {
            renderResearchList(graphics, mouseX, mouseY);
            renderDetailPane(graphics);
            renderResearchTooltip(graphics, mouseX, mouseY);
        }

        // Active-tab accent (drawn above the tab buttons)
        renderTabHighlight(graphics);

        // Tree node tooltip
        if (currentView == ViewMode.TREE) {
            graphView.renderTooltip(graphics, mouseX, mouseY);
        }

        // Machine-panel tooltips (always available)
        renderFluidGaugeTooltip(graphics, mouseX, mouseY);
        renderIdeaChipTooltip(graphics, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderTabHighlight(GuiGraphics g) {
        if (currentView == ViewMode.PROGRESS) return;
        Button active = (currentView == ViewMode.TREE) ? treeTabButton : listTabButton;
        if (!active.visible) return;
        int ax = active.getX();
        int ay = active.getY();
        int aw = active.getWidth();
        int ah = active.getHeight();
        g.fill(ax, ay + ah - 2, ax + aw, ay + ah, TAB_ACCENT);
    }

    /**
     * Renders the detail pane below the research list showing info about the selected research.
     */
    private void renderDetailPane(GuiGraphics g) {
        int x = leftPos + ResearchTableMenu.DETAIL_X;
        int y = topPos + ResearchTableMenu.DETAIL_Y;
        int w = ResearchTableMenu.DETAIL_W;
        int h = ResearchTableMenu.DETAIL_H;

        // Detail pane background - use same padding as list (-2/+2) for alignment
        ScreenRenderHelper.drawBevelBox(g, x - 2, y, w + 4, h, 0xFF1E2233);

        ResearchDefinition def = getSelectedDefinition();
        if (def == null) {
            g.drawString(font, "Select a research entry", x + 2, y + 4, 0xFF666666, false);
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
                while (font.width(description + "…") > w - 12 && description.length() > 3) {
                    description = description.substring(0, description.length() - 1);
                }
                description += "…";
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
                while (font.width(costStr + "…") > w - 12 && costStr.length() > 3) {
                    costStr = costStr.substring(0, costStr.length() - 1);
                }
                costStr += "…";
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

        // List background + border
        ScreenRenderHelper.drawBevelBox(graphics, x - 2, y - 2, listW + 4, visibleRows * rowH + 4, ScreenRenderHelper.LIST_BG);

        for (int i = 0; i < visibleRows; i++) {
            int displayIdx = scrollOffset + i;
            if (displayIdx >= displayRows.size()) break;

            ListRow row = displayRows.get(displayIdx);
            int rowY = y + i * rowH;

            if (row.isHeader()) {
                String headerLabel = "▸ " + row.headerText();
                if (font.width(headerLabel) > listW - 4) {
                    while (font.width(headerLabel + "…") > listW - 4 && headerLabel.length() > 3) {
                        headerLabel = headerLabel.substring(0, headerLabel.length() - 1);
                    }
                    headerLabel += "…";
                }
                graphics.fill(x, rowY, x + listW, rowY + rowH, 0xFF2A2A1A);
                graphics.drawString(font, headerLabel, x + 3, rowY + 3, 0xFFCCAA00, false);
            } else {
                ResearchDefinition def = row.definition();
                boolean locked = !ResearchRequirements.prereqMet(menu, def);

                if (def.getId().equals(selectedId)) {
                    graphics.fill(x, rowY, x + listW, rowY + rowH, locked ? 0xFF442222 : 0xFF334488);
                } else if (mouseX >= x && mouseX < x + listW && mouseY >= rowY && mouseY < rowY + rowH) {
                    graphics.fill(x, rowY, x + listW, rowY + rowH, 0xFF2A2A3A);
                }

                String prefix = locked ? "🔒 " : "";
                String name = def.getDisplayName();
                String label = prefix + name;

                if (font.width(label) > listW - 6) {
                    while (font.width(label + "…") > listW - 6 && label.length() > 3) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "…";
                }

                int textColor = locked ? 0xFF666666 : (def.getTier().getColor() | 0xFF000000);
                graphics.drawString(font, label, x + 3, rowY + 3, textColor, false);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawString(font, "▲", x + listW - 8, y - 12, 0xAAAAAAA, false);
        }
        if (scrollOffset + visibleRows < displayRows.size()) {
            graphics.drawString(font, "▼", x + listW - 8, y + visibleRows * rowH + 3, 0xAAAAAA, false);
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
                tooltip.add(Component.literal("  • " + cost.getItem().getDescription().getString() + " x" + cost.count())
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
            boolean satisfied = ResearchRequirements.ideaChipSatisfied(menu, def);
            String icon = satisfied ? "✔" : "✘";
            int color = satisfied ? 0x55FF55 : 0xFF5555;
            tooltip.add(Component.literal("Idea Chip: " + icon + " " + chip.getHoverName().getString())
                    .withStyle(s -> s.withColor(color)));
        }

        if (!(def.getPrerequisites() instanceof NonePrerequisite)) {
            boolean met = ResearchRequirements.prereqMet(menu, def);
            String prereqIcon = met ? "✔" : "✘";
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

        ItemStack slotStack = menu.getSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP).getItem();
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
        if (currentView == ViewMode.TREE) {
            if (graphView.mouseClicked(mouseX, mouseY, button)) {
                selectResearch(graphView.getSelectedId());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

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
                        selectResearch(listRow.definition().getId());
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (currentView == ViewMode.TREE && graphView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (currentView == ViewMode.TREE) {
            graphView.mouseReleased(button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentView == ViewMode.TREE) {
            if (graphView.mouseScrolled(mouseX, mouseY, scrollY)) return true;
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

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
        // The upper-panel header (search box / "Research Tree" / "Research Station") is drawn per
        // view in renderBg, so the panel title is intentionally not repeated here.
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFE6EAF5, false);
    }
}
