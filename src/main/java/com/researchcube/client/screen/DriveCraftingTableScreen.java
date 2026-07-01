package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.menu.DriveCraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the Drive Crafting Table.
 * Static background comes from a texture; only the section labels and
 * tooltips are drawn dynamically.
 *
 * Layout (see DriveCraftingTableMenu constants):
 *   Machine panel: Drive slot | 3x3 crafting grid | arrow | result slot
 *   Inventory panel: player inventory + hotbar
 */
public class DriveCraftingTableScreen extends AbstractContainerScreen<DriveCraftingTableMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchCubeMod.MOD_ID, "textures/gui/drive_crafting_table.png");
    private static final int TEX_W = DriveCraftingTableMenu.GUI_WIDTH;
    private static final int TEX_H = DriveCraftingTableMenu.GUI_HEIGHT;

    private static final int LABEL_COLOR = 0xFFE6EAF5;
    private static final int SUBLABEL_COLOR = 0xFFA3AAC0;

    public DriveCraftingTableScreen(DriveCraftingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = DriveCraftingTableMenu.GUI_WIDTH;
        this.imageHeight = DriveCraftingTableMenu.GUI_HEIGHT;
        this.inventoryLabelX = DriveCraftingTableMenu.INV_X - 1;
        this.inventoryLabelY = DriveCraftingTableMenu.INV_Y - 12;
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

        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        // Section labels, centered over their slots
        int labelY = y + DriveCraftingTableMenu.LABEL_Y;
        drawCentered(graphics, "Drive", x + DriveCraftingTableMenu.DRIVE_X + 8, labelY);
        drawCentered(graphics, "Craft Matrix", x + DriveCraftingTableMenu.GRID_X + 27, labelY);
        drawCentered(graphics, "Result", x + DriveCraftingTableMenu.RESULT_X + 8, labelY);
    }

    private void drawCentered(GuiGraphics g, String text, int centerX, int y) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, SUBLABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Extra tooltip when hovering the empty drive slot
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
}
