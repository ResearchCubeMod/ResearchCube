package com.researchcube.client.screen;

import com.researchcube.sideio.IOChannel;
import com.researchcube.sideio.IOMode;
import com.researchcube.sideio.RelativeSide;
import com.researchcube.sideio.SideConfigurable;
import com.researchcube.network.SetSideConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Reusable AMOLED-styled overlay panel for editing a {@link SideConfigurable} block entity's
 * per-side IO modes. Any screen can embed one: forward mouse events to
 * {@link #mouseClicked(double, double, int)} and draw with {@link #render}.
 *
 * <p>Layout: a row of channel tabs across the top, then an unfolded-cross of the six relative
 * sides for the selected channel (Mekanism/Thermal-style, with BACK below LEFT):
 * <pre>
 *          [TOP]
 *   [LEFT] [FRONT][RIGHT]
 *   [BACK] [BOTTOM]
 * </pre>
 * Left-click cycles a side's mode forward through the channel's allowed set, right-click backward.
 * Each change is sent to the server immediately and applied optimistically on the client.
 */
public class SideConfigPanel {

    // ── AMOLED palette (matches the mod's screens) ──
    private static final int PANEL_BG = 0xFF0C0C0C;
    private static final int PANEL_OUTLINE = 0xFF282828;
    private static final int SLOT_BEVEL_LIGHT = 0xFF373737;
    private static final int SLOT_BEVEL_DARK = 0xFF141414;
    private static final int TEXT_LIGHT = 0xFFE6EAF5;
    private static final int TEXT_SECONDARY = 0xFFA3AAC0;
    private static final int TAB_ACTIVE = 0xFF334488;
    private static final int TAB_INACTIVE = 0xFF1A1A1A;

    // Mode colors
    private static final int MODE_NONE = 0xFF373737;
    private static final int MODE_INPUT = 0xFF3B82C4;   // blue
    private static final int MODE_OUTPUT = 0xFFCC7A22;  // orange
    private static final int MODE_BOTH = 0xFFCCA300;    // gold

    private static final int CELL_W = 36;   // wide enough for the full side name ("Bottom") at GUI font scale
    private static final int CELL_H = 18;
    private static final int CELL_GAP = 2;
    private static final int TAB_H = 16;
    private static final int PADDING = 6;

    // Layout is a 3-column cross: TOP at col1, LEFT/FRONT/RIGHT across, BACK at col0 / BOTTOM at col1.
    public static final int PANEL_W = 3 * CELL_W + 2 * CELL_GAP + 2 * PADDING;
    public static final int PANEL_H = TAB_H + PADDING + 3 * CELL_H + 2 * CELL_GAP + PADDING + 12;

    private final Font font;
    private final SideConfigurable configurable;
    private final BlockPos pos;
    private final List<IOChannel> channels;

    private int selectedChannel = 0;
    private boolean visible = false;

    // Absolute screen origin, set each render.
    private int originX;
    private int originY;

    public SideConfigPanel(Font font, SideConfigurable configurable, BlockPos pos) {
        this.font = font;
        this.configurable = configurable;
        this.pos = pos;
        this.channels = configurable.getIOChannels();
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggleVisible() {
        this.visible = !this.visible;
    }

    public int getWidth() {
        return PANEL_W;
    }

    public int getHeight() {
        return PANEL_H;
    }

    // ══════════════════════════════════════════════════════════════
    //  Screen-fit helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * Clamp a preferred panel X so the panel always fits on screen. At small logical
     * resolutions (large GUI scale) "right of the GUI" can land partially or fully
     * off-screen, leaving the panel invisible and unclickable.
     *
     * <p>Preference order:
     * <ol>
     *   <li>the preferred X (typically right of the GUI background),</li>
     *   <li>left of the GUI ({@code leftPos - PANEL_W - 4}),</li>
     *   <li>clamped to the right screen edge — overlapping the GUI is acceptable because
     *       the panel renders last and consumes its own clicks first.</li>
     * </ol>
     *
     * @param preferredX  the ideal panel origin X
     * @param leftPos     the screen's GUI left edge (for the left-side fallback)
     * @param screenWidth the current logical screen width
     */
    public static int clampX(int preferredX, int leftPos, int screenWidth) {
        if (preferredX + PANEL_W <= screenWidth) {
            return preferredX;
        }
        int leftSide = leftPos - PANEL_W - 4;
        if (leftSide >= 0) {
            return leftSide;
        }
        return Math.max(0, screenWidth - PANEL_W - 2);
    }

    /**
     * Clamp a preferred panel Y so the panel bottom stays on screen.
     *
     * @param preferredY   the ideal panel origin Y
     * @param screenHeight the current logical screen height
     */
    public static int clampY(int preferredY, int screenHeight) {
        return Math.max(0, Math.min(preferredY, screenHeight - PANEL_H));
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════

    /**
     * Draw the panel at the given absolute screen position. No-op when hidden.
     * Tooltips are rendered here as well (drawn last so they sit on top).
     */
    public void render(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        if (!visible) return;
        this.originX = x;
        this.originY = y;

        // Panel background + outline
        g.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        drawOutline(g, x, y, PANEL_W, PANEL_H, PANEL_OUTLINE);

        renderTabs(g, x, y, mouseX, mouseY);
        renderSides(g, x, y, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int tabW = (PANEL_W - 2) / Math.max(1, channels.size());
        int tabX = x + 1;
        int tabY = y + 1;
        for (int i = 0; i < channels.size(); i++) {
            boolean active = (i == selectedChannel);
            int bg = active ? TAB_ACTIVE : TAB_INACTIVE;
            g.fill(tabX, tabY, tabX + tabW, tabY + TAB_H, bg);
            drawOutline(g, tabX, tabY, tabW, TAB_H, PANEL_OUTLINE);

            String label = Component.translatable(channels.get(i).translationKey()).getString();
            label = trim(label, tabW - 4);
            int labelColor = active ? TEXT_LIGHT : TEXT_SECONDARY;
            g.drawString(font, label, tabX + (tabW - font.width(label)) / 2, tabY + (TAB_H - 8) / 2, labelColor, false);
            tabX += tabW;
        }
    }

    private void renderSides(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        IOChannel channel = channels.get(selectedChannel);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = x + cell[0];
            int cy = y + cell[1];
            IOMode mode = configurable.getSideIOConfig().getMode(channel.id(), side);
            drawSideCell(g, cx, cy, side, mode, mouseX, mouseY);
        }
    }

    private void drawSideCell(GuiGraphics g, int cx, int cy, RelativeSide side, IOMode mode, int mouseX, int mouseY) {
        int fill = colorForMode(mode);
        g.fill(cx, cy, cx + CELL_W, cy + CELL_H, fill);
        // Bevel: dark top/left, light bottom/right
        g.fill(cx, cy, cx + CELL_W, cy + 1, SLOT_BEVEL_DARK);
        g.fill(cx, cy, cx + 1, cy + CELL_H, SLOT_BEVEL_DARK);
        g.fill(cx + CELL_W - 1, cy, cx + CELL_W, cy + CELL_H, SLOT_BEVEL_LIGHT);
        g.fill(cx, cy + CELL_H - 1, cx + CELL_W, cy + CELL_H, SLOT_BEVEL_LIGHT);

        // Hover highlight
        if (isInCell(mouseX, mouseY, cx, cy)) {
            g.fill(cx + 1, cy + 1, cx + CELL_W - 1, cy + CELL_H - 1, 0x33FFFFFF);
        }

        // Full side name, ellipsis-trimmed if a localized string still overruns the cell.
        String label = trim(Component.translatable(side.getTranslationKey()).getString(), CELL_W - 4);
        int textColor = (mode == IOMode.NONE) ? TEXT_SECONDARY : 0xFF000000;
        g.drawString(font, label, cx + (CELL_W - font.width(label)) / 2, cy + (CELL_H - 8) / 2, textColor, false);
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        IOChannel channel = channels.get(selectedChannel);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = originX + cell[0];
            int cy = originY + cell[1];
            if (isInCell(mouseX, mouseY, cx, cy)) {
                IOMode mode = configurable.getSideIOConfig().getMode(channel.id(), side);
                g.renderTooltip(font, List.of(
                        Component.translatable(side.getTranslationKey()).withStyle(s -> s.withColor(0xE6EAF5)),
                        Component.translatable(mode.getTranslationKey()).withStyle(s -> s.withColor(colorForMode(mode) & 0x00FFFFFF))
                ), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Input
    // ══════════════════════════════════════════════════════════════

    /**
     * Handle a click within the panel. Returns true if the click was consumed.
     *
     * @param button 0 = left (cycle forward), 1 = right (cycle backward)
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // Tab clicks
        int tabW = (PANEL_W - 2) / Math.max(1, channels.size());
        int tabX = originX + 1;
        int tabY = originY + 1;
        for (int i = 0; i < channels.size(); i++) {
            if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + TAB_H) {
                selectedChannel = i;
                return true;
            }
            tabX += tabW;
        }

        // Side cell clicks
        if (button != 0 && button != 1) {
            // Consume other buttons inside the panel bounds so they don't leak to the screen.
            return isInPanel(mouseX, mouseY);
        }
        IOChannel channel = channels.get(selectedChannel);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = originX + cell[0];
            int cy = originY + cell[1];
            if (isInCell((int) mouseX, (int) mouseY, cx, cy)) {
                cycleSide(channel, side, button == 0);
                return true;
            }
        }

        // Swallow clicks anywhere inside the panel so they don't fall through to the screen.
        return isInPanel(mouseX, mouseY);
    }

    private void cycleSide(IOChannel channel, RelativeSide side, boolean forward) {
        List<IOMode> allowed = channel.orderedAllowedModes();
        IOMode current = configurable.getSideIOConfig().getMode(channel.id(), side);
        IOMode next = forward ? current.next(allowed) : current.previous(allowed);
        if (next == current) return;

        // Optimistic client update; server sync corrects if rejected.
        configurable.getSideIOConfig().setMode(channel.id(), side, next);
        PacketDistributor.sendToServer(new SetSideConfigPacket(
                pos, channel.id(), (byte) side.ordinal(), (byte) next.ordinal()));
    }

    /** Whether the mouse is anywhere within the panel bounds. */
    public boolean isInPanel(double mouseX, double mouseY) {
        return visible && mouseX >= originX && mouseX < originX + PANEL_W
                && mouseY >= originY && mouseY < originY + PANEL_H;
    }

    // ══════════════════════════════════════════════════════════════
    //  Layout helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * Relative (x,y) of a side's cell within the panel body. Three-column cross with BACK
     * tucked below LEFT (Mekanism/Thermal convention):
     * <pre>
     *          [TOP]      (row0, col1)
     *   [LEFT] [FRONT][RIGHT]   (row1: col0/col1/col2)
     *   [BACK] [BOTTOM]         (row2: col0/col1)
     * </pre>
     */
    private int[] cellPos(RelativeSide side) {
        int stepX = CELL_W + CELL_GAP;
        int stepY = CELL_H + CELL_GAP;
        int bodyY = 1 + TAB_H + PADDING;
        int col0 = PADDING;
        return switch (side) {
            case TOP ->    new int[]{col0 + stepX, bodyY};
            case LEFT ->   new int[]{col0, bodyY + stepY};
            case FRONT ->  new int[]{col0 + stepX, bodyY + stepY};
            case RIGHT ->  new int[]{col0 + 2 * stepX, bodyY + stepY};
            case BACK ->   new int[]{col0, bodyY + 2 * stepY};
            case BOTTOM -> new int[]{col0 + stepX, bodyY + 2 * stepY};
        };
    }

    private boolean isInCell(int mouseX, int mouseY, int cx, int cy) {
        return mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H;
    }

    private static int colorForMode(IOMode mode) {
        return switch (mode) {
            case NONE -> MODE_NONE;
            case INPUT -> MODE_INPUT;
            case OUTPUT -> MODE_OUTPUT;
            case BOTH -> MODE_BOTH;
        };
    }

    private void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String t = text;
        while (font.width(t + "…") > maxWidth && t.length() > 1) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}
