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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Processing Station block.
 * A general-purpose machine for multi-item + multi-fluid processing recipes.
 */
public class ProcessingStationBlock extends BaseEntityBlock {

    public static final MapCodec<ProcessingStationBlock> CODEC = simpleCodec(ProcessingStationBlock::new);

    public ProcessingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessingStationBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProcessingStationBlockEntity psbe) {
                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("block.researchcube.processing_station");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                        return new com.researchcube.menu.ProcessingStationMenu(containerId, playerInv, psbe);
                    }
                }, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PROCESSING_STATION.get(), ProcessingStationBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ProcessingStationBlockEntity be) {
                be.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProcessingStationBlockEntity psbe) {
            if (!psbe.isProcessing()) {
                return 0;
            }
            float progress = psbe.getProgress();
            if (progress >= 1.0f) {
                return 15;
            }
            return 1 + (int) (progress * 14);
        }
        return 0;
    }
}
