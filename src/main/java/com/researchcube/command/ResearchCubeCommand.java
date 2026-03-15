package com.researchcube.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.researchcube.item.DriveItem;
import com.researchcube.registry.ModItems;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchSavedData;
import com.researchcube.research.ResearchTier;
import com.researchcube.research.WeightedRecipe;
import com.researchcube.util.NbtUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Set;

/**
 * Admin command for managing research progress and drives.
 * All subcommands require OP level 2.
 */
public class ResearchCubeCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RESEARCH = (context, builder) -> {
        Collection<ResearchDefinition> all = ResearchRegistry.getAll();
        return SharedSuggestionProvider.suggestResource(
                all.stream().map(ResearchDefinition::getId),
                builder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("researchcube")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("research", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_RESEARCH)
                                        .executes(ctx -> unlock(ctx, false))
                                        .then(Commands.argument("force", BoolArgumentType.bool())
                                                .executes(ctx -> unlock(ctx, BoolArgumentType.getBool(ctx, "force")))))))
                .then(Commands.literal("unlockAll")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ResearchCubeCommand::unlockAll)))
                .then(Commands.literal("lock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("research", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_RESEARCH)
                                        .executes(ResearchCubeCommand::lock))))
                .then(Commands.literal("getDrive")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("research", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_RESEARCH)
                                        .executes(ResearchCubeCommand::getDrive))))
                .then(Commands.literal("addToDrive")
                        .then(Commands.argument("research", ResourceLocationArgument.id())
                                .suggests(SUGGEST_RESEARCH)
                                .executes(ctx -> addToDrive(ctx, false))
                                .then(Commands.argument("force", BoolArgumentType.bool())
                                        .executes(ctx -> addToDrive(ctx, BoolArgumentType.getBool(ctx, "force"))))))
                .then(Commands.literal("giveChip")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("research", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_RESEARCH)
                                        .executes(ResearchCubeCommand::giveChip))))
                .then(Commands.literal("help")
                        .executes(ResearchCubeCommand::help))
        );
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation researchId = ResourceLocationArgument.getId(ctx, "research");
        CommandSourceStack source = ctx.getSource();

        ResearchDefinition def = ResearchRegistry.get(researchId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown research: " + researchId));
            return 0;
        }

        ResearchSavedData data = ResearchSavedData.get(source.getServer());
        String key = ResearchSavedData.getResearchKey(player);

        if (data.hasCompleted(key, researchId)) {
            source.sendFailure(Component.literal("Player " + player.getName().getString()
                    + " has already completed " + researchId));
            return 0;
        }

        if (!force) {
            Set<String> completed = data.getCompletedResearchStrings(key);
            if (!def.getPrerequisites().isSatisfied(completed)) {
                source.sendFailure(Component.literal("Prerequisites not met for " + researchId
                        + ". Use force=true to override."));
                return 0;
            }
        }

        data.addCompleted(key, researchId);
        source.sendSuccess(() -> Component.literal("Unlocked " + researchId + " for "
                + player.getName().getString()), true);
        return 1;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack source = ctx.getSource();

        ResearchSavedData data = ResearchSavedData.get(source.getServer());
        String key = ResearchSavedData.getResearchKey(player);

        int count = 0;
        for (ResearchDefinition def : ResearchRegistry.getAll()) {
            if (!data.hasCompleted(key, def.getId())) {
                data.addCompleted(key, def.getId());
                count++;
            }
        }

        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Unlocked " + finalCount + " research(es) for "
                + player.getName().getString()), true);
        return count;
    }

    private static int lock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation researchId = ResourceLocationArgument.getId(ctx, "research");
        CommandSourceStack source = ctx.getSource();

        ResearchDefinition def = ResearchRegistry.get(researchId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown research: " + researchId));
            return 0;
        }

        ResearchSavedData data = ResearchSavedData.get(source.getServer());
        String key = ResearchSavedData.getResearchKey(player);

        if (!data.hasCompleted(key, researchId)) {
            source.sendFailure(Component.literal("Player " + player.getName().getString()
                    + " has not completed " + researchId));
            return 0;
        }

        data.removeCompleted(key, researchId);
        source.sendSuccess(() -> Component.literal("Locked " + researchId + " for "
                + player.getName().getString()), true);
        return 1;
    }

    private static int getDrive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation researchId = ResourceLocationArgument.getId(ctx, "research");
        CommandSourceStack source = ctx.getSource();

        ResearchDefinition def = ResearchRegistry.get(researchId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown research: " + researchId));
            return 0;
        }

        ItemStack driveStack = getDriveItemForTier(def.getTier());
        if (driveStack.isEmpty()) {
            source.sendFailure(Component.literal("No drive available for tier " + def.getTier().getDisplayName()));
            return 0;
        }

        for (WeightedRecipe wr : def.getWeightedRecipePool()) {
            NbtUtil.addRecipe(driveStack, wr.id().toString());
        }

        // Mark research as completed
        ResearchSavedData data = ResearchSavedData.get(source.getServer());
        String key = ResearchSavedData.getResearchKey(player);
        data.addCompleted(key, researchId);

        // Give the drive to the player
        if (!player.getInventory().add(driveStack)) {
            player.drop(driveStack, false);
        }

        source.sendSuccess(() -> Component.literal("Gave " + def.getTier().getDisplayName()
                + " drive with " + def.getWeightedRecipePool().size() + " recipe(s) to "
                + player.getName().getString()), true);
        return 1;
    }

    private static int addToDrive(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation researchId = ResourceLocationArgument.getId(ctx, "research");

        ResearchDefinition def = ResearchRegistry.get(researchId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown research: " + researchId));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof DriveItem driveItem)) {
            source.sendFailure(Component.literal("You must hold a drive in your main hand."));
            return 0;
        }

        if (!force && driveItem.getTier() != def.getTier()) {
            source.sendFailure(Component.literal("Drive tier (" + driveItem.getTier().getDisplayName()
                    + ") does not match research tier (" + def.getTier().getDisplayName()
                    + "). Use force=true to override."));
            return 0;
        }

        int added = 0;
        for (WeightedRecipe wr : def.getWeightedRecipePool()) {
            String recipeId = wr.id().toString();
            if (!NbtUtil.hasRecipe(heldItem, recipeId)) {
                NbtUtil.addRecipe(heldItem, recipeId);
                added++;
            }
        }

        int finalAdded = added;
        source.sendSuccess(() -> Component.literal("Added " + finalAdded + " recipe(s) from "
                + researchId + " to held drive."), true);
        return 1;
    }

    private static int giveChip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation researchId = ResourceLocationArgument.getId(ctx, "research");
        CommandSourceStack source = ctx.getSource();

        ResearchDefinition def = ResearchRegistry.get(researchId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown research: " + researchId));
            return 0;
        }

        if (def.getIdeaChip().isEmpty()) {
            source.sendFailure(Component.literal("Research " + researchId + " does not require an idea chip."));
            return 0;
        }

        ItemStack chip = def.getIdeaChip().get().copy();
        if (!player.getInventory().add(chip)) {
            player.drop(chip, false);
        }

        source.sendSuccess(() -> Component.literal("Gave idea chip for " + researchId + " to "
                + player.getName().getString()), true);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("=== ResearchCube Commands ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/researchcube unlock <player> <research> [force]")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Unlock a research for a player").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube unlockAll <player>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Unlock all research for a player").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube lock <player> <research>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Lock a research for a player").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube getDrive <player> <research>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Give an imprinted drive for a research").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube addToDrive <research> [force]")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Add research recipes to held drive").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube giveChip <player> <research>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Give the idea chip for a research").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/researchcube help")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show this help message").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static ItemStack getDriveItemForTier(ResearchTier tier) {
        return switch (tier) {
            case IRRECOVERABLE -> new ItemStack(ModItems.METADATA_IRRECOVERABLE.get());
            case UNSTABLE -> new ItemStack(ModItems.METADATA_UNSTABLE.get());
            case BASIC -> new ItemStack(ModItems.METADATA_RECLAIMED.get());
            case ADVANCED -> new ItemStack(ModItems.METADATA_ENHANCED.get());
            case PRECISE -> new ItemStack(ModItems.METADATA_ELABORATE.get());
            case FLAWLESS -> new ItemStack(ModItems.METADATA_CYBERNETIC.get());
            case SELF_AWARE -> new ItemStack(ModItems.METADATA_SELF_AWARE.get());
        };
    }
}
