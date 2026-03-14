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
 * Renders the animated model with a cube visual floating above it.
 * <p>
 * The brain bone assembly (Brain, center, b1-b8) serves as the visual
 * placeholder for the inserted cube:
 *   - No cube inserted  → brain bones hidden entirely.
 *   - Cube inserted     → brain bones visible, tinted with the tier's RGB
 *     color, rendered at full brightness (emissive glow) with a pulsing effect.
 * <p>
 * A flat white texture is used so the tier color tint IS the final colour.
 * <p>
 * TODO: Once Rubik's cube geo assets (cube_2x2.geo.json, cube_3x3.geo.json)
 *       are created, replace the brain bone rendering with the actual cube
 *       GeoModel rendered at the Brain bone's animated position. This will
 *       require rendering a nested GeoModel or using a GeoRenderLayer.
 */
public class ResearchStationRenderer extends GeoBlockRenderer<ResearchTableBlockEntity> {

    /** Names of bones that belong to the brain/cube assembly. */
    private static final Set<String> BRAIN_BONES = Set.of(
            "Brain", "center", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8"
    );

    /** Flat white texture — tier colour tint is multiplied against this. */
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
                bone.setHidden(true);
            } else {
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
