package com.researchcube.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.research.ResearchTier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Set;

/**
 * GeckoLib block entity renderer for the Research Station.
 * Renders the animated model with the rotating brain.
 * <p>
 * Brain visibility is tied to the cube slot:
 *   - No cube inserted → brain bones are hidden entirely.
 *   - Cube inserted → brain bones are visible, tinted with the tier's color,
 *     rendered at full brightness (emissive glow), with a subtle pulsing effect.
 * <p>
 * The brain is rendered using a flat white texture so the tier color tint
 * IS the final displayed colour. The original research_station.png texture is
 * dark blue in the brain region, which would otherwise darken any tint applied.
 */
public class ResearchStationRenderer extends GeoBlockRenderer<ResearchTableBlockEntity> {

    /** Names of bones that belong to the brain assembly. */
    private static final Set<String> BRAIN_BONES = Set.of(
            "Brain", "center", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8"
    );

    /** Flat white texture — tier colour tint is multiplied against this,
     *  so the final colour matches the tier colour exactly. */
    private static final ResourceLocation WHITE_TEXTURE =
            ResearchCubeMod.rl("textures/misc/white.png");

    public ResearchStationRenderer() {
        super(new ResearchStationModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ResearchTableBlockEntity animatable,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        if (BRAIN_BONES.contains(bone.getName())) {
            @Nullable ResearchTier cubeTier = animatable.getCubeTier();

            if (cubeTier == null || !cubeTier.isFunctional()) {
                // No cube (or non-functional tier) — hide the brain entirely
                bone.setHidden(true);
            } else {
                // Cube present — show brain, tinted with tier colour on a white texture
                bone.setHidden(false);

                int tierRgb = cubeTier.getColor();

                // Pulsing brightness: sine wave oscillates between 0.88 and 1.0
                double time = System.currentTimeMillis() / 800.0;
                float pulse = (float) (Math.sin(time) * 0.06 + 0.94);

                int r = Math.min(255, (int) (((tierRgb >> 16) & 0xFF) * pulse));
                int g = Math.min(255, (int) (((tierRgb >> 8) & 0xFF) * pulse));
                int b = Math.min(255, (int) ((tierRgb & 0xFF) * pulse));

                colour = 0xFF000000 | (r << 16) | (g << 8) | b;
                packedLight = LightTexture.FULL_BRIGHT;

                // Solid cutout render type on a white texture: colour tint = final colour, no translucency
                RenderType brainType = RenderType.entityCutoutNoCull(WHITE_TEXTURE);
                VertexConsumer brainBuffer = bufferSource.getBuffer(brainType);

                super.renderRecursively(poseStack, animatable, bone, brainType,
                        bufferSource, brainBuffer, isReRender, partialTick,
                        packedLight, packedOverlay, colour);
                return;
            }
        }

        super.renderRecursively(poseStack, animatable, bone, renderType,
                bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
