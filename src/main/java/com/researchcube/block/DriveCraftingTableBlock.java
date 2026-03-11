package com.researchcube.block;

import com.researchcube.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A dedicated crafting station for Drive Crafting recipes.
 * The player inserts a Drive (with researched recipe IDs) into a separate slot
 * and places ingredients in a 3x3 grid. Matching drive_crafting recipes produce output.
 */
public class DriveCraftingTableBlock extends BaseEntityBlock {

    public static final MapCodec<DriveCraftingTableBlock> CODEC = simpleCodec(DriveCraftingTableBlock::new);

    public DriveCraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DriveCraftingTableBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DriveCraftingTableBlockEntity dcbe) {
                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("block.researchcube.drive_crafting_table");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                        return new com.researchcube.menu.DriveCraftingTableMenu(containerId, playerInv, dcbe);
                    }
                }, buf -> {
                    buf.writeBlockPos(pos);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DriveCraftingTableBlockEntity be) {
                be.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
