package com.researchcube.recipe;

import com.mojang.serialization.Codec;
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

    // ── Fluid Stack Codec ──

    private static final Codec<ProcessingFluidStack> FLUID_STACK_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("fluid").forGetter(ProcessingFluidStack::fluidId),
                    Codec.INT.fieldOf("amount").forGetter(ProcessingFluidStack::amount)
            ).apply(instance, ProcessingFluidStack::new)
    );

    // ── Main MapCodec ──

    public static final MapCodec<ProcessingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(ProcessingRecipe::getGroup),
                    Ingredient.CODEC_NONEMPTY.listOf().optionalFieldOf("inputs", List.of()).forGetter(r -> r.getIngredients().stream().toList()),
                    FLUID_STACK_CODEC.listOf().optionalFieldOf("fluid_inputs", List.of()).forGetter(ProcessingRecipe::getFluidInputs),
                    ItemStack.STRICT_CODEC.listOf().fieldOf("outputs").forGetter(ProcessingRecipe::getResults),
                    FLUID_STACK_CODEC.optionalFieldOf("fluid_output").forGetter(r -> Optional.ofNullable(r.getFluidOutput())),
                    Codec.INT.fieldOf("duration").forGetter(ProcessingRecipe::getDuration)
            ).apply(instance, (group, inputs, fluidInputs, outputs, fluidOutput, duration) ->
                    new ProcessingRecipe(group, inputs, fluidInputs, outputs, fluidOutput.orElse(null), duration))
    );

    // ── StreamCodec for Network ──

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> STREAM_CODEC =
            StreamCodec.of(
                    ProcessingRecipeSerializer::toNetwork,
                    ProcessingRecipeSerializer::fromNetwork
            );

    private static ProcessingRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
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

        return new ProcessingRecipe(group, ingredients, fluidInputs, outputs, fluidOutput, duration);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, ProcessingRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
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
