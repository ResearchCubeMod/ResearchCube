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

import java.util.ArrayList;
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
 * Each side cell is a compact 18px square showing the side's initial letter (F/B/L/R/U/D) and
 * tinted by its mode. Left-click cycles a side forward through the tab's states, right-click
 * backward. Tooltips show the full side name and the full mode/state name.
 *
 * <p>Tabs come in two flavours:
 * <ul>
 *   <li><b>Ungrouped</b>: one {@link IOChannel} per tab. A side cell cycles that channel's
 *       {@link IOMode} directly through its allowed set.</li>
 *   <li><b>Grouped</b>: several channels sharing an {@link IOChannel#groupId()} collapse into
 *       one tab (e.g. the Processing Station's three fluid tanks become a single "Fluid" tab).
 *       A side cell shows a composite state: None, or exactly one member channel active in its
 *       role. Cycling walks None -> member 0 -> member 1 -> ... -> None. Each change sends one
 *       {@link SetSideConfigPacket} per member channel, so the server model and sided handlers
 *       need no knowledge of grouping.</li>
 * </ul>
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

    // Mode / state colors
    private static final int MODE_NONE = 0xFF373737;
    private static final int MODE_INPUT = 0xFF3B82C4;   // blue
    private static final int MODE_OUTPUT = 0xFFCC7A22;  // orange
    private static final int MODE_BOTH = 0xFFCCA300;    // gold
    private static final int STATE_MIXED = 0xFF8A5CD1;  // violet: illegal legacy combo

    private static final int CELL_W = 18;   // compact single-letter square
    private static final int CELL_H = 18;
    private static final int CELL_GAP = 2;
    private static final int TAB_H = 16;
    private static final int PADDING = 6;
    /** Horizontal padding baked into a tab's width around its (widest) label. */
    private static final int TAB_LABEL_PADDING = 8;

    // The side cross occupies a fixed 3x3 footprint (BACK tucks below LEFT).
    private static final int CROSS_W = 3 * CELL_W + 2 * CELL_GAP + 2 * PADDING;

    /**
     * Fixed panel height: the tab row, the 3-row side cross, and a spare text line. Height does
     * not depend on the channel set, so it is a compile-time constant used by the (static)
     * screen-fit clamp.
     */
    public static final int PANEL_H = TAB_H + PADDING + 3 * CELL_H + 2 * CELL_GAP + PADDING + 12;

    /**
     * Conservative upper bound on panel width, used only by the static {@link #clampX} screen-fit
     * helper (called by embedding screens before an instance width is convenient). Every real
     * panel is at most this wide, so clamping against it never pushes a panel off-screen; a real
     * panel that ends up narrower simply has a little extra slack. Comfortably fits the widest
     * realistic tab strip (e.g. "Items" + "Fluid").
     */
    public static final int MAX_PANEL_W = 160;

    private final Font font;
    private final SideConfigurable configurable;
    private final BlockPos pos;

    /** Presentation tabs (grouped or ungrouped), derived once from the channel list. */
    private final List<Tab> tabs;

    /** Actual panel width: wide enough for both the side cross and every tab label. */
    private final int panelW;

    private int selectedTab = 0;
    private boolean visible = false;

    // Absolute screen origin, set each render.
    private int originX;
    private int originY;

    public SideConfigPanel(Font font, SideConfigurable configurable, BlockPos pos) {
        this.font = font;
        this.configurable = configurable;
        this.pos = pos;
        this.tabs = buildTabs(configurable.getIOChannels());

        // Width must satisfy both the side cross and the tab strip so no tab label ellipsizes.
        int tabStripW = 2; // 1px outline on each side (tabs start at originX + 1)
        for (Tab tab : tabs) {
            tabStripW += font.width(tab.label()) + TAB_LABEL_PADDING;
        }
        this.panelW = Math.max(CROSS_W, tabStripW);
    }

    // ══════════════════════════════════════════════════════════════
    //  Tab model
    // ══════════════════════════════════════════════════════════════

    /**
     * Group consecutive channels sharing a {@link IOChannel#groupId()} into one grouped tab;
     * every other channel becomes its own ungrouped tab. Declaration order is preserved.
     */
    private static List<Tab> buildTabs(List<IOChannel> channels) {
        List<Tab> result = new ArrayList<>();
        int i = 0;
        while (i < channels.size()) {
            IOChannel channel = channels.get(i);
            if (!channel.isGrouped()) {
                result.add(new Tab(channel.translationKey(), List.of(channel)));
                i++;
                continue;
            }
            // Collect all subsequent channels sharing this group id.
            String groupId = channel.groupId();
            List<IOChannel> members = new ArrayList<>();
            String labelKey = channel.groupTranslationKey() != null
                    ? channel.groupTranslationKey() : channel.translationKey();
            int j = i;
            while (j < channels.size() && groupId.equals(channels.get(j).groupId())) {
                members.add(channels.get(j));
                j++;
            }
            result.add(new Tab(labelKey, List.copyOf(members)));
            i = j;
        }
        return result;
    }

    /**
     * One tab in the strip. {@code members} holds a single channel for an ungrouped tab, or the
     * ordered member channels for a grouped tab. {@code label} is the resolved (localized) tab
     * text, cached so tab-width math and rendering agree.
     */
    private record Tab(String translationKey, List<IOChannel> members) {
        boolean isGroup() {
            return members.size() > 1;
        }

        String label() {
            return Component.translatable(translationKey).getString();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Visibility / geometry
    // ══════════════════════════════════════════════════════════════

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
        return panelW;
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
     * <p>Static so embedding screens can position the overlay before/without an instance width;
     * it clamps against {@link #MAX_PANEL_W}, the widest any panel can be. A panel that ends up
     * narrower than that still fits at the returned X (the extra slack only makes the helper fall
     * back to a nearer position a touch sooner), so the real width never needs to be threaded in.
     *
     * <p>Preference order:
     * <ol>
     *   <li>the preferred X (typically right of the GUI background),</li>
     *   <li>left of the GUI ({@code leftPos - MAX_PANEL_W - 4}),</li>
     *   <li>clamped to the right screen edge (overlapping the GUI is acceptable because
     *       the panel renders last and consumes its own clicks first).</li>
     * </ol>
     *
     * @param preferredX  the ideal panel origin X
     * @param leftPos     the screen's GUI left edge (for the left-side fallback)
     * @param screenWidth the current logical screen width
     */
    public static int clampX(int preferredX, int leftPos, int screenWidth) {
        if (preferredX + MAX_PANEL_W <= screenWidth) {
            return preferredX;
        }
        int leftSide = leftPos - MAX_PANEL_W - 4;
        if (leftSide >= 0) {
            return leftSide;
        }
        return Math.max(0, screenWidth - MAX_PANEL_W - 2);
    }

    /**
     * Clamp a preferred panel Y so the panel bottom stays on screen. Static: {@link #PANEL_H} is
     * a fixed constant, so no instance is needed.
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
        g.fill(x, y, x + panelW, y + PANEL_H, PANEL_BG);
        drawOutline(g, x, y, panelW, PANEL_H, PANEL_OUTLINE);

        renderTabs(g, x, y, mouseX, mouseY);
        renderSides(g, x, y, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int tabX = x + 1;
        int tabY = y + 1;
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i).label();
            int tabW = tabWidth(i);
            boolean active = (i == selectedTab);
            int bg = active ? TAB_ACTIVE : TAB_INACTIVE;
            g.fill(tabX, tabY, tabX + tabW, tabY + TAB_H, bg);
            drawOutline(g, tabX, tabY, tabW, TAB_H, PANEL_OUTLINE);

            int labelColor = active ? TEXT_LIGHT : TEXT_SECONDARY;
            g.drawString(font, label, tabX + (tabW - font.width(label)) / 2, tabY + (TAB_H - 8) / 2, labelColor, false);
            tabX += tabW;
        }
    }

    /**
     * Width of tab {@code i}. Each tab is sized to its own label plus padding; the last tab
     * absorbs any leftover width (panelW was widened past the cross for the tab strip) so the
     * strip spans the full panel with no ragged gap.
     */
    private int tabWidth(int i) {
        int base = font.width(tabs.get(i).label()) + TAB_LABEL_PADDING;
        if (i < tabs.size() - 1) {
            return base;
        }
        // Last tab: fill the remaining width up to the panel's right outline.
        int consumed = 1; // left outline
        for (int k = 0; k < tabs.size() - 1; k++) {
            consumed += font.width(tabs.get(k).label()) + TAB_LABEL_PADDING;
        }
        return Math.max(base, panelW - 1 - consumed);
    }

    private void renderSides(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Tab tab = tabs.get(selectedTab);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = x + cell[0];
            int cy = y + cell[1];
            drawSideCell(g, cx, cy, side, tab, mouseX, mouseY);
        }
    }

    private void drawSideCell(GuiGraphics g, int cx, int cy, RelativeSide side, Tab tab, int mouseX, int mouseY) {
        int fill;
        int badge; // 0 = none, 1 or 2 = corner digit for grouped inputs
        if (tab.isGroup()) {
            GroupState state = groupState(tab, side);
            fill = state.color;
            badge = state.badge;
        } else {
            fill = colorForMode(configurable.getSideIOConfig().getMode(tab.members().get(0).id(), side));
            badge = 0;
        }

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

        // Single-letter side label (F/B/L/R/U/D), centered.
        String letter = sideLetter(side);
        boolean disabled = (fill == MODE_NONE);
        int textColor = disabled ? TEXT_SECONDARY : 0xFF000000;
        g.drawString(font, letter, cx + (CELL_W - font.width(letter)) / 2, cy + (CELL_H - 8) / 2, textColor, false);

        // Corner badge distinguishing Input 1 vs Input 2 on the merged fluid tab.
        if (badge != 0) {
            String digit = Integer.toString(badge);
            g.drawString(font, digit, cx + CELL_W - font.width(digit) - 1, cy + 1, 0xFF000000, false);
        }
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Tab tab = tabs.get(selectedTab);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = originX + cell[0];
            int cy = originY + cell[1];
            if (isInCell(mouseX, mouseY, cx, cy)) {
                Component sideLine = Component.translatable(side.getTranslationKey())
                        .withStyle(s -> s.withColor(0xE6EAF5));
                Component stateLine;
                if (tab.isGroup()) {
                    GroupState state = groupState(tab, side);
                    stateLine = Component.translatable(state.tooltipKey)
                            .withStyle(s -> s.withColor(state.color & 0x00FFFFFF));
                } else {
                    IOMode mode = configurable.getSideIOConfig().getMode(tab.members().get(0).id(), side);
                    stateLine = Component.translatable(mode.getTranslationKey())
                            .withStyle(s -> s.withColor(colorForMode(mode) & 0x00FFFFFF));
                }
                g.renderTooltip(font, List.of(sideLine, stateLine), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Grouped-tab composite state
    // ══════════════════════════════════════════════════════════════

    /**
     * The composite state of a grouped tab on one side. {@code activeMember} is the index of the
     * single active member channel (-1 = none active), or {@link #MIXED_INDEX} when more than one
     * member is active at once (an illegal legacy combo that the next click normalizes).
     */
    private record GroupState(int activeMember, int color, int badge, String tooltipKey) {
    }

    private static final int MIXED_INDEX = -2;

    /** The non-NONE "active" mode a grouped member takes when selected (its role). */
    private IOMode activeModeOf(IOChannel channel) {
        if (channel.defaultMode() != IOMode.NONE) {
            return channel.defaultMode();
        }
        for (IOMode mode : channel.orderedAllowedModes()) {
            if (mode != IOMode.NONE) return mode;
        }
        return IOMode.NONE; // degenerate: channel only allows NONE
    }

    /** Derive the composite state of a grouped tab on {@code side} from its members' modes. */
    private GroupState groupState(Tab tab, RelativeSide side) {
        List<IOChannel> members = tab.members();
        int active = -1;
        int activeCount = 0;
        for (int m = 0; m < members.size(); m++) {
            IOMode mode = configurable.getSideIOConfig().getMode(members.get(m).id(), side);
            if (mode != IOMode.NONE) {
                active = m;
                activeCount++;
            }
        }
        if (activeCount == 0) {
            return new GroupState(-1, MODE_NONE, 0, "gui.researchcube.iomode.none");
        }
        if (activeCount > 1) {
            return new GroupState(MIXED_INDEX, STATE_MIXED, 0, "gui.researchcube.side_state.mixed");
        }
        IOChannel channel = members.get(active);
        IOMode role = activeModeOf(channel);
        int color = colorForMode(role);
        // Badge only for the two fluid inputs (blue). Output stays badge-free.
        int badge = 0;
        if (role == IOMode.INPUT) {
            // Count how many input members precede+include this one to number them 1..N.
            int inputRank = 0;
            for (int m = 0; m <= active; m++) {
                if (activeModeOf(members.get(m)) == IOMode.INPUT) inputRank++;
            }
            badge = inputRank;
        }
        return new GroupState(active, color, badge, channel.translationKey());
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
        int tabX = originX + 1;
        int tabY = originY + 1;
        for (int i = 0; i < tabs.size(); i++) {
            int tabW = tabWidth(i);
            if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + TAB_H) {
                selectedTab = i;
                return true;
            }
            tabX += tabW;
        }

        // Side cell clicks
        if (button != 0 && button != 1) {
            // Consume other buttons inside the panel bounds so they don't leak to the screen.
            return isInPanel(mouseX, mouseY);
        }
        Tab tab = tabs.get(selectedTab);
        for (RelativeSide side : RelativeSide.values()) {
            int[] cell = cellPos(side);
            int cx = originX + cell[0];
            int cy = originY + cell[1];
            if (isInCell((int) mouseX, (int) mouseY, cx, cy)) {
                if (tab.isGroup()) {
                    cycleGroup(tab, side, button == 0);
                } else {
                    cycleChannel(tab.members().get(0), side, button == 0);
                }
                return true;
            }
        }

        // Swallow clicks anywhere inside the panel so they don't fall through to the screen.
        return isInPanel(mouseX, mouseY);
    }

    /** Cycle a plain (ungrouped) channel's mode and push the change to the server. */
    private void cycleChannel(IOChannel channel, RelativeSide side, boolean forward) {
        List<IOMode> allowed = channel.orderedAllowedModes();
        IOMode current = configurable.getSideIOConfig().getMode(channel.id(), side);
        IOMode next = forward ? current.next(allowed) : current.previous(allowed);
        if (next == current) return;
        applyMode(channel, side, next);
    }

    /**
     * Cycle a grouped tab's composite state on {@code side}. Composite states walk
     * None -> member 0 -> member 1 -> ... -> None (forward) and reverse (backward). A legacy
     * MIXED side normalizes to the first state in the cycle direction.
     *
     * <p>Applies by writing each member's target mode: the newly-active member takes its role
     * mode, all other members go NONE. Only members whose mode actually changes emit a packet
     * (up to one per member). {@link SetSideConfigPacket} handling is per-packet and never rate
     * limited, so several packets in the same click are all applied.
     */
    private void cycleGroup(Tab tab, RelativeSide side, boolean forward) {
        int memberCount = tab.members().size();
        // Composite index: 0 = None, 1..memberCount = that member active.
        GroupState current = groupState(tab, side);
        int currentIndex;
        if (current.activeMember == MIXED_INDEX) {
            currentIndex = 0; // treat legacy mixed as "None" so the next step is deterministic
        } else if (current.activeMember < 0) {
            currentIndex = 0;
        } else {
            currentIndex = current.activeMember + 1;
        }

        int size = memberCount + 1; // + the None state
        int nextIndex = ((currentIndex + (forward ? 1 : -1)) % size + size) % size;

        int targetMember = nextIndex - 1; // -1 => None
        for (int m = 0; m < memberCount; m++) {
            IOChannel channel = tab.members().get(m);
            IOMode target = (m == targetMember) ? activeModeOf(channel) : IOMode.NONE;
            IOMode currentMode = configurable.getSideIOConfig().getMode(channel.id(), side);
            if (currentMode != target) {
                applyMode(channel, side, target);
            }
        }
    }

    /** Optimistic client update + server packet for one channel/side/mode change. */
    private void applyMode(IOChannel channel, RelativeSide side, IOMode mode) {
        configurable.getSideIOConfig().setMode(channel.id(), side, mode);
        PacketDistributor.sendToServer(new SetSideConfigPacket(
                pos, channel.id(), (byte) side.ordinal(), (byte) mode.ordinal()));
    }

    /** Whether the mouse is anywhere within the panel bounds. */
    public boolean isInPanel(double mouseX, double mouseY) {
        return visible && mouseX >= originX && mouseX < originX + panelW
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

    /** Single uppercase letter for a side's cell (Up/Down for TOP/BOTTOM to stay unambiguous). */
    private static String sideLetter(RelativeSide side) {
        return switch (side) {
            case FRONT -> "F";
            case BACK -> "B";
            case LEFT -> "L";
            case RIGHT -> "R";
            case TOP -> "U";
            case BOTTOM -> "D";
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
}
