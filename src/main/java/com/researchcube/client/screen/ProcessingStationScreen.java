package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.menu.ProcessingStationMenu;
import com.researchcube.network.StartProcessingPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
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
 *   Machine panel: 4x4 inputs | tanks + progress + Start | 2x4 outputs
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

    // Start button
    private static final int BUTTON_X = 108;
    private static final int BUTTON_Y = 88;
    private static final int BUTTON_W = 68;
    private static final int BUTTON_H = 16;

    // Status text baseline
    private static final int STATUS_Y = 107;

    private Button startButton;

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

        startButton = Button.builder(Component.literal("Start"), btn -> onStartProcessing())
                .bounds(leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H)
                .build();
        addRenderableWidget(startButton);
    }

    private void onStartProcessing() {
        if (menu.isProcessing()) return;
        ProcessingStationBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new StartProcessingPacket(be.getBlockPos()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        startButton.active = !menu.isProcessing();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

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

        // Fluid gauges (dynamic fill over the baked gauge background)
        int capacity = ProcessingStationBlockEntity.TANK_CAPACITY;
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_IN1_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidInput1Amount(), capacity, FLUID_IN1_COLOR);
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_IN2_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidInput2Amount(), capacity, FLUID_IN2_COLOR);
        ScreenRenderHelper.drawFluidGauge(g, x + TANK_OUT_X, y + TANK_Y, TANK_W, TANK_H,
                menu.getFluidOutputAmount(), capacity, FLUID_OUT_COLOR);

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
    }

    private void drawCentered(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, 6, LABEL_COLOR, false);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);

        // Processing status under the Start button
        if (menu.isProcessing()) {
            int percent = (int) (menu.getProgress() * 100);
            String status = "Processing " + percent + "%";
            g.drawString(font, status, CONTROL_CENTER_X - font.width(status) / 2, STATUS_Y, 0xFF4ADE80, false);
        } else {
            String status = "Idle";
            g.drawString(font, status, CONTROL_CENTER_X - font.width(status) / 2, STATUS_Y, 0xFFB0B0B0, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderFluidTooltips(guiGraphics, mouseX, mouseY);
        renderProgressTooltip(guiGraphics, mouseX, mouseY);

        renderTooltip(guiGraphics, mouseX, mouseY);
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
            lines.add(Component.literal("Insert valid inputs and press Start")
                    .withStyle(s -> s.withColor(0x888888).withItalic(true)));
        }

        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void renderFluidTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (isInTank(mouseX, mouseY, TANK_IN1_X)) {
            renderFluidTooltip(g, "Input Tank 1", menu.getBlockEntity().getFluidInput1().getFluid(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_IN2_X)) {
            renderFluidTooltip(g, "Input Tank 2", menu.getBlockEntity().getFluidInput2().getFluid(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_OUT_X)) {
            renderFluidTooltip(g, "Output Tank", menu.getBlockEntity().getFluidOutput().getFluid(), mouseX, mouseY);
        }
    }

    private boolean isInTank(int mouseX, int mouseY, int tx) {
        int x = leftPos + tx;
        int y = topPos + TANK_Y;
        return mouseX >= x - 1 && mouseX < x + TANK_W + 1 && mouseY >= y - 1 && mouseY < y + TANK_H + 1;
    }

    private void renderFluidTooltip(GuiGraphics g, String tankName, FluidStack stack, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(tankName));

        if (stack.isEmpty()) {
            lines.add(Component.literal("Empty")
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            lines.add(Component.literal("0 / " + ProcessingStationBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        } else {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            String fluidName = id != null ? id.toString() : "unknown";
            lines.add(Component.literal(fluidName)
                    .withStyle(s -> s.withColor(0x66CCFF)));
            lines.add(Component.literal(stack.getAmount() + " / " + ProcessingStationBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        }

        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }
}
