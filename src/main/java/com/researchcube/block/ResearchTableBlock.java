package com.researchcube.block;

import com.researchcube.registry.ModBlockEntities;
import com.researchcube.research.ResearchSavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The Research Table block. Houses a BlockEntity that manages
 * active research, ticking progress, and item storage.
 */
public class ResearchTableBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<ResearchTableBlock> CODEC = simpleCodec(ResearchTableBlock::new);

    public ResearchTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // ENTITYBLOCK_ANIMATED for GeckoLib rendering
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResearchTableBlockEntity rtbe) {
                // Collect completed research for this player's team/solo key to send to client
                String researchKey = ResearchSavedData.getResearchKey(serverPlayer);
                Set<ResourceLocation> completed = (level instanceof ServerLevel sl)
                        ? ResearchSavedData.get(sl).getCompletedResearch(researchKey)
                        : Set.of();

                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("block.researchcube.research_station");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                        return new com.researchcube.menu.ResearchTableMenu(containerId, playerInv, rtbe);
                    }
                }, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeCollection(completed, (b, rl) -> b.writeResourceLocation(rl));
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null; // No client-side ticking needed
        }
        return createTickerHelper(type, ModBlockEntities.RESEARCH_STATION.get(), ResearchTableBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Block was broken — research is lost
            if (level.getBlockEntity(pos) instanceof ResearchTableBlockEntity be) {
                be.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * Comparator output based on research progress:
     * - 0 when idle (no research active)
     * - 1-14 scaling with progress (1% to 99%)
     * - 15 when research is complete (100%)
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ResearchTableBlockEntity rtbe) {
            if (!rtbe.isResearching()) {
                return 0;
            }
            float progress = rtbe.getProgress();
            if (progress >= 1.0f) {
                return 15; // Fully complete
            }
            // Scale 0.0-0.99 to 1-14
            return 1 + (int) (progress * 14);
        }
        return 0;
    }
}
