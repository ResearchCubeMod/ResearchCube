package com.researchcube.datapack;

import com.researchcube.ResearchCubeMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers the built-in example datapack that ships inside the mod jar
 * (resources/datapacks/example).
 *
 * The pack contains the complete default content — the example research tree,
 * its recipes, advancements and the fluid bucket crafting recipes. Because it is
 * registered with {@link PackSource#BUILT_IN} and NOT marked always-active:
 *
 *   - it is enabled automatically in new and existing worlds
 *   - pack devs / server owners can turn it off with the vanilla command:
 *       /datapack disable "mod/researchcube:datapacks/example"
 *     (the choice is stored per world, so it stays off)
 *
 * The mod jar's normal data/ folder intentionally contains only core data
 * (loot tables, tags, guidebook) that must never be disabled.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID)
public final class BuiltinPacks {

    /** Folder inside the mod jar resources/ that holds the example pack. */
    public static final String EXAMPLE_PACK_PATH = "datapacks/example";

    /** The pack ID as it appears in /datapack list. */
    public static final String EXAMPLE_PACK_ID = "mod/" + ResearchCubeMod.MOD_ID + ":" + EXAMPLE_PACK_PATH;

    private BuiltinPacks() {}

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addPackFinders(
                ResearchCubeMod.rl(EXAMPLE_PACK_PATH),
                PackType.SERVER_DATA,
                Component.literal("ResearchCube Example Content"),
                // BUILT_IN → enabled by default, still disableable via /datapack
                PackSource.BUILT_IN,
                // alwaysActive = false → the whole point: /datapack disable works
                false,
                // BOTTOM → world datapacks and other packs override the example content
                Pack.Position.BOTTOM
        );
        ResearchCubeMod.LOGGER.info("[ResearchCube] Registered built-in example datapack ({}).", EXAMPLE_PACK_ID);
    }
}
