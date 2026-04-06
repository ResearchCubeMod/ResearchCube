package com.researchcube.util;

import com.researchcube.research.ResearchTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods for reading/writing custom NBT data on ItemStacks.
 * In 1.21.1, item data uses DataComponents with CustomData.
 */
public final class NbtUtil {

    private static final String KEY_TIER = "Tier";
    private static final String KEY_RECIPES = "Recipes";

    private NbtUtil() {}

    // ── Tier ──

    public static void writeTier(ItemStack stack, ResearchTier tier) {
        mutateCustomData(stack, tag -> tag.putString(KEY_TIER, tier.getSerializedName()));
    }

    public static ResearchTier readTier(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        if (tag != null && tag.contains(KEY_TIER)) {
            return ResearchTier.fromString(tag.getString(KEY_TIER));
        }
        return null;
    }

    // ── Recipes ──

    public static void writeRecipes(ItemStack stack, List<String> recipes) {
        mutateCustomData(stack, tag -> {
            ListTag listTag = new ListTag();
            for (String recipe : recipes) {
                listTag.add(StringTag.valueOf(recipe));
            }
            tag.put(KEY_RECIPES, listTag);
        });
    }

    public static List<String> readRecipes(ItemStack stack) {
        List<String> result = new ArrayList<>();
        CompoundTag tag = getCustomData(stack);
        if (tag != null && tag.contains(KEY_RECIPES, Tag.TAG_LIST)) {
            ListTag listTag = tag.getList(KEY_RECIPES, Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                result.add(listTag.getString(i));
            }
        }
        return result;
    }

    public static void addRecipe(ItemStack stack, String recipeId) {
        mutateCustomData(stack, tag -> {
            ListTag listTag = tag.contains(KEY_RECIPES, Tag.TAG_LIST)
                    ? tag.getList(KEY_RECIPES, Tag.TAG_STRING)
                    : new ListTag();
            for (int i = 0; i < listTag.size(); i++) {
                if (listTag.getString(i).equals(recipeId)) return;
            }
            listTag.add(StringTag.valueOf(recipeId));
            tag.put(KEY_RECIPES, listTag);
        });
    }

    public static boolean hasRecipe(ItemStack stack, String recipeId) {
        CompoundTag tag = getCustomData(stack);
        if (tag == null || !tag.contains(KEY_RECIPES, Tag.TAG_LIST)) return false;
        ListTag listTag = tag.getList(KEY_RECIPES, Tag.TAG_STRING);
        for (int i = 0; i < listTag.size(); i++) {
            if (listTag.getString(i).equals(recipeId)) return true;
        }
        return false;
    }

    /**
     * Removes a single recipe ID from the stack's custom data.
     * Returns true if the recipe was found and removed.
     */
    public static boolean removeRecipe(ItemStack stack, String recipeId) {
        List<String> existing = readRecipes(stack);
        if (existing.remove(recipeId)) {
            writeRecipes(stack, existing);
            return true;
        }
        return false;
    }

    // ── Internal helpers ──

    private static CompoundTag getCustomData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static void mutateCustomData(ItemStack stack, java.util.function.Consumer<CompoundTag> mutator) {
        CompoundTag tag = getCustomData(stack);
        if (tag == null) {
            tag = new CompoundTag();
        }
        mutator.accept(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
