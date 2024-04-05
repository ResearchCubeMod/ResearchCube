package net.mrsilly.researchcube.block.entity;


import net.mrsilly.researchcube.ResearchCube;
import net.mrsilly.researchcube.block.ModBlocks;
import net.mrsilly.researchcube.block.entity.custom.ResearchStationBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ResearchCube.MOD_ID);

    public static final RegistryObject<BlockEntityType<ResearchStationBlockEntity>> RESEARCH_STATION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("research_station_block_entity", () ->
                    BlockEntityType.Builder.of(ResearchStationBlockEntity::new,
                            ModBlocks.RESEARCH_STATION.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
