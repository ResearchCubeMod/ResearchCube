package com.researchcube.client.renderer;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.CubeItem;
import com.researchcube.research.ResearchTier;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

/**
 * Tier-aware GeoModel for CubeItem. Returns the correct geo, texture,
 * and animation resource based on the cube's tier:
 * <ul>
 *   <li>UNSTABLE, BASIC             → 1×1 geo (cube_basic)</li>
 *   <li>ADVANCED, PRECISE           → 2×2 geo (cube_advanced)</li>
 *   <li>FLAWLESS, SELF_AWARE        → 3×3 geo (cube_flawless)</li>
 * </ul>
 * All tiers share a single white texture; the renderer applies a tier-color tint.
 */
public class CubeItemModel extends GeoModel<CubeItem> {

    private static final ResourceLocation GEO_1X1 = ResearchCubeMod.rl("geo/cube_basic.geo.json");
    private static final ResourceLocation GEO_2X2 = ResearchCubeMod.rl("geo/cube_advanced.geo.json");
    private static final ResourceLocation GEO_3X3 = ResearchCubeMod.rl("geo/cube_flawless.geo.json");

    private static final ResourceLocation ANIM_1X1 = ResearchCubeMod.rl("animations/cube_basic.animation.json");
    private static final ResourceLocation ANIM_2X2 = ResearchCubeMod.rl("animations/cube_advanced.animation.json");
    private static final ResourceLocation ANIM_3X3 = ResearchCubeMod.rl("animations/cube_flawless.animation.json");

    private static final ResourceLocation TEX_1X1 = ResearchCubeMod.rl("textures/cube/cube_basic.png");
    private static final ResourceLocation TEX_2X2 = ResearchCubeMod.rl("textures/cube/cube_advanced.png");
    private static final ResourceLocation TEX_3X3 = ResearchCubeMod.rl("textures/cube/cube_flawless.png");

    private static final Map<ResearchTier, ResourceLocation> GEO_MAP = Map.of(
            ResearchTier.UNSTABLE, GEO_1X1,
            ResearchTier.BASIC, GEO_1X1,
            ResearchTier.ADVANCED, GEO_2X2,
            ResearchTier.PRECISE, GEO_2X2,
            ResearchTier.FLAWLESS, GEO_3X3,
            ResearchTier.SELF_AWARE, GEO_3X3
    );

    private static final Map<ResearchTier, ResourceLocation> ANIM_MAP = Map.of(
            ResearchTier.UNSTABLE, ANIM_1X1,
            ResearchTier.BASIC, ANIM_1X1,
            ResearchTier.ADVANCED, ANIM_2X2,
            ResearchTier.PRECISE, ANIM_2X2,
            ResearchTier.FLAWLESS, ANIM_3X3,
            ResearchTier.SELF_AWARE, ANIM_3X3
    );

    private static final Map<ResearchTier, ResourceLocation> TEX_MAP = Map.of(
            ResearchTier.UNSTABLE, TEX_1X1,
            ResearchTier.BASIC, TEX_1X1,
            ResearchTier.ADVANCED, TEX_2X2,
            ResearchTier.PRECISE, TEX_2X2,
            ResearchTier.FLAWLESS, TEX_3X3,
            ResearchTier.SELF_AWARE, TEX_3X3
    );

    @Override
    public ResourceLocation getModelResource(CubeItem cubeItem) {
        return GEO_MAP.getOrDefault(cubeItem.getTier(), GEO_1X1);
    }

    @Override
    public ResourceLocation getTextureResource(CubeItem cubeItem) {
        return TEX_MAP.getOrDefault(cubeItem.getTier(), TEX_1X1);
    }

    @Override
    public ResourceLocation getAnimationResource(CubeItem cubeItem) {
        return ANIM_MAP.getOrDefault(cubeItem.getTier(), ANIM_1X1);
    }
}
