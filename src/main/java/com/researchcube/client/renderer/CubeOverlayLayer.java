package com.researchcube.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.item.CubeItem;
import com.researchcube.research.ResearchTier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Optional;

/**
 * Render layer that draws the Rubik's Cube model above the Research Station
 * at the Brain bone's static pivot, using the CubeItem's own GeckoLib
 * animation (face rotations, bob, spin) instead of the station's brain anim.
 * The cube is tinted with the tier's RGB colour and rendered at full brightness.
 */
public class CubeOverlayLayer extends GeoRenderLayer<ResearchTableBlockEntity> {

    /**
     * Dedicated renderer for the cube model. Using CubeItemRenderer ensures
     * the CubeItem's animation controllers are properly processed.
     */
    private final CubeItemRenderer cubeRenderer = new CubeItemRenderer();

    public CubeOverlayLayer(GeoRenderer<ResearchTableBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, ResearchTableBlockEntity blockEntity,
                       BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        // Get the actual CubeItem from the station's inventory
        ItemStack cubeStack = blockEntity.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_CUBE);
        if (cubeStack.isEmpty() || !(cubeStack.getItem() instanceof CubeItem cubeItem)) return;

        ResearchTier tier = cubeItem.getTier();
        if (!tier.isFunctional()) return;

        Optional<GeoBone> brainOpt = bakedModel.getBone("Brain");
        if (brainOpt.isEmpty()) return;
        GeoBone brain = brainOpt.get();

        // Get the cube model's baked geometry
        CubeItemModel cubeModel = (CubeItemModel) cubeRenderer.getGeoModel();
        BakedGeoModel cubeBaked = cubeModel.getBakedModel(cubeModel.getModelResource(cubeItem));

        ResourceLocation cubeTex = cubeModel.getTextureResource(cubeItem);
        RenderType cubeType = RenderType.entityTranslucentCull(cubeTex);
        VertexConsumer cubeBuffer = bufferSource.getBuffer(cubeType);

        // Tier colour with pulsing brightness + 80% alpha
        int tierRgb = tier.getColor();
        double time = System.currentTimeMillis() / 800.0;
        float pulse = (float) (Math.sin(time) * 0.06 + 0.94);
        int r = Math.min(255, (int) (((tierRgb >> 16) & 0xFF) * pulse));
        int g = Math.min(255, (int) (((tierRgb >> 8) & 0xFF) * pulse));
        int b = Math.min(255, (int) ((tierRgb & 0xFF) * pulse));
        int colour = 0xCC000000 | (r << 16) | (g << 8) | b;

        poseStack.pushPose();

        // Position at Brain bone's static pivot point.
        // The cube's own animation (master bone) handles bob + rotation.
        poseStack.translate(brain.getPivotX() / 16f, brain.getPivotY() / 16f, brain.getPivotZ() / 16f);

        // Set up the item renderer's internal state (currentItemStack, animatable)
        // so GeoItemRenderer.getInstanceId() doesn't NPE
        cubeRenderer.prepareForStationRender(cubeItem, cubeStack);

        // Use CubeItemRenderer's actuallyRender with isReRender=false
        // so that the CubeItem's animation controllers are processed
        cubeRenderer.actuallyRender(poseStack, cubeItem, cubeBaked, cubeType,
                bufferSource, cubeBuffer, false, partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, colour);

        poseStack.popPose();
    }
}
