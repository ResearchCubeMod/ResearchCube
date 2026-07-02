package com.researchcube.client;

import com.researchcube.client.screen.ChipEncoderScreen;
import com.researchcube.client.screen.DriveInspectorScreen;
import com.researchcube.client.screen.ResearchBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Client-only entry points for code that lives in common classes (items, packets).
 *
 * IMPORTANT: common classes must never reference client classes (screens, Minecraft)
 * directly — the JVM verifier loads such classes when the referencing class links,
 * which crashes dedicated servers ("Attempted to load class ... for invalid dist").
 * Instead, common code calls these static methods behind an isClientSide()/handler
 * guard; this class is only ever class-loaded on the physical client.
 */
public final class ClientHooks {

    private ClientHooks() {}

    /** Opens the drive inspector for the given drive stack. */
    public static void openDriveInspector(ItemStack stack) {
        Minecraft.getInstance().setScreen(new DriveInspectorScreen(stack));
    }

    /** Opens the research book screen with the given completed research IDs. */
    public static void openResearchBook(Set<String> completedResearch) {
        Minecraft.getInstance().setScreen(new ResearchBookScreen(completedResearch));
    }

    /** Opens the chip encoder selection screen. */
    public static void openChipEncoder(Set<ResourceLocation> completedResearch, boolean mainHand) {
        Minecraft.getInstance().setScreen(new ChipEncoderScreen(completedResearch, mainHand));
    }
}
