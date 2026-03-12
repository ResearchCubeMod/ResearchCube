package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.DriveItem;
import com.researchcube.item.CubeItem;
import com.researchcube.item.ResearchChipItem;
import com.researchcube.item.ResearchFluidBucketItem;
import com.researchcube.research.ResearchTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ResearchCubeMod.MOD_ID);

    // ── Drives (reusing metadata_* assets) ──
    // IRRECOVERABLE tier is decorative (broken drive)
    public static final DeferredItem<DriveItem> METADATA_IRRECOVERABLE = ITEMS.register("metadata_irrecoverable",
            () -> new DriveItem(ResearchTier.IRRECOVERABLE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_UNSTABLE = ITEMS.register("metadata_unstable",
            () -> new DriveItem(ResearchTier.UNSTABLE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_RECLAIMED = ITEMS.register("metadata_reclaimed",
            () -> new DriveItem(ResearchTier.BASIC, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_ENHANCED = ITEMS.register("metadata_enhanced",
            () -> new DriveItem(ResearchTier.ADVANCED, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_ELABORATE = ITEMS.register("metadata_elaborate",
            () -> new DriveItem(ResearchTier.PRECISE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_CYBERNETIC = ITEMS.register("metadata_cybernetic",
            () -> new DriveItem(ResearchTier.FLAWLESS, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DriveItem> METADATA_SELF_AWARE = ITEMS.register("metadata_self_aware",
            () -> new DriveItem(ResearchTier.SELF_AWARE, new Item.Properties().stacksTo(1)));

    // ── Cubes (6 functional tiers, no IRRECOVERABLE) ──
    public static final DeferredItem<CubeItem> CUBE_UNSTABLE = ITEMS.register("cube_unstable",
            () -> new CubeItem(ResearchTier.UNSTABLE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CubeItem> CUBE_BASIC = ITEMS.register("cube_basic",
            () -> new CubeItem(ResearchTier.BASIC, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CubeItem> CUBE_ADVANCED = ITEMS.register("cube_advanced",
            () -> new CubeItem(ResearchTier.ADVANCED, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CubeItem> CUBE_PRECISE = ITEMS.register("cube_precise",
            () -> new CubeItem(ResearchTier.PRECISE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CubeItem> CUBE_FLAWLESS = ITEMS.register("cube_flawless",
            () -> new CubeItem(ResearchTier.FLAWLESS, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CubeItem> CUBE_SELF_AWARE = ITEMS.register("cube_self_aware",
            () -> new CubeItem(ResearchTier.SELF_AWARE, new Item.Properties().stacksTo(1)));

    // ── Research Station block item ──
    public static final DeferredItem<BlockItem> RESEARCH_STATION_ITEM = ITEMS.register("research_station_item",
            () -> new BlockItem(ModBlocks.RESEARCH_STATION.get(), new Item.Properties()));

    // ── Research Book ──
    public static final DeferredItem<com.researchcube.item.ResearchBookItem> RESEARCH_BOOK = ITEMS.register("research_book",
            () -> new com.researchcube.item.ResearchBookItem(new Item.Properties().stacksTo(1)));

    // ── Drive Crafting Table block item ──
    public static final DeferredItem<BlockItem> DRIVE_CRAFTING_TABLE_ITEM = ITEMS.register("drive_crafting_table",
            () -> new BlockItem(ModBlocks.DRIVE_CRAFTING_TABLE.get(), new Item.Properties()));

    // ── Processing Station block item ──
    public static final DeferredItem<BlockItem> PROCESSING_STATION_ITEM = ITEMS.register("processing_station",
            () -> new BlockItem(ModBlocks.PROCESSING_STATION.get(), new Item.Properties()));

    // ── Fluid Buckets ──
    public static final DeferredItem<ResearchFluidBucketItem> THINKING_FLUID_BUCKET = ITEMS.register("thinking_fluid_bucket",
            () -> new ResearchFluidBucketItem(ModFluids.THINKING_FLUID.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    public static final DeferredItem<ResearchFluidBucketItem> PONDERING_FLUID_BUCKET = ITEMS.register("pondering_fluid_bucket",
            () -> new ResearchFluidBucketItem(ModFluids.PONDERING_FLUID.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    public static final DeferredItem<ResearchFluidBucketItem> REASONING_FLUID_BUCKET = ITEMS.register("reasoning_fluid_bucket",
            () -> new ResearchFluidBucketItem(ModFluids.REASONING_FLUID.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    public static final DeferredItem<ResearchFluidBucketItem> IMAGINATION_FLUID_BUCKET = ITEMS.register("imagination_fluid_bucket",
            () -> new ResearchFluidBucketItem(ModFluids.IMAGINATION_FLUID.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    // ── Research Chip (export/import research) ──
    public static final DeferredItem<ResearchChipItem> RESEARCH_CHIP = ITEMS.register("research_chip",
            () -> new ResearchChipItem(new Item.Properties().stacksTo(1)));
}
