package com.researchcube.client.screen;

import com.researchcube.menu.DriveCraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
    private static final int PANEL_BG = 0xFF444A5E;
    private static final int PANEL_INNER = 0xFF2A2E3A;
    private static final int PANEL_BORDER_LIGHT = 0xFF8F99B8;
    private static final int PANEL_BORDER_DARK = 0xFF14161C;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF272830;
    private static final int ARROW_COLOR = 0xFFB9C0D8;
    private static final int LABEL_COLOR = 0xFFE6EAF5;
    private static final int SUBLABEL_COLOR = 0xFFA3AAC0;

    public DriveCraftingTableScreen(DriveCraftingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 246;
        this.imageHeight = 200;
        this.inventoryLabelX = 10;
        this.inventoryLabelY = 104;
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

        // ── Machine and inventory panels ──
        int panelX = x + 8;
        int panelY = y + 18;
        int panelW = w - 16;
        int panelH = 84;
        drawBevelledPanel(graphics, panelX, panelY, panelW, panelH);
        drawBevelledPanel(graphics, x + 8, y + 104, w - 16, 88);

        // ── Draw all slots ──
        drawSlot(graphics, x + DriveCraftingTableMenu.DRIVE_X, y + DriveCraftingTableMenu.DRIVE_Y);

        // 3x3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(graphics, x + DriveCraftingTableMenu.GRID_X + col * 18, y + DriveCraftingTableMenu.GRID_Y + row * 18);
            }
        }

        // Result slot
        drawResultSlot(graphics, x + DriveCraftingTableMenu.RESULT_X, y + DriveCraftingTableMenu.RESULT_Y);

        // ── Arrow between grid and result ──
        drawArrow(graphics, x + 170, y + 45);

        // ── Drive info label ──
        graphics.drawString(this.font, "Drive", x + 30, y + 24, SUBLABEL_COLOR, false);
        graphics.drawString(this.font, "Craft Matrix", x + 88, y + 20, SUBLABEL_COLOR, false);
        graphics.drawString(this.font, "Result", x + 192, y + 24, SUBLABEL_COLOR, false);
        graphics.drawString(this.font, "craft", x + 170, y + 34, 0xFF9EA8C8, false);

        // Player inventory slots visual
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + DriveCraftingTableMenu.INV_X + col * 18, y + DriveCraftingTableMenu.INV_Y + row * 18);
            }
        }
        // Hotbar slots
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + DriveCraftingTableMenu.INV_X + col * 18, y + DriveCraftingTableMenu.INV_Y + 58);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Extra tooltip when hovering the drive slot: show stored recipe IDs
        int driveSlotX = this.leftPos + DriveCraftingTableMenu.DRIVE_X;
        int driveSlotY = this.topPos + DriveCraftingTableMenu.DRIVE_Y;
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
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_INNER);
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
        g.fill(x - 1, y - 1, x + 17, y, PANEL_BORDER_DARK);
        g.fill(x - 1, y - 1, x, y + 17, PANEL_BORDER_DARK);
    }

    private void drawResultSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 3, y - 3, x + 19, y + 19, 0xFF7B8451);
        g.fill(x - 2, y - 2, x + 18, y + 18, SLOT_BG);
        g.fill(x, y, x + 16, y + 16, SLOT_INNER);
    }

    private void drawArrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 3, x + 18, y + 5, ARROW_COLOR);
        g.fill(x + 12, y + 1, x + 18, y + 2, ARROW_COLOR);
        g.fill(x + 13, y + 2, x + 18, y + 3, ARROW_COLOR);
        g.fill(x + 14, y + 3, x + 18, y + 5, ARROW_COLOR);
        g.fill(x + 13, y + 5, x + 18, y + 6, ARROW_COLOR);
        g.fill(x + 12, y + 6, x + 18, y + 7, ARROW_COLOR);
    }
}
