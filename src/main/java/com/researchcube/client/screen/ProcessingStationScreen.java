package com.researchcube.client.screen;

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
 * Enhanced screen for the Processing Station.
 */
public class ProcessingStationScreen extends AbstractContainerScreen<ProcessingStationMenu> {

    // Colors
    private static final int BG_OUTER = 0xFFC6C6C6;
    private static final int PANEL_BG = 0xFF4A4F60;
    private static final int PANEL_INNER = 0xFF2C3140;
    private static final int PANEL_BORDER_LIGHT = 0xFF8791B0;
    private static final int PANEL_BORDER_DARK = 0xFF171920;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF272830;
    private static final int PROGRESS_BG = 0xFF101216;
    private static final int PROGRESS_FG = 0xFF22C55E;
    private static final int FLUID_IN1_COLOR = 0xFF4F9BFF;
    private static final int FLUID_IN2_COLOR = 0xFFB16CFF;
    private static final int FLUID_OUT_COLOR = 0xFFFFB547;

    // Panels
    private static final int MACHINE_PANEL_X = 8;
    private static final int MACHINE_PANEL_Y = 18;
    private static final int MACHINE_PANEL_W = 344;
    private static final int MACHINE_PANEL_H = 98;

    private static final int INV_PANEL_X = 8;
    private static final int INV_PANEL_Y = 120;
    private static final int INV_PANEL_W = 344;
    private static final int INV_PANEL_H = 108;

    // Fluid gauges
    private static final int TANK_IN1_X = 149;
    private static final int TANK_IN1_Y = 40;
    private static final int TANK_IN2_X = 167;
    private static final int TANK_IN2_Y = 40;
    private static final int TANK_OUT_X = 185;
    private static final int TANK_OUT_Y = 40;
    private static final int TANK_W = 14;
    private static final int TANK_H = 36;

    // Progress bar
    private static final int PROGRESS_X = 144;
    private static final int PROGRESS_Y = 90;
    private static final int PROGRESS_W = 60;
    private static final int PROGRESS_H = 8;

    private Button startButton;

    public ProcessingStationScreen(ProcessingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 360;
        this.imageHeight = 236;
        this.inventoryLabelX = 10;
        this.inventoryLabelY = 110;
    }

    @Override
    protected void init() {
        super.init();

        // Start button centered in the control channel.
        startButton = Button.builder(Component.literal("Start"), btn -> onStartProcessing())
            .bounds(leftPos + 145, topPos + 66, 58, 18)
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

        // Outer background
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_OUTER);

        // Bevel effect
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);

        drawInsetPanel(g, x + MACHINE_PANEL_X, y + MACHINE_PANEL_Y, MACHINE_PANEL_W, MACHINE_PANEL_H);
        drawInsetPanel(g, x + INV_PANEL_X, y + INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H);
        drawInsetPanel(g, x + 142, y + 34, 70, 72);

        // Input slots (4x4)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                drawSlotBg(g,
                        x + ProcessingStationMenu.INPUT_GRID_X + col * 18,
                        y + ProcessingStationMenu.INPUT_GRID_Y + row * 18);
            }
        }

        // Output slots (2x4)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                drawSlotBg(g,
                        x + ProcessingStationMenu.OUTPUT_GRID_X + col * 18,
                        y + ProcessingStationMenu.OUTPUT_GRID_Y + row * 18);
            }
        }

        drawFluidGauge(g, x + TANK_IN1_X, y + TANK_IN1_Y, TANK_W, TANK_H, menu.getFluidInput1Amount(), FLUID_IN1_COLOR);
        drawFluidGauge(g, x + TANK_IN2_X, y + TANK_IN2_Y, TANK_W, TANK_H, menu.getFluidInput2Amount(), FLUID_IN2_COLOR);
        drawFluidGauge(g, x + TANK_OUT_X, y + TANK_OUT_Y, TANK_W, TANK_H, menu.getFluidOutputAmount(), FLUID_OUT_COLOR);

        // Progress bar
        drawProgressBar(g, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_W, PROGRESS_H);

        // Machine labels
        g.drawString(font, "Inputs", x + 24, y + 24, 0xFFC9CEDC, false);
        g.drawString(font, "Control", x + 150, y + 24, 0xFFC9CEDC, false);
        g.drawString(font, "Outputs", x + 232, y + 24, 0xFFC9CEDC, false);
        g.drawString(font, "I1", x + 151, y + 30, FLUID_IN1_COLOR, false);
        g.drawString(font, "I2", x + 169, y + 30, FLUID_IN2_COLOR, false);
        g.drawString(font, "O", x + 190, y + 30, FLUID_OUT_COLOR, false);

        // Flow indicators
        g.drawString(font, ">>", x + 124, y + 68, 0xFF848AA0, false);
        g.drawString(font, ">>", x + 214, y + 68, 0xFF848AA0, false);

        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g,
                        x + ProcessingStationMenu.PLAYER_INV_X + col * 18,
                        y + ProcessingStationMenu.PLAYER_INV_Y + row * 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlotBg(g,
                    x + ProcessingStationMenu.HOTBAR_X + col * 18,
                    y + ProcessingStationMenu.HOTBAR_Y);
        }
    }

    private void drawInsetPanel(GuiGraphics g, int px, int py, int pw, int ph) {
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL_INNER);
        g.fill(px, py, px + pw, py + 1, PANEL_BORDER_DARK);
        g.fill(px, py, px + 1, py + ph, PANEL_BORDER_DARK);
        g.fill(px + pw - 1, py, px + pw, py + ph, PANEL_BORDER_LIGHT);
        g.fill(px, py + ph - 1, px + pw, py + ph, PANEL_BORDER_LIGHT);
    }

    private void drawSlotBg(GuiGraphics g, int sx, int sy) {
        int x0 = sx - 1;
        int y0 = sy - 1;
        g.fill(x0, y0, x0 + 18, y0 + 18, SLOT_BG);
        g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, SLOT_INNER);
        g.fill(x0, y0, x0 + 18, y0 + 1, PANEL_BORDER_DARK);
        g.fill(x0, y0, x0 + 1, y0 + 18, PANEL_BORDER_DARK);
    }

    private void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh, int amount, int color) {
        // Background
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF161920);

        // Fluid fill (bottom to top)
        if (amount > 0) {
            int maxAmount = ProcessingStationBlockEntity.TANK_CAPACITY;
            float fillRatio = Math.min(1.0f, (float) amount / maxAmount);
            int fillHeight = (int) (gh * fillRatio);
            int fillY = gy + gh - fillHeight;
            g.fill(gx, fillY, gx + gw, gy + gh, color);
            if (fillHeight > 2) {
                g.fill(gx, fillY, gx + gw, fillY + 1, 0x55FFFFFF);
            }
        }

        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    private void drawProgressBar(GuiGraphics g, int px, int py, int pw, int ph) {
        // Background
        g.fill(px, py, px + pw, py + ph, PROGRESS_BG);
        g.fill(px - 1, py - 1, px + pw + 1, py, PANEL_BORDER_DARK);
        g.fill(px - 1, py, px, py + ph + 1, PANEL_BORDER_DARK);

        // Progress
        if (menu.isProcessing()) {
            float progress = menu.getProgress();
            int fillWidth = (int) (pw * progress);
            g.fill(px, py, px + fillWidth, py + ph, PROGRESS_FG);
            if (fillWidth > 1) {
                g.fill(px, py, px + fillWidth, py + 1, 0x55FFFFFF);
            }
        }

        g.fill(px + pw, py - 1, px + pw + 1, py + ph + 1, PANEL_BORDER_LIGHT);
        g.fill(px - 1, py + ph, px + pw + 1, py + ph + 1, PANEL_BORDER_LIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);

        // Processing status
        if (menu.isProcessing()) {
            int percent = (int) (menu.getProgress() * 100);
            String status = "Processing " + percent + "%";
            int statusX = 174 - font.width(status) / 2;
            guiGraphics.drawString(font, status, statusX, 100, 0xFF4ADE80, false);
        } else {
            String status = "Idle";
            int statusX = 174 - font.width(status) / 2;
            guiGraphics.drawString(font, status, statusX, 100, 0xFFB0B0B0, false);
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
        if (isInTank(mouseX, mouseY, TANK_IN1_X, TANK_IN1_Y)) {
            renderFluidTooltip(g, "Input Tank 1", menu.getBlockEntity().getFluidInput1().getFluid(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_IN2_X, TANK_IN2_Y)) {
            renderFluidTooltip(g, "Input Tank 2", menu.getBlockEntity().getFluidInput2().getFluid(), mouseX, mouseY);
            return;
        }
        if (isInTank(mouseX, mouseY, TANK_OUT_X, TANK_OUT_Y)) {
            renderFluidTooltip(g, "Output Tank", menu.getBlockEntity().getFluidOutput().getFluid(), mouseX, mouseY);
        }
    }

    private boolean isInTank(int mouseX, int mouseY, int tx, int ty) {
        int x = leftPos + tx;
        int y = topPos + ty;
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
