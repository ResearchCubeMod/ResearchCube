package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.item.CubeItem;
import com.researchcube.menu.ResearchTableMenu;
import com.researchcube.network.CancelResearchPacket;
import com.researchcube.network.StartResearchPacket;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchTier;
import com.researchcube.research.ItemCost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-side screen for the Research Table.
 *
 * Layout (176x166 standard container background):
 *   Left column: Drive slot (0), Cube slot (1)
 *   Center: 3x2 grid for cost inputs (slots 2-7)
 *   Right panel: Research list (scrollable), Start/Cancel button, progress bar
 *   Bottom: Player inventory + hotbar
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {

    private static final ResourceLocation BACKGROUND =
            ResearchCubeMod.rl("textures/gui/research_table.png");

    // Research list state
    private List<ResearchDefinition> availableResearch = new ArrayList<>();
    private int selectedIndex = -1;
    private ResourceLocation selectedId = null; // persists across refreshes
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 3;
    private static final int LIST_X = 120;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 50;
    private static final int ROW_H = 12;

    // Progress bar dimensions (relative to leftPos/topPos)
    private static final int PROGRESS_X = 62;
    private static final int PROGRESS_Y = 58;
    private static final int PROGRESS_W = 54;
    private static final int PROGRESS_H = 6;

    private Button startButton;
    private Button cancelButton;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Start Research button
        startButton = Button.builder(Component.literal("Start"), btn -> onStartResearch())
                .bounds(leftPos + 120, topPos + 58, 24, 16)
                .build();
        addRenderableWidget(startButton);

        // Cancel Research button
        cancelButton = Button.builder(Component.literal("Stop"), btn -> onCancelResearch())
                .bounds(leftPos + 146, topPos + 58, 24, 16)
                .build();
        addRenderableWidget(cancelButton);

        refreshResearchList();
    }

    private void refreshResearchList() {
        availableResearch.clear();

        // Get cube tier from the block entity to filter research
        ResearchTableBlockEntity be = menu.getBlockEntity();
        ItemStack cubeStack = be.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_CUBE);

        if (cubeStack.getItem() instanceof CubeItem cube) {
            ResearchTier cubeTier = cube.getTier();
            availableResearch = new ArrayList<>(ResearchRegistry.getUpToTier(cubeTier));
        } else {
            // No cube — show all research as reference
            availableResearch = new ArrayList<>(ResearchRegistry.getAll());
        }

        // Restore selection by ID so it survives per-tick refreshes
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

    private void onStartResearch() {
        if (selectedIndex < 0 || selectedIndex >= availableResearch.size()) return;
        if (menu.isResearching()) return;

        ResearchDefinition def = availableResearch.get(selectedIndex);
        ResearchTableBlockEntity be = menu.getBlockEntity();

        PacketDistributor.sendToServer(new StartResearchPacket(be.getBlockPos(), def.getId().toString()));
    }

    private void onCancelResearch() {
        if (!menu.isResearching()) return;
        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new CancelResearchPacket(be.getBlockPos()));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshResearchList();

        // Start: active when not researching and a research is selected
        startButton.active = !menu.isResearching() && selectedIndex >= 0 && selectedIndex < availableResearch.size();
        // Cancel: active only while researching
        cancelButton.active = menu.isResearching();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw background texture
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw progress bar if researching
        if (menu.isResearching()) {
            float progress = menu.getScaledProgress();
            int filledWidth = (int) (PROGRESS_W * progress);
            // Draw filled portion (green bar)
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                    leftPos + PROGRESS_X + filledWidth, topPos + PROGRESS_Y + PROGRESS_H,
                    0xFF00CC00);
            // Draw unfilled bg
            graphics.fill(leftPos + PROGRESS_X + filledWidth, topPos + PROGRESS_Y,
                    leftPos + PROGRESS_X + PROGRESS_W, topPos + PROGRESS_Y + PROGRESS_H,
                    0xFF333333);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Research list
        renderResearchList(graphics, mouseX, mouseY);

        // Progress text
        if (menu.isResearching()) {
            int percent = (int) (menu.getScaledProgress() * 100);
            String progressText = percent + "%";
            graphics.drawCenteredString(font, progressText,
                    leftPos + PROGRESS_X + PROGRESS_W / 2, topPos + PROGRESS_Y - 10, 0xFFFFFF);
        }

        // Research row tooltip (hover)
        renderResearchTooltip(graphics, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderResearchList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        // Header
        graphics.drawString(font, "Research:", x, y - 10, 0xFFFFFF, false);

        // List background
        graphics.fill(x - 1, y - 1, x + LIST_W + 1, y + VISIBLE_ROWS * ROW_H + 1, 0xFF222222);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= availableResearch.size()) break;

            ResearchDefinition def = availableResearch.get(idx);
            int rowY = y + i * ROW_H;

            // Highlight selected row
            if (idx == selectedIndex) {
                graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFF4444AA);
            }

            // Use display name (falls back to path if no name field)
            String name = def.getDisplayName();
            if (name.length() > 8) name = name.substring(0, 7) + "\u2026";

            // Color by tier
            int textColor = def.getTier().getColor() | 0xFF000000;
            graphics.drawString(font, name, x + 2, rowY + 2, textColor, false);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawString(font, "\u25B2", x + LIST_W - 8, y - 10, 0xAAAAAA, false);
        }
        if (scrollOffset + VISIBLE_ROWS < availableResearch.size()) {
            graphics.drawString(font, "\u25BC", x + LIST_W - 8, y + VISIBLE_ROWS * ROW_H + 2, 0xAAAAAA, false);
        }
    }

    /**
     * Renders a tooltip when hovering over a research list entry showing
     * tier, duration, item costs, and description.
     */
    private void renderResearchTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX < x || mouseX >= x + LIST_W || mouseY < y || mouseY >= y + VISIBLE_ROWS * ROW_H) {
            return;
        }

        int row = (mouseY - y) / ROW_H;
        int idx = scrollOffset + row;
        if (idx < 0 || idx >= availableResearch.size()) return;

        ResearchDefinition def = availableResearch.get(idx);
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

        // Recipe pool count
        if (def.hasRecipePool()) {
            tooltip.add(Component.literal("Recipes: " + def.getRecipePool().size() + " possible")
                    .withStyle(s -> s.withColor(0x55FFFF)));
        }

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check clicks on research list
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + VISIBLE_ROWS * ROW_H) {
            int row = (int) ((mouseY - y) / ROW_H);
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < availableResearch.size()) {
                selectedIndex = idx;
                selectedId = availableResearch.get(idx).getId();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll research list
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;

        if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + VISIBLE_ROWS * ROW_H + 10) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
            } else if (scrollY < 0 && scrollOffset + VISIBLE_ROWS < availableResearch.size()) {
                scrollOffset++;
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
