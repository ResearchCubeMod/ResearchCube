package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.List;

/**
 * JEI recipe category for Drive Crafting recipes.
 * Shows the drive requirement, ingredients, and the recipe output.
 * Supports both shaped (matching the pattern grid) and shapeless (sequential fill) layouts.
 *
 * <p>The layout mirrors the real Drive Crafting Table GUI arrangement players see:
 * drive slot on the left, the 3x3 craft matrix in the center, an arrow, then the result
 * slot on the right, with the drive and result vertically centered on the grid's middle
 * row, exactly as in {@link com.researchcube.client.screen.DriveCraftingTableScreen}.
 *
 * <p>Slot backgrounds and the arrow are drawn with JEI's own drawables so the layout
 * matches vanilla recipe categories; only the labels and the research-completion
 * indicator are drawn manually.
 */
public class DriveCraftingCategory implements IRecipeCategory<DriveCraftingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("drive_crafting");
    public static final RecipeType<DriveCraftingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, DriveCraftingRecipe.class);

    private static final int SLOT_STRIDE = 18;

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;

    // Slot anchors are the ingredient top-left (setStandardSlotBackground draws the 18x18
    // frame at offset -1,-1). The 3x3 grid is centered; the drive (left) and result (right)
    // sit on the grid's middle row so the whole row reads "drive | grid | arrow | result",
    // matching the machine GUI.
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_X = 30;
    private static final int GRID_Y = 12;
    private static final int GRID_MID_Y = GRID_Y + SLOT_STRIDE; // middle row of the 3x3 grid

    private static final int DRIVE_X = 6;
    private static final int DRIVE_Y = GRID_MID_Y;

    private static final int OUTPUT_X = 118;
    private static final int OUTPUT_Y = GRID_MID_Y;

    private static final int ARROW_X = 90;
    private static final int ARROW_Y = GRID_MID_Y;

    // Label row baseline, above the slots.
    private static final int LABEL_Y = 1;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public DriveCraftingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.METADATA_RECLAIMED.get()));
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.researchcube.category.drive_crafting");
    }

    @Override
    public RecipeType<DriveCraftingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void draw(DriveCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Section labels, centered over their sections (mirrors the machine GUI's labels).
        drawCentered(g, font, Component.translatable("jei.researchcube.drive_crafting.slot.drive"),
                DRIVE_X + 8, LABEL_Y, 0xFFAAAAAA);
        drawCentered(g, font, Component.translatable("jei.researchcube.drive_crafting.grid"),
                GRID_X + SLOT_STRIDE + 8, LABEL_Y, 0xFFAAAAAA);
        drawCentered(g, font, Component.translatable("jei.researchcube.drive_crafting.result"),
                OUTPUT_X + 8, LABEL_Y, 0xFFAAAAAA);

        // Arrow from grid to result (JEI's own drawable, vertically centred on the middle row).
        arrow.draw(g, ARROW_X, ARROW_Y + (SLOT_STRIDE - arrow.getHeight()) / 2);

        // Research-completion indicator under the result slot.
        JeiRenderHelper.drawCompletionIndicator(g, font, recipe.getRequiredRecipeId(),
                OUTPUT_X + 5, OUTPUT_Y + SLOT_STRIDE + 3);
    }

    private void drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DriveCraftingRecipe recipe, IFocusGroup focuses) {
        // Resolve which research unlocks this recipe and pick the matching drive tier.
        String requiredRecipeId = recipe.getRequiredRecipeId();
        List<ResearchDefinition> unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        // A drive pre-loaded with this recipe_id, so "show uses" on an imprinted drive finds it.
        ItemStack preloadedDrive = unlockingResearch.isEmpty()
                ? new ItemStack(ModItems.METADATA_RECLAIMED.get())
                : RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        builder.addSlot(RecipeIngredientRole.INPUT, DRIVE_X, DRIVE_Y)
                .setStandardSlotBackground()
                .addItemStack(preloadedDrive)
                .addRichTooltipCallback((slotView, tooltip) ->
                        JeiRenderHelper.addDriveTooltip(tooltip, requiredRecipeId, unlockingResearch));

        if (recipe.isShaped() && recipe.getShapedPattern() != null) {
            addShapedSlots(builder, recipe.getShapedPattern());
        } else {
            addShapelessSlots(builder, recipe.getIngredients());
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(null));
    }

    /** Lay out shaped ingredients in the pattern's own grid, skipping empty (air) cells. */
    private void addShapedSlots(IRecipeLayoutBuilder builder, ShapedRecipePattern pattern) {
        int width = pattern.width();
        int height = pattern.height();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                Ingredient ingredient = pattern.ingredients().get(row * width + col);
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                builder.addSlot(RecipeIngredientRole.INPUT,
                                GRID_X + col * SLOT_STRIDE, GRID_Y + row * SLOT_STRIDE)
                        .setStandardSlotBackground()
                        .addIngredients(ingredient);
            }
        }
    }

    /** Lay out shapeless ingredients left-to-right within the 3x3 grid, wrapping every 3. */
    private void addShapelessSlots(IRecipeLayoutBuilder builder, List<Ingredient> ingredients) {
        int placed = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty() || placed >= GRID_COLUMNS * GRID_COLUMNS) {
                continue;
            }
            int col = placed % GRID_COLUMNS;
            int row = placed / GRID_COLUMNS;
            builder.addSlot(RecipeIngredientRole.INPUT,
                            GRID_X + col * SLOT_STRIDE, GRID_Y + row * SLOT_STRIDE)
                    .setStandardSlotBackground()
                    .addIngredients(ingredient);
            placed++;
        }
    }
}
