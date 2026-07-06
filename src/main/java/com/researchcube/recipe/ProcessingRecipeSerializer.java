package com.researchcube.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serializer for ProcessingRecipe.
 * Handles JSON parsing via MapCodec and network sync via StreamCodec.
 */
public class ProcessingRecipeSerializer implements RecipeSerializer<ProcessingRecipe> {

    // ── Physical Machine Limits ──
    // These MUST stay in sync with ProcessingStationBlockEntity. They are duplicated
    // here (rather than referenced) to avoid a recipe -> block package dependency
    // (block already depends on recipe, so importing it back would be circular).
    // Source of truth: ProcessingStationBlockEntity.INPUT_SLOT_COUNT (16),
    // OUTPUT_SLOT_COUNT (8), TANK_CAPACITY (8000), and the station's 2 input tanks.
    private static final int MAX_INGREDIENTS = 16;   // INPUT_SLOT_COUNT
    private static final int MAX_ITEM_RESULTS = 8;   // OUTPUT_SLOT_COUNT
    private static final int MAX_FLUID_INPUTS = 2;   // two input tanks
    private static final int TANK_CAPACITY = 8000;   // TANK_CAPACITY (mB)

    // ── Fluid Stack Codec ──

    private static final Codec<ProcessingFluidStack> FLUID_STACK_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("fluid").forGetter(ProcessingFluidStack::fluidId),
                    Codec.INT.fieldOf("amount").forGetter(ProcessingFluidStack::amount)
            ).apply(instance, ProcessingFluidStack::new)
    );

    // ── Main MapCodec ──

    private static final MapCodec<ProcessingRecipe> RAW_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    // "recipe_id" is optional: empty means "bind to my own recipe ID after load"
                    // (resolved by ResearchManager); explicit values are only for aliasing.
                    Codec.STRING.optionalFieldOf("recipe_id", "").forGetter(ProcessingRecipe::getRequiredRecipeId),
                    Codec.STRING.optionalFieldOf("group", "").forGetter(ProcessingRecipe::getGroup),
                    Ingredient.CODEC_NONEMPTY.listOf().optionalFieldOf("inputs", List.of()).forGetter(r -> r.getIngredients().stream().toList()),
                    FLUID_STACK_CODEC.listOf().optionalFieldOf("fluid_inputs", List.of()).forGetter(ProcessingRecipe::getFluidInputs),
                    ItemStack.STRICT_CODEC.listOf().fieldOf("outputs").forGetter(ProcessingRecipe::getResults),
                    FLUID_STACK_CODEC.optionalFieldOf("fluid_output").forGetter(r -> Optional.ofNullable(r.getFluidOutput())),
                    Codec.INT.fieldOf("duration").forGetter(ProcessingRecipe::getDuration)
            ).apply(instance, (recipeId, group, inputs, fluidInputs, outputs, fluidOutput, duration) ->
                    new ProcessingRecipe(recipeId, group, inputs, fluidInputs, outputs, fluidOutput.orElse(null), duration))
    );

    // Load-time validation: reject recipes the Processing Station physically cannot run,
    // so datapack authors get a clear error at /reload instead of a machine that silently
    // never starts. Kept as a separate step from RAW_CODEC — chaining directly onto
    // RecordCodecBuilder.mapCodec(...) breaks javac's generic inference for the builder.
    public static final MapCodec<ProcessingRecipe> CODEC = RAW_CODEC.validate(ProcessingRecipeSerializer::validate);

    /**
     * Validates a decoded recipe against the Processing Station's physical limits.
     * Returns a {@link DataResult} error (naming the offending field and its limit) that
     * surfaces in the log at datapack load / {@code /reload}.
     */
    private static DataResult<ProcessingRecipe> validate(ProcessingRecipe recipe) {
        int ingredientCount = recipe.getIngredients().size();
        if (ingredientCount > MAX_INGREDIENTS) {
            return DataResult.error(() -> "Processing recipe has " + ingredientCount
                    + " item inputs but the Processing Station allows at most "
                    + MAX_INGREDIENTS + " (field: inputs).");
        }

        int resultCount = recipe.getResults().size();
        if (resultCount > MAX_ITEM_RESULTS) {
            return DataResult.error(() -> "Processing recipe has " + resultCount
                    + " item outputs but the Processing Station allows at most "
                    + MAX_ITEM_RESULTS + " (field: outputs).");
        }

        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (fluidInputs.size() > MAX_FLUID_INPUTS) {
            return DataResult.error(() -> "Processing recipe has " + fluidInputs.size()
                    + " fluid inputs but the Processing Station allows at most "
                    + MAX_FLUID_INPUTS + " (field: fluid_inputs).");
        }

        // Fluid amounts: each input and the output must be within a tank's capacity and positive.
        for (ProcessingFluidStack fluid : fluidInputs) {
            DataResult<ProcessingRecipe> err = validateFluidAmount(recipe, fluid.amount(), "fluid_inputs");
            if (err != null) {
                return err;
            }
        }
        ProcessingFluidStack fluidOutput = recipe.getFluidOutput();
        if (fluidOutput != null) {
            DataResult<ProcessingRecipe> err = validateFluidAmount(recipe, fluidOutput.amount(), "fluid_output");
            if (err != null) {
                return err;
            }
        }

        int duration = recipe.getDuration();
        if (duration <= 0) {
            return DataResult.error(() -> "Processing recipe has duration " + duration
                    + " but it must be greater than 0 (field: duration).");
        }

        return DataResult.success(recipe);
    }

    /** Shared bounds check for a single fluid amount; returns null when valid. */
    private static DataResult<ProcessingRecipe> validateFluidAmount(ProcessingRecipe recipe, int amount, String field) {
        if (amount <= 0) {
            return DataResult.error(() -> "Processing recipe has a fluid amount of " + amount
                    + " but it must be greater than 0 (field: " + field + ").");
        }
        if (amount > TANK_CAPACITY) {
            return DataResult.error(() -> "Processing recipe has a fluid amount of " + amount
                    + " mB but a tank holds at most " + TANK_CAPACITY + " mB (field: " + field + ").");
        }
        return null;
    }

    // ── StreamCodec for Network ──

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> STREAM_CODEC =
            StreamCodec.of(
                    ProcessingRecipeSerializer::toNetwork,
                    ProcessingRecipeSerializer::fromNetwork
            );

    private static ProcessingRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        // Always the RESOLVED id — bindId ran server-side before recipe sync.
        String recipeId = buf.readUtf();
        int duration = buf.readVarInt();

        // Read ingredients
        int ingredientCount = buf.readVarInt();
        List<Ingredient> ingredients = new ArrayList<>(ingredientCount);
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }

        // Read fluid inputs
        int fluidInputCount = buf.readVarInt();
        List<ProcessingFluidStack> fluidInputs = new ArrayList<>(fluidInputCount);
        for (int i = 0; i < fluidInputCount; i++) {
            ResourceLocation fluidId = buf.readResourceLocation();
            int amount = buf.readVarInt();
            fluidInputs.add(new ProcessingFluidStack(fluidId, amount));
        }

        // Read item outputs
        int outputCount = buf.readVarInt();
        List<ItemStack> outputs = new ArrayList<>(outputCount);
        for (int i = 0; i < outputCount; i++) {
            outputs.add(ItemStack.STREAM_CODEC.decode(buf));
        }

        // Read fluid output
        ProcessingFluidStack fluidOutput = null;
        if (buf.readBoolean()) {
            ResourceLocation fluidId = buf.readResourceLocation();
            int amount = buf.readVarInt();
            fluidOutput = new ProcessingFluidStack(fluidId, amount);
        }

        return new ProcessingRecipe(recipeId, group, ingredients, fluidInputs, outputs, fluidOutput, duration);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, ProcessingRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeUtf(recipe.getRequiredRecipeId());
        buf.writeVarInt(recipe.getDuration());

        // Write ingredients
        List<Ingredient> ingredients = recipe.getIngredients();
        buf.writeVarInt(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
        }

        // Write fluid inputs
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        buf.writeVarInt(fluidInputs.size());
        for (ProcessingFluidStack fluid : fluidInputs) {
            buf.writeResourceLocation(fluid.fluidId());
            buf.writeVarInt(fluid.amount());
        }

        // Write item outputs
        List<ItemStack> outputs = recipe.getResults();
        buf.writeVarInt(outputs.size());
        for (ItemStack stack : outputs) {
            ItemStack.STREAM_CODEC.encode(buf, stack);
        }

        // Write fluid output
        ProcessingFluidStack fluidOutput = recipe.getFluidOutput();
        boolean hasFluidOutput = fluidOutput != null && fluidOutput.amount() > 0;
        buf.writeBoolean(hasFluidOutput);
        if (hasFluidOutput) {
            buf.writeResourceLocation(fluidOutput.fluidId());
            buf.writeVarInt(fluidOutput.amount());
        }
    }

    @Override
    public MapCodec<ProcessingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
