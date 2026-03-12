package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.menu.ProcessingStationMenu;
import com.researchcube.network.StartProcessingPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the Processing Station.
 * 
 * Layout:
 *   Left: 4×4 input grid
 *   Center: Fluid tanks + progress bar
 *   Right: 2×4 output grid
 *   Bottom: Player inventory
 */
public class ProcessingStationScreen extends AbstractContainerScreen<ProcessingStationMenu> {

    // Colors
    private static final int BG_OUTER = 0xFFC6C6C6;
    private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_BORDER_DARK = 0xFF555555;
    private static final int PANEL_BG = 0xFF8B8B8B;
    private static final int SLOT_BG = 0xFF373737;
    private static final int PROGRESS_BG = 0xFF555555;
    private static final int PROGRESS_FG = 0xFF00AA00;
    private static final int FLUID_IN1_COLOR = 0xFF4488FF;
    private static final int FLUID_IN2_COLOR = 0xFF8844FF;
    private static final int FLUID_OUT_COLOR = 0xFFFFAA00;

    private Button startButton;

    public ProcessingStationScreen(ProcessingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 182;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Start button (center-bottom of machine area)
        startButton = Button.builder(Component.literal("Start"), btn -> onStartProcessing())
                .bounds(leftPos + 80 - 20, topPos + 72, 40, 16)
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Outer background
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_OUTER);
        
        // Bevel effect
        g.fill(x, y, x + imageWidth, y + 1, PANEL_BORDER_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, PANEL_BORDER_LIGHT);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER_DARK);

        // Input panel (left side)
        drawInsetPanel(g, x + 6, y + 16, 74, 74);

        // Output panel (right side)
        drawInsetPanel(g, x + 114, y + 16, 38, 74);

        // Center panel (tanks + progress)
        drawInsetPanel(g, x + 83, y + 16, 28, 74);

        // Draw slot backgrounds
        // Input slots (4×4)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                drawSlotBg(g, x + 8 + col * 18, y + 18 + row * 18);
            }
        }

        // Output slots (2×4)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                drawSlotBg(g, x + 116 + col * 18, y + 18 + row * 18);
            }
        }

        // Draw fluid tanks (vertical gauges)
        drawFluidGauge(g, x + 85, y + 18, 10, 30, menu.getFluidInput1Amount(), FLUID_IN1_COLOR);
        drawFluidGauge(g, x + 99, y + 18, 10, 30, menu.getFluidInput2Amount(), FLUID_IN2_COLOR);
        drawFluidGauge(g, x + 92, y + 52, 10, 30, menu.getFluidOutputAmount(), FLUID_OUT_COLOR);

        // Progress arrow (center)
        drawProgressBar(g, x + 85, y + 50, 24, 8);

        // Player inventory panel
        drawInsetPanel(g, x + 6, y + 98, 164, 76);

        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + 8 + col * 18, y + 100 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotBg(g, x + 8 + col * 18, y + 158);
        }
    }

    private void drawInsetPanel(GuiGraphics g, int px, int py, int pw, int ph) {
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.fill(px, py, px + pw, py + 1, PANEL_BORDER_DARK);
        g.fill(px, py, px + 1, py + ph, PANEL_BORDER_DARK);
        g.fill(px + pw - 1, py, px + pw, py + ph, PANEL_BORDER_LIGHT);
        g.fill(px, py + ph - 1, px + pw, py + ph, PANEL_BORDER_LIGHT);
    }

    private void drawSlotBg(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
    }

    private void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh, int amount, int color) {
        // Background
        g.fill(gx, gy, gx + gw, gy + gh, PANEL_BORDER_DARK);
        g.fill(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1, 0xFF222222);

        // Fluid fill (bottom to top)
        if (amount > 0) {
            int maxAmount = ProcessingStationBlockEntity.TANK_CAPACITY;
            float fillRatio = Math.min(1.0f, (float) amount / maxAmount);
            int fillHeight = (int) ((gh - 2) * fillRatio);
            int fillY = gy + gh - 1 - fillHeight;
            g.fill(gx + 1, fillY, gx + gw - 1, gy + gh - 1, color);
        }
    }

    private void drawProgressBar(GuiGraphics g, int px, int py, int pw, int ph) {
        // Background
        g.fill(px, py, px + pw, py + ph, PROGRESS_BG);

        // Progress
        if (menu.isProcessing()) {
            float progress = menu.getProgress();
            int fillWidth = (int) (pw * progress);
            g.fill(px, py, px + fillWidth, py + ph, PROGRESS_FG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Processing status
        if (menu.isProcessing()) {
            int percent = (int) (menu.getProgress() * 100);
            String status = percent + "%";
            int statusX = 80 - font.width(status) / 2;
            guiGraphics.drawString(font, status, statusX, 62, 0x00AA00, false);
        }
    }
}
