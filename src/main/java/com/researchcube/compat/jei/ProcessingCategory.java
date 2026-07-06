package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * JEI recipe category for Processing Station recipes.
 * Shows item inputs (up to 16, 4×4 grid), fluid inputs (up to 2), the research-locked drive,
 * item outputs (up to 8, 2×4 grid), and the fluid output, along with the processing duration.
 *
 * <p>Slot backgrounds, the arrow and fluid rendering use JEI's own drawables/helpers; only the
 * headers, duration label and the research-completion indicator are drawn manually.
 */
public class ProcessingCategory implements IRecipeCategory<ProcessingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("processing");
    public static final RecipeType<ProcessingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, ProcessingRecipe.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 100;

    private static final int SLOT_STRIDE = 18;
    private static final int FLUID_SIZE = 16;

    // Input item grid (4×4): ingredient top-left corners.
    private static final int INPUT_GRID_X = 1;
    private static final int INPUT_GRID_Y = 1;
    private static final int INPUT_COLS = 4;
    private static final int INPUT_ROWS = 4;

    // Output item grid (2×4).
    private static final int OUTPUT_GRID_X = 114;
    private static final int OUTPUT_GRID_Y = 1;
    private static final int OUTPUT_COLS = 2;
    private static final int OUTPUT_ROWS = 4;

    // Fluid slots and the drive slot.
    private static final int FLUID_IN_Y = 76;
    private static final int FLUID_IN1_X = 1;
    private static final int FLUID_IN2_X = 19;
    private static final int FLUID_OUT_X = 156;
    private static final int DRIVE_X = 84;
    private static final int DRIVE_Y = 57;

    private static final int ARROW_X = 82;
    private static final int ARROW_Y = 36;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public ProcessingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()));
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.researchcube.category.processing");
    }

    @Override
    public RecipeType<ProcessingRecipe> getRecipeType() {
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
    public void draw(ProcessingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Section headers.
        g.drawString(font, Component.translatable("jei.researchcube.processing.header.inputs"),
                INPUT_GRID_X, INPUT_GRID_Y - 11, 0xFFAAAAAA, false);
        g.drawString(font, Component.translatable("jei.researchcube.processing.header.outputs"),
                OUTPUT_GRID_X, OUTPUT_GRID_Y - 11, 0xFFAAAAAA, false);

        // Arrow, vertically centred on the item rows.
        arrow.draw(g, ARROW_X, ARROW_Y + (SLOT_STRIDE - arrow.getHeight()) / 2);

        // Duration below the arrow.
        String durationStr = Component.translatable("jei.researchcube.processing.duration",
                String.format("%.1f", recipe.getDuration() / 20.0f)).getString();
        int durationW = font.width(durationStr);
        g.drawString(font, durationStr, ARROW_X + 10 - durationW / 2, ARROW_Y + 20, 0xFF888888, false);

        // Research-completion indicator under the drive slot.
        JeiRenderHelper.drawCompletionIndicator(g, font, recipe.getRequiredRecipeId(),
                DRIVE_X + 5, DRIVE_Y + 21);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingRecipe recipe, IFocusGroup focuses) {
        // Research-locked drive (mirrors DriveCraftingCategory): a drive pre-loaded with this
        // recipe_id so "show uses" on an imprinted drive finds this recipe.
        String requiredRecipeId = recipe.getRequiredRecipeId();
        List<ResearchDefinition> unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        ItemStack preloadedDrive = unlockingResearch.isEmpty()
                ? new ItemStack(ModItems.METADATA_RECLAIMED.get())
                : RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        builder.addSlot(RecipeIngredientRole.INPUT, DRIVE_X, DRIVE_Y)
                .setStandardSlotBackground()
                .addItemStack(preloadedDrive)
                .addRichTooltipCallback((slotView, tooltip) ->
                        JeiRenderHelper.addDriveTooltip(tooltip, requiredRecipeId, unlockingResearch));

        // Item inputs (4×4).
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < INPUT_COLS * INPUT_ROWS && i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            int x = INPUT_GRID_X + (i % INPUT_COLS) * SLOT_STRIDE;
            int y = INPUT_GRID_Y + (i / INPUT_COLS) * SLOT_STRIDE;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setStandardSlotBackground()
                    .addIngredients(ingredient);
        }

        // Fluid inputs (up to 2), rendered against the real tank capacity so the bar fills sensibly.
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (!fluidInputs.isEmpty()) {
            addFluidSlot(builder, RecipeIngredientRole.INPUT, FLUID_IN1_X, FLUID_IN_Y, fluidInputs.get(0).toFluidStack());
        }
        if (fluidInputs.size() > 1) {
            addFluidSlot(builder, RecipeIngredientRole.INPUT, FLUID_IN2_X, FLUID_IN_Y, fluidInputs.get(1).toFluidStack());
        }

        // Item outputs (2×4).
        List<ItemStack> results = recipe.getResults();
        for (int i = 0; i < OUTPUT_COLS * OUTPUT_ROWS && i < results.size(); i++) {
            ItemStack result = results.get(i);
            if (result.isEmpty()) {
                continue;
            }
            int x = OUTPUT_GRID_X + (i % OUTPUT_COLS) * SLOT_STRIDE;
            int y = OUTPUT_GRID_Y + (i / OUTPUT_COLS) * SLOT_STRIDE;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setStandardSlotBackground()
                    .addItemStack(result);
        }

        // Fluid output.
        if (recipe.hasFluidOutput()) {
            addFluidSlot(builder, RecipeIngredientRole.OUTPUT, FLUID_OUT_X, FLUID_IN_Y, recipe.getFluidOutput().toFluidStack());
        }
    }

    /**
     * Add a fluid slot rendered against the station's tank capacity (so the fill level is
     * meaningful) with the capacity shown in JEI's own fluid tooltip. Skips empty fluids.
     */
    private void addFluidSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, FluidStack fluid) {
        if (fluid.isEmpty()) {
            return;
        }
        builder.addSlot(role, x, y)
                .setStandardSlotBackground()
                .setFluidRenderer(ProcessingStationBlockEntity.TANK_CAPACITY, true, FLUID_SIZE, FLUID_SIZE)
                .addFluidStack(fluid.getFluid(), fluid.getAmount());
    }
}
