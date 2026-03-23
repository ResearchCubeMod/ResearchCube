package com.researchcube.client.screen;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.menu.ResearchTableMenu;
import com.researchcube.network.CancelResearchPacket;
import com.researchcube.network.StartResearchPacket;
import com.researchcube.network.WipeTankPacket;
import com.researchcube.registry.ModFluids;
import com.researchcube.research.FluidCost;
import com.researchcube.research.ItemCost;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.prerequisite.AndPrerequisite;
import com.researchcube.research.prerequisite.NonePrerequisite;
import com.researchcube.research.prerequisite.OrPrerequisite;
import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.SinglePrerequisite;
import com.researchcube.util.IdeaChipMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

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
 * Alternative research UI that visualizes the research dependency graph.
 *
 * Uses same layout dimensions as ResearchTableScreen (470x260) to match menu slot positions.
 * Upper panel: Graph viewport with zoom/pan
 * Lower section: Machine panel (Drive, Cube, Costs, etc.) + Player inventory
 */
public class ResearchTreeScreen extends AbstractContainerScreen<ResearchTableMenu> {

    // ── Texture ──
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchCubeMod.MOD_ID, "textures/gui/research_table.png");
    private static final int TEX_W = ResearchTableMenu.GUI_WIDTH;
    private static final int TEX_H = ResearchTableMenu.GUI_HEIGHT;

    // Colors (for dynamic elements)
    private static final int PANEL_BORDER_LIGHT = 0xFF7E87A6;
    private static final int PANEL_BORDER_DARK = 0xFF1A1A1A;
    private static final int GRAPH_BG = 0xFF171A26;

    private static final int EDGE_SINGLE = 0xFF9CA3AF;
    private static final int EDGE_AND = 0xFF2DD4BF;
    private static final int EDGE_OR = 0xFFF59E0B;

    private static final int NODE_W = 116;
    private static final int NODE_H = 36;
    private static final int LAYER_X_GAP = 178;
    private static final int LAYER_Y_GAP = 64;

    // Graph viewport (uses same upper panel area as list view)
    private static final int GRAPH_X = ResearchTableMenu.UPPER_PANEL_X + 8;
    private static final int GRAPH_Y = ResearchTableMenu.UPPER_PANEL_Y + 24;
    private static final int GRAPH_W = ResearchTableMenu.UPPER_PANEL_W - 16;
    private static final int GRAPH_H = ResearchTableMenu.UPPER_PANEL_H - 28;

    private enum EdgeStyle {
        SINGLE,
        AND,
        OR
    }

    private static class NodeBox {
        private final ResearchDefinition def;
        private int worldX;
        private int worldY;

        private NodeBox(ResearchDefinition def) {
            this.def = def;
        }
    }

    private record GraphEdge(ResourceLocation from, ResourceLocation to, EdgeStyle style) {}

    private record Dependency(ResourceLocation sourceId, EdgeStyle style) {}

    private final List<NodeBox> nodeBoxes = new ArrayList<>();
    private final List<GraphEdge> graphEdges = new ArrayList<>();
    private final Map<ResourceLocation, NodeBox> nodesById = new HashMap<>();

    private ResourceLocation selectedId;

    private float zoom = 1.0f;
    private int panX = 0;
    private int panY = 0;
    private boolean dragging;
    private double dragLastX;
    private double dragLastY;

    private Button startButton;
    private Button cancelButton;
    private Button wipeButton;
    private Button listButton;

    private int graphMinX;
    private int graphMinY;
    private int graphMaxX;
    private int graphMaxY;
    private int refreshTicks = 0;
    private int lastRegistrySize = -1;
    private boolean graphDirty = true;

    public ResearchTreeScreen(ResearchTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = ResearchTableMenu.GUI_WIDTH;
        this.imageHeight = ResearchTableMenu.GUI_HEIGHT;
        this.inventoryLabelX = ResearchTableMenu.PLAYER_INV_X;
        this.inventoryLabelY = ResearchTableMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();

        // Start button
        this.startButton = addRenderableWidget(Button.builder(Component.literal("Start"), b -> onStartResearch())
                .bounds(leftPos + ResearchTableMenu.START_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build());

        // Cancel/Stop button
        this.cancelButton = addRenderableWidget(Button.builder(Component.literal("Stop"), b -> onCancelResearch())
                .bounds(leftPos + ResearchTableMenu.STOP_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build());

        // Wipe tank button
        this.wipeButton = addRenderableWidget(Button.builder(Component.literal("Wipe"), b -> onWipeTank())
                .bounds(leftPos + ResearchTableMenu.WIPE_BTN_X, topPos + ResearchTableMenu.BUTTON_Y,
                        ResearchTableMenu.BUTTON_W, ResearchTableMenu.BUTTON_H)
                .build());

        // List view button (switches back to list view)
        this.listButton = addRenderableWidget(Button.builder(Component.literal("List"), b -> openListView())
                .bounds(leftPos + ResearchTableMenu.LIST_BTN_X, topPos + ResearchTableMenu.TREE_BTN_Y,
                        ResearchTableMenu.LIST_BTN_W, ResearchTableMenu.LIST_BTN_H)
                .build());

        buildGraph();
        fitGraphToViewport();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshTicks++;
        if (refreshTicks % 20 == 0) {
            int currentSize = ResearchRegistry.size();
            if (currentSize != lastRegistrySize) {
                graphDirty = true;
                lastRegistrySize = currentSize;
            }
        }
        if (graphDirty) {
            graphDirty = false;
            buildGraph();
            clampPan();
        }

        // Update button states (same logic as ResearchTableScreen)
        ResearchDefinition selectedDef = getSelectedDefinition();
        boolean canStart = !menu.isResearching() && selectedDef != null && canStartResearch(selectedDef);
        startButton.active = canStart;
        cancelButton.active = menu.isResearching();
        wipeButton.active = menu.getFluidAmount() > 0;
    }

    private void openListView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new ResearchTableScreen(menu, mc.player.getInventory(), this.title));
    }

    private void onStartResearch() {
        if (selectedId == null || menu.isResearching()) return;
        ResearchDefinition def = ResearchRegistry.get(selectedId);
        if (def == null || !canStartResearch(def)) return;

        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new StartResearchPacket(be.getBlockPos(), selectedId.toString()));
    }

    private void onCancelResearch() {
        if (!menu.isResearching()) return;
        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new CancelResearchPacket(be.getBlockPos()));
    }

    private void onWipeTank() {
        ResearchTableBlockEntity be = menu.getBlockEntity();
        PacketDistributor.sendToServer(new WipeTankPacket(be.getBlockPos()));
    }

    private boolean canStartResearch(ResearchDefinition def) {
        if (!isPrerequisiteMet(def)) return false;
        if (!isIdeaChipSatisfied(def)) return false;
        if (!hasRequiredItems(def)) return false;
        if (!hasRequiredFluid(def)) return false;
        return true;
    }

    private boolean hasRequiredItems(ResearchDefinition def) {
        if (def.getItemCosts().isEmpty()) return true;
        Map<Item, Integer> available = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            int slotIndex = ResearchTableBlockEntity.COST_SLOT_START + i;
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        for (ItemCost cost : def.getItemCosts()) {
            int availableCount = available.getOrDefault(cost.getItem(), 0);
            if (availableCount < cost.count()) return false;
        }
        return true;
    }

    private boolean hasRequiredFluid(ResearchDefinition def) {
        FluidCost fluidCost = def.getFluidCost();
        if (fluidCost == null) return true;
        int tankFluidType = menu.getFluidType();
        int tankAmount = menu.getFluidAmount();
        int requiredType = ModFluids.getFluidIndex(fluidCost.getFluid());
        if (tankFluidType != requiredType) return false;
        return tankAmount >= fluidCost.amount();
    }

    private boolean isIdeaChipSatisfied(ResearchDefinition def) {
        if (def.getIdeaChip().isEmpty()) return true;
        ItemStack required = def.getIdeaChip().get();
        ItemStack candidate = menu.getSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP).getItem();
        return IdeaChipMatcher.matches(required, candidate);
    }

    private ResearchDefinition getSelectedDefinition() {
        if (selectedId == null) return null;
        return ResearchRegistry.get(selectedId);
    }

    private boolean isPrerequisiteMet(ResearchDefinition def) {
        return def.getPrerequisites().isSatisfied(menu.getCompletedResearch());
    }

    private void buildGraph() {
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

    private void fitGraphToViewport() {
        if (nodeBoxes.isEmpty()) {
            panX = 0;
            panY = 0;
            return;
        }

        int graphWidth = Math.max(1, graphMaxX - graphMinX);
        int graphHeight = Math.max(1, graphMaxY - graphMinY);
        float fitX = (GRAPH_W - 16f) / graphWidth;
        float fitY = (GRAPH_H - 16f) / graphHeight;
        zoom = Math.max(0.20f, Math.min(1.2f, Math.min(fitX, fitY)));

        int viewportCenterX = GRAPH_W / 2;
        int viewportCenterY = GRAPH_H / 2;
        int graphCenterX = graphMinX + graphWidth / 2;
        int graphCenterY = graphMinY + graphHeight / 2;
        panX = viewportCenterX - Math.round(graphCenterX * zoom);
        panY = viewportCenterY - Math.round(graphCenterY * zoom);
        clampPan();
    }

    private void adjustZoom(float delta) {
        float prevZoom = zoom;
        zoom = Math.max(0.20f, Math.min(2.0f, zoom + delta));
        if (prevZoom == zoom) {
            return;
        }

        float centerWorldX = (GRAPH_W * 0.5f - panX) / prevZoom;
        float centerWorldY = (GRAPH_H * 0.5f - panY) / prevZoom;
        panX = Math.round(GRAPH_W * 0.5f - centerWorldX * zoom);
        panY = Math.round(GRAPH_H * 0.5f - centerWorldY * zoom);
        clampPan();
    }

    private void clampPan() {
        int scaledMinX = Math.round(graphMinX * zoom);
        int scaledMaxX = Math.round(graphMaxX * zoom);
        int scaledMinY = Math.round(graphMinY * zoom);
        int scaledMaxY = Math.round(graphMaxY * zoom);

        int minPanX = GRAPH_W - scaledMaxX - 8;
        int maxPanX = 8 - scaledMinX;
        int minPanY = GRAPH_H - scaledMaxY - 8;
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

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // ── Static background from texture (same as ResearchTableScreen) ──
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        // ── Slot labels (above slot row) ──
        int labelY = y + ResearchTableMenu.LABEL_Y;
        g.drawString(font, "Dr", x + ResearchTableMenu.DRIVE_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Cb", x + ResearchTableMenu.CUBE_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Id", x + ResearchTableMenu.IDEA_CHIP_X + 2, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Costs", x + ResearchTableMenu.COST_X, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "Fl", x + ResearchTableMenu.FLUID_GAUGE_X + 3, labelY, 0xFFD3D7E5, false);
        g.drawString(font, "I/O", x + ResearchTableMenu.BUCKET_IN_X, labelY, 0xFFD3D7E5, false);

        // ── Idea chip dynamic overlay ──
        renderIdeaChipOverlay(g, x + ResearchTableMenu.IDEA_CHIP_X, y + ResearchTableMenu.IDEA_CHIP_Y);

        // ── Fluid gauge (dynamic) ──
        drawFluidGauge(g, x + ResearchTableMenu.FLUID_GAUGE_X, y + ResearchTableMenu.FLUID_GAUGE_Y,
                ResearchTableMenu.FLUID_GAUGE_W, ResearchTableMenu.FLUID_GAUGE_H);

        // Graph viewport (render on top of texture background)
        int gx = x + GRAPH_X;
        int gy = y + GRAPH_Y;
        g.fill(gx, gy, gx + GRAPH_W, gy + GRAPH_H, GRAPH_BG);
        g.fill(gx, gy, gx + GRAPH_W, gy + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + 1, gy + GRAPH_H, PANEL_BORDER_DARK);
        g.fill(gx + GRAPH_W - 1, gy, gx + GRAPH_W, gy + GRAPH_H, PANEL_BORDER_LIGHT);
        g.fill(gx, gy + GRAPH_H - 1, gx + GRAPH_W, gy + GRAPH_H, PANEL_BORDER_LIGHT);

        g.enableScissor(gx + 1, gy + 1, gx + GRAPH_W - 1, gy + GRAPH_H - 1);
        drawEdges(g, gx, gy);
        drawNodes(g, gx, gy);
        g.disableScissor();

        // ── Edge legend (inside graph viewport, top-left corner) ──
        int legendX = gx + 4;
        int legendY = gy + 4;
        g.fill(legendX - 2, legendY - 2, legendX + 62, legendY + 12, 0xAA1A1E2A);
        g.drawString(font, "AND", legendX, legendY, EDGE_AND, false);
        g.drawString(font, "OR", legendX + 22, legendY, EDGE_OR, false);
        g.drawString(font, "S", legendX + 38, legendY, EDGE_SINGLE, false);
    }

    private void renderIdeaChipOverlay(GuiGraphics g, int sx, int sy) {
        ResearchDefinition selected = getSelectedDefinition();
        if (selected == null || selected.getIdeaChip().isEmpty()) {
            g.fill(sx, sy, sx + 16, sy + 16, 0x88000000);
        } else if (!isIdeaChipSatisfied(selected)) {
            int x0 = sx - 1;
            int y0 = sy - 1;
            g.fill(x0, y0, x0 + 18, y0 + 1, 0xFFFF3333);
            g.fill(x0, y0 + 17, x0 + 18, y0 + 18, 0xFFFF3333);
            g.fill(x0, y0, x0 + 1, y0 + 18, 0xFFFF3333);
            g.fill(x0 + 17, y0, x0 + 18, y0 + 18, 0xFFFF3333);
        }
    }

    private void drawFluidGauge(GuiGraphics g, int gx, int gy, int gw, int gh) {
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_DARK);
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF222222);

        int fluidAmount = menu.getFluidAmount();
        int fluidType = menu.getFluidType();
        if (fluidAmount > 0 && fluidType > 0) {
            int fillHeight = Math.min(gh, Math.round((float) gh * fluidAmount / ResearchTableBlockEntity.TANK_CAPACITY));
            int fillY = gy + gh - fillHeight;
            int color = ModFluids.getFluidColor(fluidType);
            g.fill(gx, fillY, gx + gw, gy + gh, color);

            if (fillHeight > 2) {
                int shine = (color & 0x00FFFFFF) | 0x44000000;
                g.fill(gx, fillY, gx + gw, fillY + 1, shine);
            }
        }

        g.fill(gx + gw, gy - 1, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
        g.fill(gx - 1, gy + gh, gx + gw + 1, gy + gh + 1, PANEL_BORDER_LIGHT);
    }

    private void drawEdges(GuiGraphics g, int graphScreenX, int graphScreenY) {
        for (GraphEdge edge : graphEdges) {
            NodeBox from = nodesById.get(edge.from());
            NodeBox to = nodesById.get(edge.to());
            if (from == null || to == null) continue;

            int sx = worldToScreenX(from.worldX + NODE_W, graphScreenX);
            int sy = worldToScreenY(from.worldY + NODE_H / 2, graphScreenY);
            int tx = worldToScreenX(to.worldX, graphScreenX);
            int ty = worldToScreenY(to.worldY + NODE_H / 2, graphScreenY);

            int midX = sx + (tx - sx) / 2;
            int color = switch (edge.style()) {
                case AND -> EDGE_AND;
                case OR -> EDGE_OR;
                case SINGLE -> EDGE_SINGLE;
            };

            if (edge.style() == EdgeStyle.OR) {
                drawPolyline(g, sx, sy - 1, midX, sy - 1, midX, ty - 1, tx, ty - 1, color);
                drawPolyline(g, sx, sy + 1, midX, sy + 1, midX, ty + 1, tx, ty + 1, color);
            } else {
                drawPolyline(g, sx, sy, midX, sy, midX, ty, tx, ty, color);
            }
        }
    }

    private void drawPolyline(GuiGraphics g, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int color) {
        drawLine(g, x1, y1, x2, y2, color);
        drawLine(g, x2, y2, x3, y3, color);
        drawLine(g, x3, y3, x4, y4, color);
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            g.fill(x1, minY, x1 + 1, maxY + 1, color);
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            g.fill(minX, y1, maxX + 1, y1 + 1, color);
        }
    }

    private void drawNodes(GuiGraphics g, int graphScreenX, int graphScreenY) {
        for (NodeBox node : nodeBoxes) {
            int sx = worldToScreenX(node.worldX, graphScreenX);
            int sy = worldToScreenY(node.worldY, graphScreenY);
            int sw = Math.max(52, Math.round(NODE_W * zoom));
            int sh = Math.max(20, Math.round(NODE_H * zoom));

            boolean completed = menu.getCompletedResearch().contains(node.def.getId().toString());
            boolean locked = !isPrerequisiteMet(node.def);
            boolean selected = node.def.getId().equals(selectedId);

            int tierColor = node.def.getTier().getColor() | 0xFF000000;
            int fill = locked ? 0xFF30303A : 0xFF20242F;
            if (completed) {
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
                String stateText = completed ? "[DONE]" : (locked ? "[LOCKED]" : "[READY]");
                int stateColor = completed ? 0xFF78DD78 : (locked ? 0xFFDD7777 : 0xFFAAD3FF);
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

    private int worldToScreenX(int worldX, int graphScreenX) {
        return graphScreenX + panX + Math.round(worldX * zoom);
    }

    private int worldToScreenY(int worldY, int graphScreenY) {
        return graphScreenY + panY + Math.round(worldY * zoom);
    }

    private Optional<NodeBox> getHoveredNode(double mouseX, double mouseY) {
        int gx = leftPos + GRAPH_X;
        int gy = topPos + GRAPH_Y;

        if (mouseX < gx || mouseX >= gx + GRAPH_W || mouseY < gy || mouseY >= gy + GRAPH_H) {
            return Optional.empty();
        }

        for (NodeBox node : nodeBoxes) {
            int sx = worldToScreenX(node.worldX, gx);
            int sy = worldToScreenY(node.worldY, gy);
            int sw = Math.max(52, Math.round(NODE_W * zoom));
            int sh = Math.max(20, Math.round(NODE_H * zoom));
            if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Optional<NodeBox> hovered = getHoveredNode(mouseX, mouseY);
        if (hovered.isPresent() && button == 0) {
            selectedId = hovered.get().def.getId();
            return true;
        }

        int gx = leftPos + GRAPH_X;
        int gy = topPos + GRAPH_Y;
        if (mouseX >= gx && mouseX < gx + GRAPH_W && mouseY >= gy && mouseY < gy + GRAPH_H && button == 1) {
            dragging = true;
            dragLastX = mouseX;
            dragLastY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 1) {
            panX += (int) Math.round(mouseX - dragLastX);
            panY += (int) Math.round(mouseY - dragLastY);
            dragLastX = mouseX;
            dragLastY = mouseY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int gx = leftPos + GRAPH_X;
        int gy = topPos + GRAPH_Y;
        if (mouseX < gx || mouseX >= gx + GRAPH_W || mouseY < gy || mouseY >= gy + GRAPH_H) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        float delta = scrollY > 0 ? 0.1f : -0.1f;
        float prevZoom = zoom;
        zoom = Math.max(0.20f, Math.min(2.0f, zoom + delta));
        if (zoom != prevZoom) {
            float worldX = (float) ((mouseX - gx - panX) / prevZoom);
            float worldY = (float) ((mouseY - gy - panY) / prevZoom);
            panX = Math.round((float) (mouseX - gx) - worldX * zoom);
            panY = Math.round((float) (mouseY - gy) - worldY * zoom);
            clampPan();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Node tooltip
        Optional<NodeBox> hovered = getHoveredNode(mouseX, mouseY);
        hovered.ifPresent(node -> renderNodeTooltip(graphics, node, mouseX, mouseY));

        // Fluid gauge tooltip
        renderFluidGaugeTooltip(graphics, mouseX, mouseY);

        // Idea chip tooltip
        renderIdeaChipTooltip(graphics, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderFluidGaugeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int gx = leftPos + ResearchTableMenu.FLUID_GAUGE_X;
        int gy = topPos + ResearchTableMenu.FLUID_GAUGE_Y;
        int gw = ResearchTableMenu.FLUID_GAUGE_W;
        int gh = ResearchTableMenu.FLUID_GAUGE_H;

        if (mouseX < gx - 1 || mouseX >= gx + gw + 1 || mouseY < gy - 1 || mouseY >= gy + gh + 1) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        int fluidAmount = menu.getFluidAmount();
        int fluidType = menu.getFluidType();

        if (fluidAmount > 0 && fluidType > 0) {
            int color = ModFluids.getFluidColor(fluidType);
            tooltip.add(Component.literal(ModFluids.getFluidName(fluidType))
                    .withStyle(s -> s.withColor(color & 0x00FFFFFF)));
            tooltip.add(Component.literal(fluidAmount + " / " + ResearchTableBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        } else {
            tooltip.add(Component.literal("Empty")
                    .withStyle(s -> s.withColor(0x888888)));
            tooltip.add(Component.literal("0 / " + ResearchTableBlockEntity.TANK_CAPACITY + " mB")
                    .withStyle(s -> s.withColor(0xBBBBBB)));
        }
        tooltip.add(Component.literal("Insert fluid buckets below")
                .withStyle(s -> s.withColor(0x666666).withItalic(true)));

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderIdeaChipTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int sx = leftPos + ResearchTableMenu.IDEA_CHIP_X;
        int sy = topPos + ResearchTableMenu.IDEA_CHIP_Y;

        if (mouseX < sx - 1 || mouseX >= sx + 17 || mouseY < sy - 1 || mouseY >= sy + 17) {
            return;
        }

        ItemStack slotStack = menu.getSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP).getItem();
        if (!slotStack.isEmpty()) return;

        List<Component> tooltip = new ArrayList<>();
        ResearchDefinition selected = getSelectedDefinition();

        if (selected == null || selected.getIdeaChip().isEmpty()) {
            tooltip.add(Component.literal("Idea Chip Slot")
                    .withStyle(s -> s.withColor(0x888888)));
            tooltip.add(Component.literal("No idea chip required for this research.")
                    .withStyle(s -> s.withColor(0x666666).withItalic(true)));
        } else {
            ItemStack required = selected.getIdeaChip().get();
            tooltip.add(Component.literal("Idea Chip Slot")
                    .withStyle(s -> s.withColor(0xFF5555)));
            tooltip.add(Component.literal("Requires: " + required.getHoverName().getString())
                    .withStyle(s -> s.withColor(0xFFAAAA)));
        }

        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderNodeTooltip(GuiGraphics graphics, NodeBox node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        boolean completed = menu.getCompletedResearch().contains(node.def.getId().toString());
        boolean locked = !isPrerequisiteMet(node.def);

        lines.add(Component.literal(node.def.getDisplayName())
                .withStyle(s -> s.withColor(node.def.getTier().getColor())));
        lines.add(Component.literal("Tier: " + node.def.getTier().getDisplayName())
                .withStyle(s -> s.withColor(0xAAB0C0)));

        String state = completed ? "Completed" : (locked ? "Locked" : "Available");
        int stateColor = completed ? 0x55FF55 : (locked ? 0xFF6666 : 0x66B3FF);
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

        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF343841, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFE6EAF5, false);

        // Researching indicator (same as ResearchTableScreen)
        if (menu.isResearching()) {
            graphics.drawString(this.font, "\u25CF Researching",
                    ResearchTableMenu.MACHINE_PANEL_X + 4, ResearchTableMenu.BUTTON_Y + 18, 0xFF77DD77, false);
        }
    }
}
