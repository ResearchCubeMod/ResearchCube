package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.DriveCraftingTableBlockEntity;
import com.researchcube.item.DriveItem;
import com.researchcube.menu.DriveCraftingTableMenu;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.util.NbtUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Screen for the Drive Crafting Table.
 * Programmatic background rendering (no texture dependency), matching the mod's visual style.
 *
 * Layout:
 *   Left: Drive slot with label
 *   Center: 3x3 crafting grid
 *   Right: Arrow + result slot
 *   Bottom: Player inventory
 */
public class DriveCraftingTableScreen extends AbstractContainerScreen<DriveCraftingTableMenu> {

    // ── Colors (matching ResearchTableScreen style) ──
    private static final int BG_OUTER = 0xFFC6C6C6;
    private static final int PANEL_BG = 0xFF3A3A3A;
    private static final int PANEL_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF373737;
    private static final int ARROW_COLOR = 0xFFAAAAAA;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int SUBLABEL_COLOR = 0xFF888888;

    public DriveCraftingTableScreen(DriveCraftingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // ── Outer background ──
        graphics.fill(x, y, x + w, y + h, BG_OUTER);
        // Dark border
        graphics.fill(x, y, x + w, y + 1, PANEL_BORDER_DARK);
        graphics.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER_DARK);
        graphics.fill(x, y, x + 1, y + h, PANEL_BORDER_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER_DARK);

        // ── Top panel (crafting area) ──
        int panelX = x + 4;
        int panelY = y + 4;
        int panelW = w - 8;
        int panelH = 74;
        drawBevelledPanel(graphics, panelX, panelY, panelW, panelH);

        // ── Draw all slots ──
        // Drive slot at (15, 35)
        drawSlot(graphics, x + 15, y + 35);

        // 3x3 grid at (44, 17)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(graphics, x + 44 + col * 18, y + 17 + row * 18);
            }
        }

        // Result slot at (134, 35) — slightly larger appearance
        drawResultSlot(graphics, x + 134, y + 35);

        // ── Arrow between grid and result ──
        drawArrow(graphics, x + 106, y + 37);

        // ── Drive info label ──
        String driveLabel = "DRIVE";
        graphics.drawString(this.font, driveLabel,
                x + 15 + (16 - this.font.width(driveLabel)) / 2,
                y + 25, SUBLABEL_COLOR, false);

        // ── Show recipe ID info when a recipe is matched ──
        DriveCraftingRecipe recipe = this.menu.getCurrentRecipe();
        if (recipe != null) {
            String recipeId = recipe.getRequiredRecipeId();
            // Truncate if too long
            String display = recipeId;
            if (this.font.width(display) > panelW - 8) {
                // Show just the path part
                int lastColon = display.lastIndexOf(':');
                if (lastColon >= 0) display = display.substring(lastColon + 1);
            }
            if (this.font.width(display) > panelW - 8) {
                display = display.substring(0, Math.min(display.length(), 20)) + "...";
            }
        }

        // ── Player inventory area ──
        int invPanelY = y + 80;
        int invPanelH = h - 84;
        drawBevelledPanel(graphics, panelX, invPanelY, panelW, invPanelH);

        // Player inventory slots visual
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        // Hotbar slots
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 8 + col * 18, y + 142);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Extra tooltip when hovering the drive slot: show stored recipe IDs
        int driveSlotX = this.leftPos + 15;
        int driveSlotY = this.topPos + 35;
        if (mouseX >= driveSlotX && mouseX < driveSlotX + 16 && mouseY >= driveSlotY && mouseY < driveSlotY + 16) {
            ItemStack driveStack = this.menu.getSlot(0).getItem();
            if (driveStack.isEmpty()) {
                graphics.renderTooltip(this.font, Component.literal("Insert a Drive with researched recipes"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, 6, LABEL_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    // ── Drawing helpers ──

    private void drawBevelledPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        // Top and left borders (lighter)
        g.fill(x, y, x + w, y + 1, PANEL_BORDER_LIGHT);
        g.fill(x, y, x + 1, y + h, PANEL_BORDER_LIGHT);
        // Bottom and right borders (darker)
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER_DARK);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        // Outer slot border
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        // Inner slot background
        g.fill(x, y, x + 16, y + 16, SLOT_INNER);
    }

    private void drawResultSlot(GuiGraphics g, int x, int y) {
        // Larger result slot with highlight border
        g.fill(x - 3, y - 3, x + 19, y + 19, 0xFF5A5A2A); // Gold-ish border
        g.fill(x - 2, y - 2, x + 18, y + 18, SLOT_BG);
        g.fill(x, y, x + 16, y + 16, SLOT_INNER);
    }

    private void drawArrow(GuiGraphics g, int x, int y) {
        // Simple right-pointing arrow: ▶
        // Arrow body (horizontal line)
        g.fill(x, y + 3, x + 16, y + 5, ARROW_COLOR);
        // Arrow head
        g.fill(x + 12, y + 1, x + 16, y + 2, ARROW_COLOR);
        g.fill(x + 13, y + 2, x + 16, y + 3, ARROW_COLOR);
        g.fill(x + 14, y + 3, x + 16, y + 5, ARROW_COLOR);
        g.fill(x + 13, y + 5, x + 16, y + 6, ARROW_COLOR);
        g.fill(x + 12, y + 6, x + 16, y + 7, ARROW_COLOR);
    }
}
