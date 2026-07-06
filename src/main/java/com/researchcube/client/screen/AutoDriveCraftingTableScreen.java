package com.researchcube.client.screen;

import com.researchcube.block.AutoDriveCraftingTableBlockEntity;
import com.researchcube.menu.AutoDriveCraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the Auto Drive Crafting Table.
 * The background is drawn procedurally in the mod's AMOLED palette (no dedicated texture):
 * a machine panel with the drive slot, 3x3 grid, a flow arrow and the output slot, plus the
 * player inventory panel. An "IO" button toggles the embedded {@link SideConfigPanel}.
 *
 * Layout (see {@link AutoDriveCraftingTableMenu} constants).
 */
public class AutoDriveCraftingTableScreen extends AbstractContainerScreen<AutoDriveCraftingTableMenu> {

    // ── AMOLED palette (matches SideConfigPanel / the mod's screens) ──
    private static final int PANEL_BG = 0xFF0C0C0C;
    private static final int PANEL_OUTLINE = 0xFF282828;
    private static final int LABEL_COLOR = 0xFFE6EAF5;
    private static final int SUBLABEL_COLOR = 0xFFA3AAC0;
    private static final int FLOW_COLOR = 0xFF848AA0;
    // Subdued hint drawn under an empty drive slot so players know a drive goes there.
    private static final int HINT_COLOR = 0xFF6B7080;

    // Machine + inventory panel bounds (relative to leftPos/topPos)
    private static final int MACHINE_PANEL_X = 8;
    private static final int MACHINE_PANEL_Y = 16;
    private static final int MACHINE_PANEL_W = 184;
    private static final int MACHINE_PANEL_H = 84;
    private static final int INV_PANEL_X = 8;
    private static final int INV_PANEL_Y = 104;
    private static final int INV_PANEL_W = 184;
    private static final int INV_PANEL_H = 98;

    // IO config toggle button (small square, top-right of the machine panel)
    private static final int IO_BTN_X = 176;
    private static final int IO_BTN_Y = 4;
    private static final int IO_BTN_SIZE = 16;

    private Button ioButton;
    private SideConfigPanel sideConfigPanel;

    public AutoDriveCraftingTableScreen(AutoDriveCraftingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = AutoDriveCraftingTableMenu.GUI_WIDTH;
        this.imageHeight = AutoDriveCraftingTableMenu.GUI_HEIGHT;
        this.inventoryLabelX = AutoDriveCraftingTableMenu.INV_X - 1;
        this.inventoryLabelY = AutoDriveCraftingTableMenu.INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        ioButton = Button.builder(Component.literal("IO"), btn -> onToggleSideConfig())
                .bounds(leftPos + IO_BTN_X, topPos + IO_BTN_Y, IO_BTN_SIZE, IO_BTN_SIZE)
                .tooltip(Tooltip.create(Component.translatable("gui.researchcube.io_config.tooltip")))
                .build();
        addRenderableWidget(ioButton);

        if (sideConfigPanel == null) {
            sideConfigPanel = new SideConfigPanel(font, menu.getBlockEntity(), menu.getBlockEntity().getBlockPos());
        }
    }

    private void onToggleSideConfig() {
        if (sideConfigPanel != null) {
            sideConfigPanel.toggleVisible();
        }
    }

    /** Absolute screen X where the side-config overlay is drawn. */
    private int panelX() {
        return SideConfigPanel.clampX(leftPos + imageWidth + 4, leftPos, this.width);
    }

    /** Absolute screen Y where the side-config overlay is drawn. */
    private int panelY() {
        return SideConfigPanel.clampY(topPos + IO_BTN_Y, this.height);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Solid AMOLED backdrop with a subtle outline.
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        drawOutline(g, x, y, imageWidth, imageHeight, PANEL_OUTLINE);

        // Machine + inventory inset panels
        ScreenRenderHelper.drawInsetPanel(g, x + MACHINE_PANEL_X, y + MACHINE_PANEL_Y, MACHINE_PANEL_W, MACHINE_PANEL_H);
        ScreenRenderHelper.drawInsetPanel(g, x + INV_PANEL_X, y + INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H);

        // Slot backgrounds (18x18 frame sits 1px outside the 16x16 item area)
        ScreenRenderHelper.drawSlotBg(g, x + AutoDriveCraftingTableMenu.DRIVE_X - 1, y + AutoDriveCraftingTableMenu.DRIVE_Y - 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ScreenRenderHelper.drawSlotBg(g,
                        x + AutoDriveCraftingTableMenu.GRID_X - 1 + col * 18,
                        y + AutoDriveCraftingTableMenu.GRID_Y - 1 + row * 18);
            }
        }
        ScreenRenderHelper.drawSlotBg(g, x + AutoDriveCraftingTableMenu.OUTPUT_X - 1, y + AutoDriveCraftingTableMenu.OUTPUT_Y - 1);

        // Player inventory + hotbar slot backgrounds
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ScreenRenderHelper.drawSlotBg(g,
                        x + AutoDriveCraftingTableMenu.INV_X - 1 + col * 18,
                        y + AutoDriveCraftingTableMenu.INV_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            ScreenRenderHelper.drawSlotBg(g,
                    x + AutoDriveCraftingTableMenu.INV_X - 1 + col * 18,
                    y + AutoDriveCraftingTableMenu.INV_Y - 1 + 58);
        }

        // Flow arrow between the grid and the output slot
        ScreenRenderHelper.drawArrow(g, x + 128, y + AutoDriveCraftingTableMenu.OUTPUT_Y + 4, FLOW_COLOR);

        // Section labels, centered over their slots
        int labelY = y + AutoDriveCraftingTableMenu.LABEL_Y;
        drawCentered(g, "Drive", x + AutoDriveCraftingTableMenu.DRIVE_X + 8, labelY, SUBLABEL_COLOR);
        drawCentered(g, "Craft Matrix", x + AutoDriveCraftingTableMenu.GRID_X + 27, labelY, SUBLABEL_COLOR);
        drawCentered(g, "Result", x + AutoDriveCraftingTableMenu.OUTPUT_X + 8, labelY, SUBLABEL_COLOR);

        // Subdued "Insert Drive" hint under an empty drive slot. Centered on the drive column
        // but clamped to the machine panel so the wider text never clips the left border.
        if (menu.getSlot(AutoDriveCraftingTableBlockEntity.SLOT_DRIVE).getItem().isEmpty()) {
            String hint = Component.translatable("gui.researchcube.processing.insert_drive").getString();
            int centerX = x + AutoDriveCraftingTableMenu.DRIVE_X + 8;
            int textX = Math.max(x + MACHINE_PANEL_X + 2, centerX - font.width(hint) / 2);
            g.drawString(font, hint, textX, y + AutoDriveCraftingTableMenu.DRIVE_Y + 20, HINT_COLOR, false);
        }
    }

    private void drawCentered(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, 6, LABEL_COLOR, false);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        // Hint when hovering an empty drive slot
        int driveSlotX = this.leftPos + AutoDriveCraftingTableMenu.DRIVE_X;
        int driveSlotY = this.topPos + AutoDriveCraftingTableMenu.DRIVE_Y;
        if (mouseX >= driveSlotX && mouseX < driveSlotX + 16 && mouseY >= driveSlotY && mouseY < driveSlotY + 16) {
            ItemStack driveStack = this.menu.getSlot(AutoDriveCraftingTableBlockEntity.SLOT_DRIVE).getItem();
            if (driveStack.isEmpty()) {
                g.renderTooltip(this.font,
                        Component.translatable("gui.researchcube.processing.insert_drive.tooltip"), mouseX, mouseY);
            }
        }

        // Side-config overlay renders last so it (and its tooltips) sit above everything.
        if (sideConfigPanel != null) {
            sideConfigPanel.render(g, panelX(), panelY(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sideConfigPanel != null && sideConfigPanel.isVisible()
                && sideConfigPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
