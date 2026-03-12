package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.DriveCraftingTableBlockEntity;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTableBlockEntity>> RESEARCH_STATION =
            BLOCK_ENTITIES.register("research_station",
                    () -> BlockEntityType.Builder.of(
                            ResearchTableBlockEntity::new,
                            ModBlocks.RESEARCH_STATION.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveCraftingTableBlockEntity>> DRIVE_CRAFTING_TABLE =
            BLOCK_ENTITIES.register("drive_crafting_table",
                    () -> BlockEntityType.Builder.of(
                            DriveCraftingTableBlockEntity::new,
                            ModBlocks.DRIVE_CRAFTING_TABLE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingStationBlockEntity>> PROCESSING_STATION =
            BLOCK_ENTITIES.register("processing_station",
                    () -> BlockEntityType.Builder.of(
                            ProcessingStationBlockEntity::new,
                            ModBlocks.PROCESSING_STATION.get()
                    ).build(null));
}
