package com.researchcube.client.screen;

import com.researchcube.item.DriveItem;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchTier;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only screen showing all recipes stored on a Drive item.
 * Opened by right-clicking a filled Drive in hand (client-side only).
 */
public class DriveInspectorScreen extends Screen {

    private record RecipeDisplay(String recipeId, ItemStack output, String displayName) {}

    private final ItemStack driveStack;
    private final ResearchTier driveTier;
    private final List<RecipeDisplay> recipes = new ArrayList<>();
    private int scrollOffset = 0;

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 200;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 7;

    public DriveInspectorScreen(ItemStack drive) {
        super(Component.literal("Drive Inspector"));
        this.driveStack = drive;
        this.driveTier = drive.getItem() instanceof DriveItem di ? di.getTier() : ResearchTier.BASIC;
    }

    @Override
    protected void init() {
        super.init();
        recipes.clear();

        for (String recipeId : NbtUtil.readRecipes(driveStack)) {
            ItemStack output = RecipeOutputResolver.resolveOutput(recipeId);
            String name = output.isEmpty() ? recipeId : output.getHoverName().getString();
            if (output.getCount() > 1) {
                name += " \u00d7" + output.getCount();
            }
            recipes.add(new RecipeDisplay(recipeId, output, name));
        }

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(panelX + PANEL_W / 2 - 30, panelY + PANEL_H - 24, 60, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // Background dim
        g.fill(0, 0, width, height, 0x80101010);

        // Panel background
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF293047);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, 0xFF1E2435);
        // Border
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF3A4466);
        g.fill(panelX, panelY, panelX + 1, panelY + PANEL_H, 0xFF3A4466);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF3A4466);
        g.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF3A4466);

        // Title
        int tierColor = driveTier.getColor() | 0xFF000000;
        String title = "Drive Inspector — " + driveTier.getDisplayName();
        g.drawCenteredString(font, title, panelX + PANEL_W / 2, panelY + 6, tierColor);

        // Capacity line
        int total = NbtUtil.readRecipes(driveStack).size();
        String capStr = driveTier.hasRecipeLimit()
                ? total + "/" + driveTier.getMaxRecipes() + " recipes"
                : total + " recipes (unlimited)";
        g.drawCenteredString(font, capStr, panelX + PANEL_W / 2, panelY + 18, 0xFFAAAAAA);

        // Recipe list
        int listX = panelX + 8;
        int listY = panelY + 34;
        int listW = PANEL_W - 16;

        // List background
        ScreenRenderHelper.drawInsetPanel(g, listX, listY, listW, VISIBLE_ROWS * ROW_H + 4);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= recipes.size()) break;

            RecipeDisplay display = recipes.get(idx);
            int rowY = listY + 2 + i * ROW_H;

            // Alternating row background
            if (i % 2 == 1) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, 0x20FFFFFF);
            }

            // Item icon
            if (!display.output.isEmpty()) {
                g.renderItem(display.output, listX + 4, rowY + 2);
            }

            // Recipe output name
            g.drawString(font, display.displayName, listX + 24, rowY + 6, 0xFF55FF55, false);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            g.drawCenteredString(font, "\u25B2", listX + listW / 2, listY - 8, 0xFFAAAAAA);
        }
        if (scrollOffset + VISIBLE_ROWS < recipes.size()) {
            g.drawCenteredString(font, "\u25BC", listX + listW / 2, listY + VISIBLE_ROWS * ROW_H + 6, 0xFFAAAAAA);
        }

        // Tooltip on hover
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= recipes.size()) break;

            int rowY = listY + 2 + i * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                RecipeDisplay display = recipes.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(display.displayName).withStyle(s -> s.withColor(0x55FF55)));
                tooltip.add(Component.literal("Recipe ID: " + display.recipeId).withStyle(s -> s.withColor(0x888888)));

                // Show unlocking research
                var defs = RecipeOutputResolver.findResearchForRecipe(display.recipeId);
                for (ResearchDefinition def : defs) {
                    tooltip.add(Component.literal("Unlocked by: " + def.getDisplayName()
                            + " (" + def.getTier().getDisplayName() + ")")
                            .withStyle(s -> s.withColor(0xAAAAFF)));
                }
                g.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (scrollY < 0 && scrollOffset + VISIBLE_ROWS < recipes.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
