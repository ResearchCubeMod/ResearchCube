package net.mrsilly.researchcube.item;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.mrsilly.researchcube.ResearchCube;
import net.mrsilly.researchcube.block.ModBlocks;
import net.mrsilly.researchcube.item.custom.ResearchStationBlockItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ResearchCube.MOD_ID);

    public static final RegistryObject<Item> METADATA_IRRECOVERABLE = ITEMS.register("metadata_irrecoverable", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_UNSTABLE = ITEMS.register("metadata_unstable", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_RECLAIMED = ITEMS.register("metadata_reclaimed", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_ENHANCED = ITEMS.register("metadata_enhanced", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_ELABORATE = ITEMS.register("metadata_elaborate", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_CYBERNETIC = ITEMS.register("metadata_cybernetic", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));
    public static final RegistryObject<Item> METADATA_SELF_AWARE = ITEMS.register("metadata_self_aware", () -> new Item(new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));

    public static final RegistryObject<Item> RESEARCH_STATION_ITEM = ITEMS.register("research_station", () -> new ResearchStationBlockItem(ModBlocks.RESEARCH_STATION.get(),new Item.Properties().tab(ModCreativeModeTab.RESEACHCUBE_TAB)));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);

    }
}
