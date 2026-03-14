package com.researchcube.item;

import com.researchcube.research.ResearchTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Map;

/**
 * A Cube item used for tier validation in the Research Table.
 * Cubes define the maximum research tier allowed.
 * They have no NBT data—only a fixed tier.
 * <p>
 * Implements GeoItem for 3D Rubik's Cube rendering via GeckoLib.
 * Tiers UNSTABLE through SELF_AWARE get 3D models.
 */
public class CubeItem extends Item implements GeoItem {

    private static final Map<ResearchTier, String> ANIM_NAMES = Map.of(
            ResearchTier.UNSTABLE, "animation.cube_basic.idle",
            ResearchTier.BASIC, "animation.cube_basic.idle",
            ResearchTier.ADVANCED, "cube_advanced",
            ResearchTier.PRECISE, "cube_advanced",
            ResearchTier.FLAWLESS, "cube_flawless",
            ResearchTier.SELF_AWARE, "cube_flawless"
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ResearchTier tier;

    public CubeItem(ResearchTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ResearchTier getTier() {
        return tier;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String animName = ANIM_NAMES.get(tier);
        if (animName == null) return; // UNSTABLE / IRRECOVERABLE — no GeckoLib animation

        RawAnimation anim = RawAnimation.begin().thenLoop(animName);
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            state.setAnimation(anim);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("Tier: " + tier.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Max Research Level: " + tier.getLevel())
                .withStyle(ChatFormatting.YELLOW));
    }
}
