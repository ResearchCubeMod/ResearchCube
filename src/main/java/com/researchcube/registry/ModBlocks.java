package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.DriveCraftingTableBlock;
import com.researchcube.block.ResearchTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ResearchCubeMod.MOD_ID);

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_STATION = BLOCKS.register("research_station",
            () -> new ResearchTableBlock(BlockBehaviour.Properties.of()
                    .strength(4.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<DriveCraftingTableBlock> DRIVE_CRAFTING_TABLE = BLOCKS.register("drive_crafting_table",
            () -> new DriveCraftingTableBlock(BlockBehaviour.Properties.of()
                    .strength(3.5f, 5.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
}
