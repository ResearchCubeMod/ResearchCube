package com.researchcube.client.screen;

import com.researchcube.menu.ResearchTableMenu;
import com.researchcube.research.FluidCost;
import com.researchcube.research.ItemCost;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.prerequisite.AndPrerequisite;
import com.researchcube.research.prerequisite.NonePrerequisite;
import com.researchcube.research.prerequisite.OrPrerequisite;
import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.SinglePrerequisite;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self-contained research dependency graph: builds a layered DAG from research
 * prerequisites and renders it with pan/zoom inside a viewport. Used as the "Tree"
 * view embedded in {@link ResearchTableScreen}.
 *
 * <p>The component owns its own selection, pan and zoom; the host screen sets the
 * viewport (absolute screen coordinates) and forwards mouse events. Selection is
 * kept in sync with the screen via {@link #getSelectedId()} / {@link #setSelectedId}.
 */
public class ResearchGraphView {

    // ── Visual constants ──
    private static final int GRAPH_BG = 0xFF171A26;
    private static final int EDGE_SINGLE = 0xFF9CA3AF;
    private static final int EDGE_AND = 0xFF2DD4BF;
    private static final int EDGE_OR = 0xFFF59E0B;

    private static final int NODE_W = 116;
    private static final int NODE_H = 36;
    private static final int LAYER_X_GAP = 178;
    private static final int LAYER_Y_GAP = 64;

    // Dashed-line pattern (for OR edges): DASH_ON px drawn, then a gap, repeating every DASH_PERIOD.
    private static final int DASH_ON = 3;
    private static final int DASH_PERIOD = 5;

    private enum EdgeStyle { SINGLE, AND, OR }

    private static final class NodeBox {
        private final ResearchDefinition def;
        private int worldX;
        private int worldY;

        private NodeBox(ResearchDefinition def) { this.def = def; }
    }

    private record GraphEdge(ResourceLocation from, ResourceLocation to, EdgeStyle style) {}

    private record Dependency(ResourceLocation sourceId, EdgeStyle style) {}

    private final Font font;
    private final ResearchTableMenu menu;

    private final List<NodeBox> nodeBoxes = new ArrayList<>();
    private final List<GraphEdge> graphEdges = new ArrayList<>();
    private final Map<ResourceLocation, NodeBox> nodesById = new HashMap<>();

    private ResourceLocation selectedId;

    // Viewport (absolute screen coordinates), set by the host screen.
    private int vpX, vpY, vpW = 1, vpH = 1;

    private float zoom = 1.0f;
    private int panX = 0;
    private int panY = 0;
    private boolean dragging;
    private double dragLastX;
    private double dragLastY;

    private int graphMinX, graphMinY, graphMaxX, graphMaxY;
    private int lastRegistrySize = -1;
    private boolean graphDirty = true;

    public ResearchGraphView(Font font, ResearchTableMenu menu) {
        this.font = font;
        this.menu = menu;
    }

    // ── Host interface ──

    /** Set the graph viewport in absolute screen coordinates. */
    public void setViewport(int x, int y, int w, int h) {
        this.vpX = x;
        this.vpY = y;
        this.vpW = Math.max(1, w);
        this.vpH = Math.max(1, h);
    }

    public ResourceLocation getSelectedId() {
        return selectedId;
    }

    public void setSelectedId(ResourceLocation id) {
        this.selectedId = id;
    }

    /** Rebuild when the registry changes (e.g. reload). Call once per client tick. */
    public void tick() {
        int currentSize = ResearchRegistry.size();
        if (currentSize != lastRegistrySize) {
            graphDirty = true;
            lastRegistrySize = currentSize;
        }
        if (graphDirty) {
            graphDirty = false;
            buildGraph();
            clampPan();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Graph construction
    // ══════════════════════════════════════════════════════════════

    public void buildGraph() {
        List<ResearchDefinition> defs = new ArrayList<>(ResearchRegistry.getAll());
        defs.sort(Comparator.comparingInt((ResearchDefinition d) -> d.getTier().ordinal())
                .thenComparing(ResearchDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        Map<ResourceLocation, List<Dependency>> dependencies = new HashMap<>();
        for (ResearchDefinition def : defs) {
            List<Dependency> deps = new ArrayList<>();
            collectDependencies(def.getPrerequisites(), EdgeStyle.SINGLE, deps);
            dependencies.put(def.getId(), deps);
        }

        Map<ResourceLocation, Integer> depthCache = new HashMap<>();
        for (ResearchDefinition def : defs) {
            computeDepth(def.getId(), dependencies, depthCache, new HashSet<>());
        }

        graphEdges.clear();
        nodesById.clear();
        nodeBoxes.clear();

        Map<Integer, List<NodeBox>> layerMap = new HashMap<>();
        for (ResearchDefinition def : defs) {
            int depth = depthCache.getOrDefault(def.getId(), 0);
            NodeBox box = new NodeBox(def);
            nodesById.put(def.getId(), box);
            layerMap.computeIfAbsent(depth, d -> new ArrayList<>()).add(box);
            nodeBoxes.add(box);
        }

        for (Map.Entry<ResourceLocation, List<Dependency>> entry : dependencies.entrySet()) {
            for (Dependency dep : entry.getValue()) {
                if (nodesById.containsKey(dep.sourceId())) {
                    graphEdges.add(new GraphEdge(dep.sourceId(), entry.getKey(), dep.style()));
                }
            }
        }

        int maxLayerSize = 1;
        for (List<NodeBox> boxes : layerMap.values()) {
            maxLayerSize = Math.max(maxLayerSize, boxes.size());
        }

        List<Integer> sortedLayers = new ArrayList<>(layerMap.keySet());
        Collections.sort(sortedLayers);
        for (Integer layer : sortedLayers) {
            List<NodeBox> layerNodes = layerMap.get(layer);
            layerNodes.sort(Comparator
                    .comparingInt((NodeBox n) -> n.def.getTier().ordinal())
                    .thenComparing(n -> n.def.getDisplayName(), String.CASE_INSENSITIVE_ORDER));

            int n = layerNodes.size();
            int rowOffset = (maxLayerSize - n) * (LAYER_Y_GAP / 2);
            for (int i = 0; i < n; i++) {
                NodeBox node = layerNodes.get(i);
                node.worldX = layer * LAYER_X_GAP;
                node.worldY = rowOffset + i * LAYER_Y_GAP;
            }
        }

        recalcGraphBounds();

        if (selectedId != null && !nodesById.containsKey(selectedId)) {
            selectedId = null;
        }
    }

    private static void collectDependencies(Prerequisite prereq, EdgeStyle inheritedStyle, List<Dependency> out) {
        if (prereq instanceof NonePrerequisite) {
            return;
        }
        if (prereq instanceof SinglePrerequisite single) {
            ResourceLocation source = ResourceLocation.tryParse(single.getResearchId());
            if (source != null) {
                out.add(new Dependency(source, inheritedStyle));
            }
            return;
        }
        if (prereq instanceof AndPrerequisite and) {
            for (Prerequisite child : and.getChildren()) {
                collectDependencies(child, EdgeStyle.AND, out);
            }
            return;
        }
        if (prereq instanceof OrPrerequisite or) {
            for (Prerequisite child : or.getChildren()) {
                collectDependencies(child, EdgeStyle.OR, out);
            }
        }
    }

    private static int computeDepth(
            ResourceLocation id,
            Map<ResourceLocation, List<Dependency>> dependencies,
            Map<ResourceLocation, Integer> depthCache,
            Set<ResourceLocation> active
    ) {
        Integer cached = depthCache.get(id);
        if (cached != null) {
            return cached;
        }
        if (!active.add(id)) {
            return 0;
        }

        List<Dependency> deps = dependencies.getOrDefault(id, List.of());
        int depth;
        if (deps.isEmpty()) {
            depth = 0;
        } else {
            int max = 0;
            for (Dependency dep : deps) {
                max = Math.max(max, computeDepth(dep.sourceId(), dependencies, depthCache, active) + 1);
            }
            depth = max;
        }

        active.remove(id);
        depthCache.put(id, depth);
        return depth;
    }

    private void recalcGraphBounds() {
        if (nodeBoxes.isEmpty()) {
            graphMinX = 0;
            graphMinY = 0;
            graphMaxX = NODE_W;
            graphMaxY = NODE_H;
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeBox node : nodeBoxes) {
            minX = Math.min(minX, node.worldX);
            maxX = Math.max(maxX, node.worldX + NODE_W);
            minY = Math.min(minY, node.worldY);
            maxY = Math.max(maxY, node.worldY + NODE_H);
        }
        graphMinX = minX;
        graphMinY = minY;
        graphMaxX = maxX;
        graphMaxY = maxY;
    }

    /** Centre and zoom the graph so it fits the current viewport. */
    public void fitToViewport() {
        if (nodeBoxes.isEmpty()) {
            panX = 0;
            panY = 0;
            return;
        }

        int graphWidth = Math.max(1, graphMaxX - graphMinX);
        int graphHeight = Math.max(1, graphMaxY - graphMinY);
        float fitX = (vpW - 16f) / graphWidth;
        float fitY = (vpH - 16f) / graphHeight;
        zoom = Math.max(0.20f, Math.min(1.2f, Math.min(fitX, fitY)));

        int viewportCenterX = vpW / 2;
        int viewportCenterY = vpH / 2;
        int graphCenterX = graphMinX + graphWidth / 2;
        int graphCenterY = graphMinY + graphHeight / 2;
        panX = viewportCenterX - Math.round(graphCenterX * zoom);
        panY = viewportCenterY - Math.round(graphCenterY * zoom);
        clampPan();
    }

    private void clampPan() {
        int scaledMinX = Math.round(graphMinX * zoom);
        int scaledMaxX = Math.round(graphMaxX * zoom);
        int scaledMinY = Math.round(graphMinY * zoom);
        int scaledMaxY = Math.round(graphMaxY * zoom);

        int minPanX = vpW - scaledMaxX - 8;
        int maxPanX = 8 - scaledMinX;
        int minPanY = vpH - scaledMaxY - 8;
        int maxPanY = 8 - scaledMinY;

        if (minPanX > maxPanX) {
            int center = (minPanX + maxPanX) / 2;
            minPanX = center;
            maxPanX = center;
        }
        if (minPanY > maxPanY) {
            int center = (minPanY + maxPanY) / 2;
            minPanY = center;
            maxPanY = center;
        }

        panX = Math.max(minPanX, Math.min(maxPanX, panX));
        panY = Math.max(minPanY, Math.min(maxPanY, panY));
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════

    /** Draw the graph viewport (background, edges, nodes, legend). */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(vpX, vpY, vpX + vpW, vpY + vpH, GRAPH_BG);
        g.fill(vpX, vpY, vpX + vpW, vpY + 1, ScreenRenderHelper.PANEL_BORDER_DARK);
        g.fill(vpX, vpY, vpX + 1, vpY + vpH, ScreenRenderHelper.PANEL_BORDER_DARK);
        g.fill(vpX + vpW - 1, vpY, vpX + vpW, vpY + vpH, ScreenRenderHelper.PANEL_BORDER_LIGHT);
        g.fill(vpX, vpY + vpH - 1, vpX + vpW, vpY + vpH, ScreenRenderHelper.PANEL_BORDER_LIGHT);

        g.enableScissor(vpX + 1, vpY + 1, vpX + vpW - 1, vpY + vpH - 1);
        drawEdges(g);
        drawNodes(g);
        g.disableScissor();
    }

    /**
     * Draw a compact edge legend (solid teal = all/AND, dashed amber = any/OR, solid grey = prereq)
     * starting at the given position. Meant for the screen's top bar, not inside the graph.
     *
     * @return the x coordinate just past the legend, so callers can lay out following content.
     */
    public int renderLegend(GuiGraphics g, int x, int y) {
        int lx = x;
        int lineY = y + 3;
        int labelColor = 0xFFAAB0C0;

        // AND — solid teal
        g.fill(lx, lineY, lx + 10, lineY + 1, EDGE_AND);
        g.drawString(font, "all", lx + 12, y, labelColor, false);
        lx += 12 + font.width("all") + 8;

        // OR — dashed amber
        for (int px = lx; px < lx + 10; px += DASH_PERIOD) {
            g.fill(px, lineY, Math.min(px + DASH_ON, lx + 10), lineY + 1, EDGE_OR);
        }
        g.drawString(font, "any", lx + 12, y, labelColor, false);
        lx += 12 + font.width("any") + 8;

        // SINGLE — solid grey
        g.fill(lx, lineY, lx + 10, lineY + 1, EDGE_SINGLE);
        g.drawString(font, "req", lx + 12, y, labelColor, false);
        lx += 12 + font.width("req");

        return lx;
    }

    private void drawEdges(GuiGraphics g) {
        for (GraphEdge edge : graphEdges) {
            NodeBox from = nodesById.get(edge.from());
            NodeBox to = nodesById.get(edge.to());
            if (from == null || to == null) continue;

            int sx = worldToScreenX(from.worldX + NODE_W);
            int sy = worldToScreenY(from.worldY + NODE_H / 2);
            int tx = worldToScreenX(to.worldX);
            int ty = worldToScreenY(to.worldY + NODE_H / 2);

            int midX = sx + (tx - sx) / 2;
            // AND = solid teal (all required), OR = dashed amber (any one),
            // SINGLE = solid grey (lone prerequisite).
            int color;
            boolean dashed;
            switch (edge.style()) {
                case AND -> { color = EDGE_AND; dashed = false; }
                case OR -> { color = EDGE_OR; dashed = true; }
                default -> { color = EDGE_SINGLE; dashed = false; }
            }
            drawPolyline(g, sx, sy, midX, sy, midX, ty, tx, ty, color, dashed);
        }
    }

    private void drawPolyline(GuiGraphics g, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4,
                              int color, boolean dashed) {
        drawLine(g, x1, y1, x2, y2, color, dashed);
        drawLine(g, x2, y2, x3, y3, color, dashed);
        drawLine(g, x3, y3, x4, y4, color, dashed);
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, boolean dashed) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            if (dashed) {
                for (int py = minY; py < maxY; py += DASH_PERIOD) {
                    g.fill(x1, py, x1 + 1, Math.min(py + DASH_ON, maxY) + 1, color);
                }
            } else {
                g.fill(x1, minY, x1 + 1, maxY + 1, color);
            }
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            if (dashed) {
                for (int px = minX; px < maxX; px += DASH_PERIOD) {
                    g.fill(px, y1, Math.min(px + DASH_ON, maxX) + 1, y1 + 1, color);
                }
            } else {
                g.fill(minX, y1, maxX + 1, y1 + 1, color);
            }
        }
    }

    private void drawNodes(GuiGraphics g) {
        Set<String> completed = menu.getCompletedResearch();
        for (NodeBox node : nodeBoxes) {
            int sx = worldToScreenX(node.worldX);
            int sy = worldToScreenY(node.worldY);
            int sw = Math.max(52, Math.round(NODE_W * zoom));
            int sh = Math.max(20, Math.round(NODE_H * zoom));

            boolean done = completed.contains(node.def.getId().toString());
            boolean locked = !ResearchRequirements.prereqMet(menu, node.def);
            boolean selected = node.def.getId().equals(selectedId);

            int tierColor = node.def.getTier().getColor() | 0xFF000000;
            int fill = locked ? 0xFF30303A : 0xFF20242F;
            if (done) {
                fill = 0xFF1E3A2A;
            }

            g.fill(sx, sy, sx + sw, sy + sh, fill);
            g.fill(sx, sy, sx + sw, sy + 1, tierColor);
            g.fill(sx, sy, sx + 1, sy + sh, tierColor);
            g.fill(sx + sw - 1, sy, sx + sw, sy + sh, selected ? 0xFFFFFFFF : 0xFF5B617B);
            g.fill(sx, sy + sh - 1, sx + sw, sy + sh, selected ? 0xFFFFFFFF : 0xFF5B617B);

            String name = trimToWidth(node.def.getDisplayName(), sw - 8);
            int textColor = locked ? 0xFF8D8D99 : 0xFFE4E7EF;
            g.drawString(font, name, sx + 4, sy + 4, textColor, false);

            if (zoom >= 0.72f) {
                String stateText = done ? "[DONE]" : (locked ? "[LOCKED]" : "[READY]");
                int stateColor = done ? 0xFF78DD78 : (locked ? 0xFFDD7777 : 0xFFAAD3FF);
                g.drawString(font, stateText, sx + 4, sy + 17, stateColor, false);
            }
        }
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 2 && font.width(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private int worldToScreenX(int worldX) {
        return vpX + panX + Math.round(worldX * zoom);
    }

    private int worldToScreenY(int worldY) {
        return vpY + panY + Math.round(worldY * zoom);
    }

    private Optional<NodeBox> getHoveredNode(double mouseX, double mouseY) {
        if (mouseX < vpX || mouseX >= vpX + vpW || mouseY < vpY || mouseY >= vpY + vpH) {
            return Optional.empty();
        }

        for (NodeBox node : nodeBoxes) {
            int sx = worldToScreenX(node.worldX);
            int sy = worldToScreenY(node.worldY);
            int sw = Math.max(52, Math.round(NODE_W * zoom));
            int sh = Math.max(20, Math.round(NODE_H * zoom));
            if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /** Render the hovered node's tooltip, if any. Call after the screen's own render. */
    public void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        getHoveredNode(mouseX, mouseY).ifPresent(node -> renderNodeTooltip(g, node, mouseX, mouseY));
    }

    private void renderNodeTooltip(GuiGraphics g, NodeBox node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        boolean done = menu.getCompletedResearch().contains(node.def.getId().toString());
        boolean locked = !ResearchRequirements.prereqMet(menu, node.def);

        lines.add(Component.literal(node.def.getDisplayName())
                .withStyle(s -> s.withColor(node.def.getTier().getColor())));
        lines.add(Component.literal("Tier: " + node.def.getTier().getDisplayName())
                .withStyle(s -> s.withColor(0xAAB0C0)));

        String state = done ? "Completed" : (locked ? "Locked" : "Available");
        int stateColor = done ? 0x55FF55 : (locked ? 0xFF6666 : 0x66B3FF);
        lines.add(Component.literal("Status: " + state)
                .withStyle(s -> s.withColor(stateColor)));

        if (!node.def.getItemCosts().isEmpty()) {
            lines.add(Component.literal("Costs:").withStyle(s -> s.withColor(0xDDCC66)));
            for (ItemCost cost : node.def.getItemCosts()) {
                lines.add(Component.literal(" - " + cost.getItem().getDescription().getString() + " x" + cost.count())
                        .withStyle(s -> s.withColor(0xC9CEDC)));
            }
        }

        FluidCost fluidCost = node.def.getFluidCost();
        if (fluidCost != null) {
            lines.add(Component.literal("Fluid: " + fluidCost.amount() + " mB " + fluidCost.getFluidName())
                    .withStyle(s -> s.withColor(0x66D9EF)));
        }

        if (node.def.getIdeaChip().isPresent()) {
            ItemStack chip = node.def.getIdeaChip().get();
            lines.add(Component.literal("Idea Chip: " + chip.getHoverName().getString())
                    .withStyle(s -> s.withColor(0xFFAA55)));
        }

        if (!(node.def.getPrerequisites() instanceof NonePrerequisite)) {
            lines.add(Component.literal("Requires: " + node.def.getPrerequisites().describe())
                    .withStyle(s -> s.withColor(0xA0A7BE)));
        }

        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ══════════════════════════════════════════════════════════════
    //  Input (forwarded by the host screen while the Tree tab is active)
    // ══════════════════════════════════════════════════════════════

    /** @return true if the click was consumed (node selected or pan started). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Optional<NodeBox> hovered = getHoveredNode(mouseX, mouseY);
        if (hovered.isPresent() && button == 0) {
            selectedId = hovered.get().def.getId();
            return true;
        }

        if (isInside(mouseX, mouseY) && button == 1) {
            dragging = true;
            dragLastX = mouseX;
            dragLastY = mouseY;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (dragging && button == 1) {
            panX += (int) Math.round(mouseX - dragLastX);
            panY += (int) Math.round(mouseY - dragLastY);
            dragLastX = mouseX;
            dragLastY = mouseY;
            clampPan();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 1) {
            dragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isInside(mouseX, mouseY)) {
            return false;
        }

        float delta = scrollY > 0 ? 0.1f : -0.1f;
        float prevZoom = zoom;
        zoom = Math.max(0.20f, Math.min(2.0f, zoom + delta));
        if (zoom != prevZoom) {
            float worldX = (float) ((mouseX - vpX - panX) / prevZoom);
            float worldY = (float) ((mouseY - vpY - panY) / prevZoom);
            panX = Math.round((float) (mouseX - vpX) - worldX * zoom);
            panY = Math.round((float) (mouseY - vpY) - worldY * zoom);
            clampPan();
        }
        return true;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= vpX && mouseX < vpX + vpW && mouseY >= vpY && mouseY < vpY + vpH;
    }
}
