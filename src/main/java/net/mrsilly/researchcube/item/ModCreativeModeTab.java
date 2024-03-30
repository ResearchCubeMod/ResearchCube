package net.mrsilly.researchcube.item;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeModeTab {

    public static final CreativeModeTab RESEACHCUBE_TAB = new CreativeModeTab("researchcubetab") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.METADATA_SELF_AWARE.get());
        }
    };
}
