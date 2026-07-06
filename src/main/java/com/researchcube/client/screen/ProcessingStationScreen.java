package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.menu.ProcessingStationMenu;
import com.researchcube.network.InteractTankPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screen for the Processing Station.
 * Static background comes from a texture; fluid gauges, the progress bar
 * fill, labels and tooltips are drawn dynamically on top.
 *
 * Layout (see ProcessingStationMenu constants):
 *   Machine panel: 4x4 inputs | tanks + progress + drive slot | 2x4 outputs
 *   Inventory panel: player inventory + hotbar
 */
public class ProcessingStationScreen extends AbstractContainerScreen<ProcessingStationMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchCubeMod.MOD_ID, "textures/gui/processing_station.png");
    private static final int TEX_W = ProcessingStationMenu.GUI_WIDTH;
    private static final int TEX_H = ProcessingStationMenu.GUI_HEIGHT;

    // Colors
    private static final int LABEL_COLOR = 0xFFE6EAF5;
    private static final int SUBLABEL_COLOR = 0xFFA3AAC0;
    private static final int FLOW_COLOR = 0xFF848AA0;
    private static final int PROGRESS_FG = 0xFF22C55E;
    private static final int FLUID_IN1_COLOR = 0xFF4F9BFF;
    private static final int FLUID_IN2_COLOR = 0xFFB16CFF;
    private static final int FLUID_OUT_COLOR = 0xFFFFB547;

    // Header label row inside the machine panel
    private static final int LABEL_Y = 24;

    // Control column (centered between input and output grids)
    private static final int CONTROL_CENTER_X = 142;

    // Fluid gauges
    private static final int TANK_IN1_X = 115;
    private static final int TANK_IN2_X = 135;
    private static final int TANK_OUT_X = 155;
    private static final int TANK_Y = 36;
    private static final int TANK_W = 14;
    private static final int TANK_H = 32;

    // Progress bar
    private static final int PROGRESS_X = 108;
    private static final int PROGRESS_Y = 76;
    private static final int PROGRESS_W = 68;
    private static final int PROGRESS_H = 8;

    // IO config toggle button (small square, top-right of the machine panel)
    private static final int IO_BTN_X = 232;
    private static final int IO_BTN_Y = 4;
    private static final int IO_BTN_SIZE = 16;

    // Status text baseline
    private static final int STATUS_Y = 107;
    // Max width the status text may occupy: the control column between the input and output
    // grids (roughly x=108..176). Anything wider is wrapped to two lines (or trimmed) so a long
    // datapack-translated string can never bleed onto the slot grids.
    private static final int STATUS_MAX_W = 66;
    private static final int STATUS_LINE_H = 9;

    // AMOLED palette baked into the background texture (verified by sampling the PNG).
    // Panel background fills the space behind the slots; the baked slot frames use a dark
    // top/left bevel, a mid interior, and a light bottom/right bevel.
    private static final int PANEL_BG = 0xFF0C0C0C;
    private static final int SLOT_BEVEL_DARK = 0xFF141414;   // baked slot top/left + border
    private static final int SLOT_INTERIOR = 0xFF1E1E1E;     // baked slot 16x16 fill
    private static final int SLOT_BEVEL_LIGHT = 0xFF373737;  // baked slot bottom/right

    // The old drive-slot frame is still baked into the texture at pixel rect (179,79)-(196,96)
    // (18x18), left over from when the drive slot lived at (180,80). The slot moved to
    // (DRIVE_SLOT_X, DRIVE_SLOT_Y), so we paint over this dead frame with the panel bg below.
    private static final int GHOST_SLOT_X = 179;
    private static final int GHOST_SLOT_Y = 79;
    private static final int GHOST_SLOT_SIZE = 18;

    private Button ioButton;
    private SideConfigPanel sideConfigPanel;

    public ProcessingStationScreen(ProcessingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = ProcessingStationMenu.GUI_WIDTH;
        this.imageHeight = ProcessingStationMenu.GUI_HEIGHT;
        this.inventoryLabelX = ProcessingStationMenu.PLAYER_INV_X - 1;
        this.inventoryLabelY = ProcessingStationMenu.PLAYER_INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        ioButton = Button.builder(Component.literal("IO"), btn -> onToggleSideConfig())
                .bounds(leftPos + IO_BTN_X, topPos + IO_BTN_Y, IO_BTN_SIZE, IO_BTN_SIZE)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.researchcube.io_config.tooltip")))
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

    /**
     * Absolute screen X where the side-config overlay is drawn: right of the GUI when it
     * fits, otherwise left of it, otherwise clamped to the screen edge.
     */
    private int panelX() {
        return SideConfigPanel.clampX(leftPos + imageWidth + 4, leftPos, this.width);
    }

    /** Absolute screen Y where the side-config overlay is drawn (clamped to the screen). */
    private int panelY() {
        return SideConfigPanel.clampY(topPos + IO_BTN_Y, this.height);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        // Paint over the ghost drive-slot frame still baked into the texture at its old
        // position, using the surrounding panel background so it reads as empty panel.
        g.fill(x + GHOST_SLOT_X, y + GHOST_SLOT_Y,
                x + GHOST_SLOT_X + GHOST_SLOT_SIZE, y + GHOST_SLOT_Y + GHOST_SLOT_SIZE, PANEL_BG);

        // Header labels, centered over their sections
        int labelY = y + LABEL_Y;
        drawCentered(g, "Inputs", x + ProcessingStationMenu.INPUT_GRID_X + 35, labelY, SUBLABEL_COLOR);
        drawCentered(g, "I1", x + TANK_IN1_X + 7, labelY, FLUID_IN1_COLOR);
        drawCentered(g, "I2", x + TANK_IN2_X + 7, labelY, FLUID_IN2_COLOR);
        drawCentered(g, "O", x + TANK_OUT_X + 7, labelY, FLUID_OUT_COLOR);
        drawCentered(g, "Outputs", x + ProcessingStationMenu.OUTPUT_GRID_X + 17, labelY, SUBLABEL_COLOR);

        // Flow indicators between the sections
        drawCentered(g, ">>", x + 99, y + 66, FLOW_COLOR);
        drawCentered(g, ">>", x + 190, y + 66, FLOW_COLOR);

        // Fluid gauges: draw each tank's real fluid texture (tinted, tiled), falling back to a
        // flat color if the sprite is missing. Contents are kept live by SyncTankPacket, so
        // externally piped-in fluid shows its true type and amount here too.
        int capacity = ProcessingStationBlockEntity.TANK_CAPACITY;
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_IN1_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidInput1Stack(), capacity, FLUID_IN1_COLOR);
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_IN2_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidInput2Stack(), capacity, FLUID_IN2_COLOR);
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_OUT_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidOutputStack(), capacity, FLUID_OUT_COLOR);

        // Progress bar fill (background is baked into the texture)
        if (menu.isProcessing()) {
            int fillWidth = (int) (PROGRESS_W * menu.getProgress());
            if (fillWidth > 0) {
                int px = x + PROGRESS_X;
                int py = y + PROGRESS_Y;
                g.fill(px, py, px + fillWidth, py + PROGRESS_H, PROGRESS_FG);
                g.fill(px, py, px + fillWidth, py + 1, 0x55FFFFFF);
            }
        }

        // Drive slot frame: drawn in code because the slot moved off its baked-in texture
        // position. The slot's item area is 16x16 at (DRIVE_SLOT_X, DRIVE_SLOT_Y); the 18x18
        // bevel frame sits 1px outside it. Uses the baked slot palette (dark bevel / mid
        // interior / light bevel) so it is indistinguishable from the texture's own slots.
        ScreenRenderHelper.drawSlotBg(g, x + ProcessingStationMenu.DRIVE_SLOT_X - 1,
                y + ProcessingStationMenu.DRIVE_SLOT_Y - 1,
                SLOT_BEVEL_DARK, SLOT_INTERIOR, SLOT_BEVEL_DARK, SLOT_BEVEL_LIGHT);
    }

    private void drawCentered(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, 6, LABEL_COLOR, false);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);

        // Processing status under the progress bar / drive slot
        if (menu.isProcessing()) {
            int percent = (int) (menu.getProgress() * 100);
            drawStatusText(g, Component.literal("Processing " + percent + "%"), 0xFF4ADE80);
        } else if (menu.getBlockEntity().getInventory()
                .getStackInSlot(ProcessingStationBlockEntity.SLOT_DRIVE).isEmpty()) {
            // Idle without a drive: recipes are research-locked, so hint at the requirement
            drawStatusText(g, Component.translatable("gui.researchcube.processing.insert_drive"), 0xFFCCAA00);
        } else {
            // Idle with a drive: the machine auto-starts on valid inputs, so prompt for them.
            drawStatusText(g, Component.translatable("gui.researchcube.processing.insert_inputs"), 0xFFB0B0B0);
        }
    }

    /**
     * Draw a status line centered on the control column. If it fits within
     * {@link #STATUS_MAX_W} it renders as one centered line; otherwise it wraps to (up to) two
     * centered lines via {@code font.split}. A third overflow line, if any, is dropped so the
     * text can never grow into the slot grids below, so long datapack translations stay contained.
     */
    private void drawStatusText(GuiGraphics g, Component status, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(status, STATUS_MAX_W);
        // Vertically center the (1 or 2) drawn lines on STATUS_Y so a two-line status still sits
        // in the same band as a one-line one.
        int drawn = Math.min(lines.size(), 2);
        int startY = STATUS_Y - (drawn - 1) * STATUS_LINE_H / 2;
        for (int i = 0; i < drawn; i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            int lineX = CONTROL_CENTER_X - font.width(line) / 2;
            g.drawString(font, line, lineX, startY + i * STATUS_LINE_H, color, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderFluidTooltips(guiGraphics, mouseX, mouseY);
        renderProgressTooltip(guiGraphics, mouseX, mouseY);

        renderTooltip(guiGraphics, mouseX, mouseY);

        // Side-config overlay renders last so it (and its tooltips) sit above everything.
        if (sideConfigPanel != null) {
            sideConfigPanel.render(guiGraphics, panelX(), panelY(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sideConfigPanel != null && sideConfigPanel.isVisible()
                && sideConfigPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Click a fluid gauge with a fluid container on the cursor to fill/drain the tank.
        // The server does the authoritative validation (reach, container type, tank space); the
        // client only gates on the cursor stack exposing an item fluid-handler capability so a
        // plain click-drag over a gauge doesn't spam packets. Empty and filled buckets both
        // expose it, covering fill and drain. The tank index matches the BE gauge mapping
        // (0/1 = fluid inputs, 2 = fluid output).
        if (button == 0) {
            int tankIndex = tankIndexAt((int) mouseX, (int) mouseY);
            if (tankIndex >= 0 && carriedIsFluidContainer()) {
                PacketDistributor.sendToServer(
                        new InteractTankPacket(menu.getBlockEntity().getBlockPos(), tankIndex));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Tank gauge index under the cursor (0/1 = inputs, 2 = output), or -1 if none. */
    private int tankIndexAt(int mouseX, int mouseY) {
        if (isInTank(mouseX, mouseY, TANK_IN1_X)) return 0;
        if (isInTank(mouseX, mouseY, TANK_IN2_X)) return 1;
        if (isInTank(mouseX, mouseY, TANK_OUT_X)) return 2;
        return -1;
    }

    /** Whether the cursor stack is a fluid container (fillable or drainable). */
    private boolean carriedIsFluidContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && carried.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    private void renderProgressTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int px = leftPos + PROGRESS_X;
        int py = topPos + PROGRESS_Y;
        if (mouseX < px - 1 || mouseX >= px + PROGRESS_W + 1 || mouseY < py - 1 || mouseY >= py + PROGRESS_H + 1) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        if (menu.isProcessing()) {
            int percent = (int) (menu.getProgress() * 100f);
            lines.add(Component.literal("Processing"));
            lines.add(Component.literal(percent + "% complete")
                    .withStyle(s -> s.withColor(0x66DD99)));
        } else {
            lines.add(Component.literal("Idle")
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            if (menu.getBlockEntity().getInventory()
                    .getStackInSlot(ProcessingStationBlockEntity.SLOT_DRIVE).isEmpty()) {
                lines.add(Component.translatable("gui.researchcube.processing.insert_drive.tooltip")
                        .withStyle(s -> s.withColor(0xCCAA00).withItalic(true)));
            }
            lines.add(Component.translatable("gui.researchcube.processing.insert_inputs")
                    .withStyle(s -> s.withColor(0x888888).withItalic(true)));
        }

        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void renderFluidTooltips(GuiGraphics g, int mouseX, int mouseY) {
        // Read the synced client-side tank contents (kept live by SyncTankPacket) so the tooltip
        // matches the gauge, including fluid piped in by external mods.
        if (isInTank(mouseX, mouseY, TANK_IN1_X)) {
            renderFluidTooltip(g, "gui.researchcube.processing.tank.input1", menu.getFluidInput1Stack(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_IN2_X)) {
            renderFluidTooltip(g, "gui.researchcube.processing.tank.input2", menu.getFluidInput2Stack(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_OUT_X)) {
            renderFluidTooltip(g, "gui.researchcube.processing.tank.output", menu.getFluidOutputStack(), mouseX, mouseY);
        }
    }

    private boolean isInTank(int mouseX, int mouseY, int tx) {
        int x = leftPos + tx;
        int y = topPos + TANK_Y;
        return mouseX >= x - 1 && mouseX < x + TANK_W + 1 && mouseY >= y - 1 && mouseY < y + TANK_H + 1;
    }

    private void renderFluidTooltip(GuiGraphics g, String tankNameKey, FluidStack stack, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(tankNameKey).withStyle(s -> s.withColor(LABEL_COLOR & 0x00FFFFFF)));

        int capacity = ProcessingStationBlockEntity.TANK_CAPACITY;
        String capacityStr = String.format("%,d", capacity);

        if (stack.isEmpty()) {
            // "Empty: 0 / 8,000 mB"
            lines.add(Component.translatable("gui.researchcube.processing.tank.contents",
                            Component.translatable("gui.researchcube.processing.tank.empty"),
                            "0", capacityStr)
                    .withStyle(s -> s.withColor(0xAAAAAA)));
        } else {
            // "Water: 3,000 / 8,000 mB", tinted toward the fluid's own color.
            Component fluidName = stack.getHoverName();
            String amountStr = String.format("%,d", stack.getAmount());
            lines.add(Component.translatable("gui.researchcube.processing.tank.contents",
                            fluidName, amountStr, capacityStr)
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        }

        // Interaction hint: click with a fluid container to fill/drain this tank.
        lines.add(Component.translatable("gui.researchcube.processing.tank.click_hint")
                .withStyle(s -> s.withColor(0x707070).withItalic(true)));

        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }
}
